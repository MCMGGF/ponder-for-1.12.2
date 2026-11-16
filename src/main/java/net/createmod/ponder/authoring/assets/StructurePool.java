package net.createmod.ponder.authoring.assets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.createmod.ponder.authoring.AuthoringCompiler;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** External structure resolver. IDs are data identifiers, never arbitrary filesystem paths. */
public final class StructurePool implements AuthoringCompiler.BudgetedAssetResolver {
    private final AuthoringAssetPaths assets;
    private final AuthoringAssetPaths authoring;
    public StructurePool(Path authoringRoot) throws IOException {
        authoring = new AuthoringAssetPaths(authoringRoot); assets = new AuthoringAssetPaths(authoringRoot.resolve("assets"));
    }
    public Path root() { return assets.root(); }

    public java.util.List<String> listIds() throws IOException {
        Path directory = assets.resolve("structures");
        if (!java.nio.file.Files.exists(directory, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return java.util.Collections.emptyList();
        java.util.List<String> result = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> entries = java.nio.file.Files.walk(directory)) {
            java.util.Iterator<Path> iterator = entries.iterator(); int visited = 0;
            while (iterator.hasNext()) {
                Path file = iterator.next();
                if (++visited > 8192) throw new IOException("Structure pool listing budget exceeded");
                AuthoringAssetPaths.verifyAncestors(file);
                if (!java.nio.file.Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS) || !file.toString().endsWith(".nbt")) continue;
                String relative = directory.relativize(file).toString().replace(java.io.File.separatorChar, '/');
                int slash = relative.indexOf('/');
                if (slash < 1) continue;
                result.add(normalizeId(relative.substring(0, slash) + ":" + relative.substring(slash + 1, relative.length() - 4)));
            }
        }
        java.util.Collections.sort(result); return java.util.Collections.unmodifiableList(result);
    }

    public static String normalizeId(String value) throws IOException {
        if (value == null || !value.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+")) throw new IOException("Invalid structure asset ID");
        String id = value.indexOf(':') < 0 ? "ponder_authoring:" + value : value;
        String[] split = id.split(":", -1);
        if (split.length != 2) throw new IOException("Invalid structure namespace");
        AuthoringAssetPaths.portableRelative(split[0] + "/" + split[1]);
        return id;
    }

    public static String relativePath(String value) throws IOException {
        return "structures/" + normalizeId(value).replace(':', '/') + ".nbt";
    }

    @Override public PonderSchematic resolve(String assetId) throws IOException {
        return PonderSchematic.loadExternal(assets.resolve(relativePath(assetId)));
    }

    @Override public PonderSchematic resolve(String assetId, PonderSchematic.DecodeBudget budget) throws IOException {
        return PonderSchematic.loadExternal(assets.resolve(relativePath(assetId)), budget);
    }

    public void saveNew(String assetId, PonderSchematic source) throws IOException {
        saveNew(assetId, source, BlueprintCapture.NbtPolicy.SAFE_VISUAL_ID_ONLY);
    }

    public void saveNew(String assetId, PonderSchematic source, BlueprintCapture.NbtPolicy policy) throws IOException {
        if (policy == null) throw new IOException("An explicit NBT policy is required");
        validateSource(source);
        Map<BlockPos, NBTTagCompound> nbt = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, NBTTagCompound> entry : source.getBlockEntityNbtCopies().entrySet())
            nbt.put(entry.getKey(), BlueprintCapture.sanitize(entry.getValue(), entry.getKey(), policy));
        PonderSchematic sanitized = PonderSchematic.of(source.getSize(), source.getBlocks(), nbt);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(sanitized.toNbt(), bytes);
        authoring.ensureRoot();
        assets.writeNew(relativePath(assetId), bytes.toByteArray());
    }

    public static void validateSource(PonderSchematic source) throws IOException {
        if (source == null) throw new IOException("Structure is required");
        BlockPos size = source.getSize();
        if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1 || size.getX() > 64 || size.getY() > 64 || size.getZ() > 64
            || source.getBlocks().isEmpty() || source.getBlocks().size() > PonderSchematic.MAX_BLOCKS) throw new IOException("Each nonempty structure must fit 64 cubed");
        for (BlockPos pos : source.getBlocks().keySet()) if (pos.getX() < 0 || pos.getY() < 0 || pos.getZ() < 0
            || pos.getX() >= size.getX() || pos.getY() >= size.getY() || pos.getZ() >= size.getZ())
            throw new IOException("Structure source positions must fit its local bounds");
    }
}
