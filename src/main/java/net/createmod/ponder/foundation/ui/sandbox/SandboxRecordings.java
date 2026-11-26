package net.createmod.ponder.foundation.ui.sandbox;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.external.ExternalPonderScenes;
import net.createmod.ponder.foundation.ui.BlockGuiDemoDefinition;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.SnapshotProvider;
import net.createmod.ponder.foundation.ui.sandbox.worker.SandboxWorker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Discovers demos on the client thread. All file copies, hashes and worker waits run off-thread. */
public final class SandboxRecordings {
    private static final SandboxRecordings INSTANCE = new SandboxRecordings();
    static final String JOB = "ponder.sandbox.job";
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Ponder GUI recorder");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Path> published = new LinkedHashMap<>();
    // Render and client tick share the client thread. The worker publishes status off-thread.
    private final LinkedHashMap<String, JsonObject> viewed = new LinkedHashMap<>();
    private final Map<String, String> status = new ConcurrentHashMap<>();
    private final AtomicBoolean reloaded = new AtomicBoolean();
    private FileChannel cacheLease;
    private volatile long revision;
    private String language;
    private int scale = -1;
    private boolean unicode;

    public static void initialize() {
        String job = System.getProperty(JOB);
        if (job != null) {
            SandboxWorker.start(new File(job));
            return;
        }
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        if (Minecraft.getMinecraft().getResourceManager() instanceof IReloadableResourceManager resources)
            resources.registerReloadListener(ignored -> requestRefresh());
    }

    public static void requestRefresh() {
        INSTANCE.reloaded.set(true);
        INSTANCE.dirty.set(true);
    }

    public static String statusMessage(String id) { return INSTANCE.status.get(id); }

    public static SnapshotProvider onView(JsonObject definition, int startTick) {
        String id = id(definition);
        return RecordedGuiPlayback.provider(id, startTick, () -> INSTANCE.requestView(id, definition));
    }

    private void requestView(String id, JsonObject definition) {
        if (viewed.containsKey(id)) return;
        if (viewed.size() >= 64) viewed.pollLastEntry();
        viewed.putFirst(id, definition);
        status.putIfAbsent(id, "ponder.sandbox.waiting");
        dirty.set(true);
    }

    public static String id(JsonObject definition) {
        return HexFormat.of().formatHex(digest().digest(definition.toString().getBytes(StandardCharsets.UTF_8)));
    }

