package net.createmod.ponder.foundation.content.mmce;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import net.createmod.ponder.Ponder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

/** Client-owned, bounded static data cache. No world, Tile, provider or renderer is retained. */
public final class MmceStaticModelPreviews implements IResourceManagerReloadListener {

    private static final MmceStaticModelPreviews INSTANCE = new MmceStaticModelPreviews();
    static final int MAX_ENTRIES = 12;
    static final long MAX_BYTES = 16L * 1024L * 1024L;

    public static final class Preview {
        private final MmceStaticModelSpec spec;
        private final MmceStaticModelMesh mesh;
        private final ResourceLocation texture;
        private final String note;
        private final long resourceGeneration;
        private final long indexGeneration;

        Preview(MmceStaticModelSpec spec, MmceStaticModelMesh mesh, String note,
            long resourceGeneration, long indexGeneration) {
            this.spec = spec;
            this.mesh = mesh;
            this.texture = mesh == null ? null : new ResourceLocation(spec.getTexture());
            this.note = note;
            this.resourceGeneration = resourceGeneration;
            this.indexGeneration = indexGeneration;
        }

        public boolean hasMesh() {
            return mesh != null;
        }

        public MmceStaticModelMesh getMesh() {
            return mesh;
        }

        public ResourceLocation getTexture() {
            return texture;
        }

        public MmceStaticModelSpec getSpec() {
            return spec;
        }

        public String getNote() {
            return note;
        }

        public long getResourceGeneration() {
            return resourceGeneration;
        }
    }

    private final MmceStaticModelBaker baker = new MmceStaticModelBaker();
    private final Map<String, Preview> cache = new LinkedHashMap<String, Preview>(16, 0.75F, true);
    private IResourceManager attachedResources;
    private long resourceGeneration;
    private long cachedIndexGeneration = -1;
    private long cachedBytes;

    MmceStaticModelPreviews() {
    }

    public static MmceStaticModelPreviews getInstance() {
        return INSTANCE;
    }

    /** Resolve and bake only when a machine screen is opened or its cached resources become stale. */
    public Preview getPreview(ResourceLocation machineId, IResourceManager resources) {
        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            throw new IllegalStateException("静态模型预览只能在客户端主线程读取");
        }
        if (!(resources instanceof IReloadableResourceManager)) {
            return new Preview(MmceStaticModelSpec.unavailable("资源管理器不支持重载监听，当前显示方块结构"),
                null, "资源管理器不支持重载监听，当前显示方块结构",
                getResourceGeneration(), MmceMachineIndex.getInstance().getGeneration());
        }
        attach(resources);
        MmceMachineIndex index = MmceMachineIndex.getInstance();
        long indexVersion = index.getGeneration();
        long resourceVersion;
        synchronized (this) {
            if (cachedIndexGeneration != indexVersion) {
                clear();
                cachedIndexGeneration = indexVersion;
            }
            Preview present = cache.get(machineId.toString());
            if (present != null) {
                return present;
            }
            resourceVersion = resourceGeneration;
        }
        MmceStaticModelSpec spec = index.getDefaultModelSpec(machineId);
        MmceStaticModelMesh mesh = null;
        String note = spec.getNote();
        if (spec.isBound()) {
            long started = System.nanoTime();
            try {
                byte[] geometry = read(resources, new ResourceLocation(spec.getGeometry()),
                    MmceGeoModelValidation.MAX_JSON_BYTES);
                MmceModelResources.validateTexture(read(resources, new ResourceLocation(spec.getTexture()),
                    MmceModelResources.MAX_TEXTURE_BYTES));
                String json = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(geometry)).toString();
                mesh = baker.bake(json, spec.getGeometry());
                Ponder.LOGGER.info("MMCE static preview {}: {} bones, {} cubes, {} vertices, {} ms", machineId,
                    Integer.valueOf(mesh.getBoneCount()), Integer.valueOf(mesh.getCubeCount()),
                    Integer.valueOf(mesh.getVertexCount()), Long.valueOf((System.nanoTime() - started) / 1000000L));
            } catch (IOException | ReflectiveOperationException | RuntimeException | LinkageError failure) {
                MmceMachineIndex.rethrowFatalTarget(failure);
                Throwable cause = failure;
                while (cause instanceof InvocationTargetException && cause.getCause() != null) {
                    cause = cause.getCause();
                }
                note = "默认模型无法安全预览，当前显示方块结构："
                    + (cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
                Ponder.LOGGER.warn("Cannot bake read-only MMCE static model for {}", machineId, failure);
            }
        }
        Preview result = new Preview(spec, mesh, note, resourceVersion, indexVersion);
        synchronized (this) {
            if (resourceVersion == resourceGeneration && indexVersion == index.getGeneration()) {
                remember(machineId.toString(), result);
            }
        }
        return result;
    }

    public synchronized boolean isCurrent(Preview preview) {
        return preview != null && preview.resourceGeneration == resourceGeneration
            && preview.indexGeneration == MmceMachineIndex.getInstance().getGeneration();
    }

    public synchronized long getResourceGeneration() {
        return resourceGeneration;
    }

    public synchronized int getCachedCount() {
        return cache.size();
    }

    public synchronized long getCachedBytes() {
        return cachedBytes;
    }

    /** Also call for explicit Ponder reload/world unload. This touches only owned data. */
    public synchronized void clear() {
        cache.clear();
        cachedBytes = 0;
        cachedIndexGeneration = -1;
        resourceGeneration++;
    }

    @Override
    public synchronized void onResourceManagerReload(IResourceManager resourceManager) {
        if (resourceManager == attachedResources) {
            clear();
        }
    }

    synchronized void remember(String key, Preview preview) {
        Preview previous = cache.put(key, preview);
        cachedBytes += weight(preview) - weight(previous);
        Iterator<Preview> iterator = cache.values().iterator();
        while (cache.size() > MAX_ENTRIES || cachedBytes > MAX_BYTES) {
            cachedBytes -= weight(iterator.next());
            iterator.remove();
        }
    }

    private void attach(IResourceManager resources) {
        synchronized (this) {
            if (resources == attachedResources) {
                return;
            }
            attachedResources = resources;
            clear();
        }
        ((IReloadableResourceManager) resources).registerReloadListener(this);
    }

    private static long weight(Preview preview) {
        return preview == null || preview.mesh == null ? 0 : preview.mesh.getByteSize();
    }

    private static byte[] read(IResourceManager resources, ResourceLocation location, int maximum)
        throws IOException {
        try (IResource resource = resources.getResource(location);
            InputStream stream = resource.getInputStream()) {
            return MmceModelResources.readLimited(stream, maximum);
        }
    }
}