    static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PonderIndex.getGeneration() == 0) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.guiScale != scale || !mc.gameSettings.language.equals(language)
                || mc.gameSettings.forceUnicodeFont != unicode) {
            scale = mc.gameSettings.guiScale;
            language = mc.gameSettings.language;
            unicode = mc.gameSettings.forceUnicodeFont;
            dirty.set(true);
        }
        if (!dirty.getAndSet(false)) return;
        if (reloaded.getAndSet(false)) viewed.clear();
        Map<String, JsonObject> demos = new LinkedHashMap<>(viewed);
        ExternalPonderScenes.visitGuiDemos(mc.getResourceManager(), timeline -> {
            if (timeline instanceof BlockGuiDemoDefinition demo && demo.sandbox() != null
                    && ForgeRegistries.BLOCKS.containsKey(demo.block())) {
                JsonObject json = demo.rawJson();
                demos.putIfAbsent(id(json), json);
            }
        });
        long current = ++revision;
        status.keySet().retainAll(demos.keySet());
        demos.keySet().forEach(id -> status.putIfAbsent(id, "ponder.sandbox.waiting"));
        try {
            SandboxProfile profile = demos.isEmpty() ? null : SandboxProfile.capture(mc);
            executor.execute(() -> refresh(current, demos, profile));
        } catch (RuntimeException | LinkageError failure) {
            demos.keySet().forEach(id -> status.put(id, "ponder.sandbox.failed"));
            Ponder.LOGGER.warn("Could not inspect the client profile for sandbox GUI recording", failure);
        }
    }

    private void refresh(long version, Map<String, JsonObject> demos, SandboxProfile profile) {
        if (revision != version) return;
        long started = System.nanoTime();
        RecordedGuiPlayback.retain(demos.keySet());
        published.keySet().retainAll(demos.keySet());
        if (profile == null) return;
        try {
            String environment = profile.fingerprint();
            if (cacheLease == null) {
                Path cache = profile.game().resolve("ponder-gui-cache");
                Set<Path> protectedGenerations = demos.keySet().stream()
                    .map(id -> cache.resolve("recordings").resolve(id).resolve(environment)).collect(Collectors.toSet());
                cacheLease = SandboxCacheMaintenance.open(cache, protectedGenerations);
            }
            for (var entry : demos.entrySet()) {
                if (revision != version) return;
                try {
                    install(profile, environment, entry.getKey(), entry.getValue(), version);
                } catch (IOException | RuntimeException failure) {
                    if (revision == version) status.put(entry.getKey(), "ponder.sandbox.failed");
                    Ponder.LOGGER.warn("Sandbox GUI for {} could not be recorded", entry.getValue().get("source"), failure);
                }
            }
            Ponder.LOGGER.info("Checked {} sandbox GUI recordings in {} ms", demos.size(), (System.nanoTime() - started) / 1000000);
        } catch (IOException | RuntimeException failure) {
            if (revision == version) demos.keySet().forEach(id -> status.put(id, "ponder.sandbox.failed"));
            Ponder.LOGGER.warn("Could not prepare the sandbox recording environment", failure);
        }
    }

    private void install(SandboxProfile profile, String environment, String id, JsonObject demo, long version) throws IOException {
        Path cache = profile.game().resolve("ponder-gui-cache");
        Path target = cache.resolve("recordings").resolve(id).resolve(environment);
        if (target.equals(published.get(id))) {
            status.remove(id);
            return;
        }
        if (Files.isRegularFile(target.resolve("frames.json"))) {
            try {
                RecordedGuiPlayback.publish(id, target);
                published.put(id, target);
                status.remove(id);
                Ponder.LOGGER.info("Loaded cached sandbox GUI {}", demo.get("source"));
                return;
            } catch (IOException corrupt) {
                Ponder.LOGGER.warn("Replacing damaged GUI recording {}", target, corrupt);
                Files.move(target, target.resolveSibling(environment + ".invalid-" + System.nanoTime()));
            }
        }
        Files.createDirectories(target.getParent());
        Path jobs = Files.createDirectories(cache.resolve("jobs"));
        status.put(id, "ponder.sandbox.recording");
        try {
            recordAttempt(profile, id, demo, version, target, jobs, 1);
        } catch (SandboxProfile.WorkerCrash crash) {
            if (!crash.beforeScript || revision != version) throw crash;
            Ponder.LOGGER.warn("Sandbox worker crashed before scripting {}; retrying in a new world", demo.get("source"));
            recordAttempt(profile, id, demo, version, target, jobs, 2);
        }
    }

    private void recordAttempt(SandboxProfile profile, String id, JsonObject demo, long version, Path target,
            Path jobs, int attempt) throws IOException {
        Path job = Files.createTempDirectory(jobs, "record-");
        try {
            Files.writeString(job.resolve("job.json"), demo.toString());
            profile.record(job);
            // Finish the atomic directory before readers or a second game instance can see it.
            Path ready = Files.createDirectory(job.resolve("ready"));
            Files.move(job.resolve("frames"), ready.resolve("frames"));
            Files.move(job.resolve("frames.json"), ready.resolve("frames.json"));
            RecordingFrames.read(ready);
            try {
                try { Files.move(ready, target, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException unavailable) { Files.move(ready, target); }
            } catch (IOException concurrent) {
                if (!Files.isRegularFile(target.resolve("frames.json"))) throw concurrent;
            }
            if (revision == version) {
                RecordedGuiPlayback.publish(id, target);
                published.put(id, target);
                status.remove(id);
            }
            Ponder.LOGGER.info("Recorded sandbox GUI {} at {}", demo.get("source"), target);
        } catch (IOException failure) {
            Path cache = jobs.getParent();
            Path details = cache.resolve("last-error-" + id + ".log");
            if (failure instanceof SandboxProfile.WorkerCrash crash) {
                Files.copy(crash.report, cache.resolve("last-crash-" + id + "-" + attempt + ".log"),
                    StandardCopyOption.REPLACE_EXISTING);
                Files.copy(crash.report, details, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Path log = Files.isRegularFile(job.resolve("error.txt")) ? job.resolve("error.txt") : job.resolve("worker.log");
                if (Files.isRegularFile(log)) Files.copy(log, details, StandardCopyOption.REPLACE_EXISTING);
            }
            throw failure;
        } finally {
            SandboxProfile.removeJob(jobs, job);
        }
    }
}
