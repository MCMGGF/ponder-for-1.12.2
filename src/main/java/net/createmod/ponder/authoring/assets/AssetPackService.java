package net.createmod.ponder.authoring.assets;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import net.createmod.ponder.authoring.AuthoringDocument;
import net.createmod.ponder.authoring.AuthoringValidation;
import net.createmod.ponder.foundation.PonderSchematic;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Data-only packs. Import is staged, never published, registered or executed. */
public final class AssetPackService {
    public static final class Limits {
        public final long compressedBytes, expandedBytes;
        public final long decodedNbtBytes, decodedPngBytes;
        public final int decodedBlocks;
        public final int entries;
        public Limits(long compressedBytes, long expandedBytes, int entries) {
            this(compressedBytes, expandedBytes, entries, 256L * 1024 * 1024, 1048576, 256L * 1024 * 1024);
        }
        public Limits(long compressedBytes, long expandedBytes, int entries, long decodedNbtBytes, int decodedBlocks, long decodedPngBytes) {
            if (compressedBytes < 1 || compressedBytes > 64L * 1024 * 1024 || expandedBytes < 1
                || expandedBytes > 256L * 1024 * 1024 || entries < 1 || entries > 4096
                || decodedNbtBytes < 1 || decodedNbtBytes > 256L * 1024 * 1024 || decodedBlocks < 1 || decodedBlocks > 1048576
                || decodedPngBytes < 1 || decodedPngBytes > 256L * 1024 * 1024)
                throw new IllegalArgumentException("Pack limits may only narrow production limits");
            this.compressedBytes = compressedBytes; this.expandedBytes = expandedBytes; this.entries = entries;
            this.decodedNbtBytes = decodedNbtBytes; this.decodedBlocks = decodedBlocks; this.decodedPngBytes = decodedPngBytes;
        }
        public static Limits defaults() { return new Limits(64L * 1024 * 1024, 256L * 1024 * 1024, 4096); }
    }
    public static final class EntryInfo {
        public final String path, sha256;
        public final long bytes;
        EntryInfo(String path, byte[] bytes) { this.path = path; this.bytes = bytes.length; this.sha256 = AuthoringDocument.hash(bytes); }
    }
    public static final class ImportPlan {
        public final Path archive;
        public final String archiveSha256;
        public final List<EntryInfo> entries;
        public final List<String> warnings;
        public final boolean destinationExists;
        private final String reviewToken;
        private final Map<String, byte[]> files;
        private final AssetPackService owner;
        private ImportPlan(AssetPackService owner, Path archive, String sha, Map<String, byte[]> files,
            List<String> warnings, boolean exists) {
            this.owner = owner; this.archive = archive; this.archiveSha256 = sha; this.files = files;
            this.entries = inventory(files); this.warnings = Collections.unmodifiableList(warnings);
            this.destinationExists = exists; this.reviewToken = UUID.randomUUID().toString();
        }
        public String getReviewToken() { return reviewToken; }
    }
    public static final class ExportPlan {
        public final List<EntryInfo> entries;
        public final List<String> warnings;
        public final BlueprintCapture.NbtPolicy nbtPolicy;
        private final String reviewToken = UUID.randomUUID().toString();
        private final Map<String, byte[]> files;
        private final AssetPackService owner;
        private ExportPlan(AssetPackService owner, Map<String, byte[]> files, List<String> warnings, BlueprintCapture.NbtPolicy policy) {
            this.owner = owner; this.files = files; this.entries = inventory(files);
            this.warnings = Collections.unmodifiableList(warnings); this.nbtPolicy = policy;
        }
        public String getReviewToken() { return reviewToken; }
    }
    public static final class Receipt {
        public final Path path;
        public final List<EntryInfo> entries;
        public final String sha256;
        Receipt(Path path, List<EntryInfo> entries, String sha) { this.path = path; this.entries = entries; this.sha256 = sha; }
    }
    public static final class InstallReceipt {
        public final List<String> structureIds, texturePaths, imageIds;
        InstallReceipt(List<String> ids, List<String> textures) {
            structureIds = Collections.unmodifiableList(ids); texturePaths = Collections.unmodifiableList(textures);
            List<String> images = new ArrayList<>();
            for (String texture : textures) {
                try { images.add(ImageAssetPool.idFromPackPath("assets/" + texture)); }
                catch (IOException invalid) { throw new IllegalArgumentException("Installed texture has no canonical local image ID", invalid); }
            }
            imageIds = Collections.unmodifiableList(images);
        }
    }
    private final AuthoringAssetPaths paths;
    private final Limits limits;
    private final java.util.function.Function<AuthoringDocument, AuthoringValidation> schemaValidator;
    public AssetPackService(Path authoringRoot) throws IOException { this(authoringRoot, Limits.defaults()); }
    public AssetPackService(Path authoringRoot, Limits limits) throws IOException {
        this(authoringRoot, limits, AuthoringValidation::validate);
    }
    public AssetPackService(Path authoringRoot, java.util.function.Function<AuthoringDocument, AuthoringValidation> schemaValidator) throws IOException {
        this(authoringRoot, Limits.defaults(), schemaValidator);
    }
    public AssetPackService(Path authoringRoot, Limits limits,
        java.util.function.Function<AuthoringDocument, AuthoringValidation> schemaValidator) throws IOException {
        if (limits == null || schemaValidator == null) throw new IOException("Explicit pack limits and a schema review validator are required");
        this.paths = new AuthoringAssetPaths(authoringRoot); this.limits = limits; this.schemaValidator = schemaValidator;
    }

    public ImportPlan previewImport(Path archive) throws IOException {
        Path file = archive.toAbsolutePath().normalize(); AuthoringAssetPaths.verifyAncestors(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > limits.compressedBytes)
            throw new IOException("Pack must be a regular file within the compressed byte budget");
        byte[] encoded = readBoundedFile(file, limits.compressedBytes);
        rejectSpecialZipEntries(encoded);
        Map<String, byte[]> files = readZip(encoded);
        List<String> warnings = validateFiles(files);
        String sha = AuthoringDocument.hash(encoded);
        Path target = paths.resolve("imports/" + sha.toLowerCase(java.util.Locale.ROOT));
        return new ImportPlan(this, file, sha, files, warnings, Files.exists(target, LinkOption.NOFOLLOW_LINKS));
    }

    /** Caller must display the plan and obtain explicit user confirmation before supplying its token. */
    public Receipt confirmImport(ImportPlan plan, String reviewedToken) throws IOException {
        if (plan == null || plan.owner != this || !plan.reviewToken.equals(reviewedToken)) throw new IOException("Reviewed import confirmation is required");
        ImportPlan current = previewImport(plan.archive);
        if (!plan.archiveSha256.equals(current.archiveSha256)) throw new IOException("Archive changed after preview; review again");
        if (current.destinationExists) throw new IOException("Import conflicts with an existing staged pack; nothing will be overwritten");
        paths.ensureRoot();
        Path target = paths.resolve("imports/" + plan.archiveSha256.toLowerCase(java.util.Locale.ROOT));
        Path staging = paths.resolve("imports/staging-" + UUID.randomUUID());
        paths.createParents(staging);
        AuthoringAssetPaths staged = new AuthoringAssetPaths(staging);
        for (Map.Entry<String, byte[]> entry : current.files.entrySet()) staged.writeNew(entry.getKey(), entry.getValue());
        AuthoringAssetPaths.verifyAncestors(target);
        Files.move(staging, target);
        return new Receipt(target, current.entries, current.archiveSha256);
    }

    /** A second explicit activation step: default-private assets only; drafts and published scenes are untouched. */
    public InstallReceipt installImportedAssets(Receipt receipt) throws IOException {
        if (receipt == null || receipt.sha256 == null || !receipt.sha256.matches("[A-F0-9]{64}")) throw new IOException("A staged import receipt is required");
        Path expected = paths.resolve("imports/" + receipt.sha256.toLowerCase(java.util.Locale.ROOT));
        if (!expected.equals(receipt.path.toAbsolutePath().normalize()) || !Files.isDirectory(expected, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Receipt is not an import staged by this authoring workspace");
        AuthoringAssetPaths imported = new AuthoringAssetPaths(expected);
        AuthoringAssetPaths destination = new AuthoringAssetPaths(paths.root().resolve("assets"));
        StructurePool structures = new StructurePool(paths.root());
        Map<String, PonderSchematic> pendingStructures = new LinkedHashMap<>();
        Map<String, byte[]> pendingTextures = new LinkedHashMap<>();
        PonderSchematic.DecodeBudget nbtBudget = newNbtBudget(); long imageBytes = 0;
        for (EntryInfo entry : receipt.entries) {
            Path file = imported.resolve(entry.path);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != entry.bytes) throw new IOException("Staged pack changed: " + entry.path);
            byte[] bytes = readBoundedFile(file, Math.min(entry.bytes, fileByteLimit(entry.path)));
            if (!AuthoringDocument.hash(bytes).equals(entry.sha256)) throw new IOException("Staged pack hash changed: " + entry.path);
            if (entry.path.startsWith("assets/structures/")) {
                String id = entry.path.substring("assets/structures/".length(), entry.path.length() - 4);
                int slash = id.indexOf('/'); id = StructurePool.normalizeId(id.substring(0, slash) + ":" + id.substring(slash + 1));
                Path target = destination.resolve(StructurePool.relativePath(id));
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Structure activation conflict; no assets installed: " + id);
                pendingStructures.put(id, PonderSchematic.loadExternal(new ByteArrayInputStream(bytes), nbtBudget));
            } else if (entry.path.startsWith("assets/textures/")) {
                imageBytes += PngAssetValidator.validate(bytes, limits.decodedPngBytes - imageBytes); String relative = entry.path.substring("assets/".length());
                if (Files.exists(destination.resolve(relative), LinkOption.NOFOLLOW_LINKS)) throw new IOException("Texture activation conflict; no assets installed: " + relative);
                pendingTextures.put(relative, bytes);
            }
        }
        // Ordinary collisions are fully preflighted. Disk failures remain explicit and never overwrite existing files.
        for (Map.Entry<String, PonderSchematic> entry : pendingStructures.entrySet()) structures.saveNew(entry.getKey(), entry.getValue());
        for (Map.Entry<String, byte[]> entry : pendingTextures.entrySet()) destination.writeNew(entry.getKey(), entry.getValue());
        return new InstallReceipt(new ArrayList<>(pendingStructures.keySet()), new ArrayList<>(pendingTextures.keySet()));
    }

    public ExportPlan previewExport(Map<String, byte[]> explicitFiles) throws IOException {
        return previewExport(explicitFiles, BlueprintCapture.NbtPolicy.SAFE_VISUAL_ID_ONLY);
    }
    public ExportPlan previewExport(Map<String, byte[]> explicitFiles, BlueprintCapture.NbtPolicy policy) throws IOException {
        if (explicitFiles == null || policy == null) throw new IOException("Explicit files and privacy policy are required");
        Map<String, byte[]> files = new LinkedHashMap<>();
        PonderSchematic.DecodeBudget nbtBudget = newNbtBudget(); long explicitBytes = 0;
        if (explicitFiles.size() > limits.entries) throw new IOException("Too many export files");
        for (Map.Entry<String, byte[]> entry : explicitFiles.entrySet()) {
            String name = AuthoringAssetPaths.portableRelative(entry.getKey());
            if (entry.getValue() == null) throw new IOException("Missing pack file bytes");
            explicitBytes += entry.getValue().length;
            if (entry.getValue().length > fileByteLimit(name) || explicitBytes > limits.expandedBytes) throw new IOException("Explicit export files exceed their byte budgets");
            byte[] bytes = entry.getValue().clone();
            if (name.startsWith("assets/structures/") && name.endsWith(".nbt")) {
                PonderSchematic source = PonderSchematic.loadExternal(new ByteArrayInputStream(bytes), nbtBudget);
                Map<BlockPos, NBTTagCompound> tiles = new LinkedHashMap<>();
                for (Map.Entry<BlockPos, NBTTagCompound> tile : source.getBlockEntityNbtCopies().entrySet())
                    tiles.put(tile.getKey(), BlueprintCapture.sanitize(tile.getValue(), tile.getKey(), policy));
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                CompressedStreamTools.writeCompressed(PonderSchematic.of(source.getSize(), source.getBlocks(), tiles).toNbt(), output);
                bytes = output.toByteArray();
            }
            files.put(name, bytes);
        }
        JsonObject manifest = files.containsKey("pack.json") ? json(files.get("pack.json")) : new JsonObject();
        if (!manifest.has("formatVersion")) manifest.addProperty("formatVersion", 1);
        manifest.addProperty("type", "ponder-authoring-data"); manifest.addProperty("nbtPolicy", policy.name());
        JsonObject images = new JsonObject();
        for (String name : files.keySet()) if (name.startsWith("assets/textures/"))
            images.addProperty(ImageAssetPool.idFromPackPath(name), name);
        manifest.add("imageAssets", images);
        files.put("pack.json", manifest.toString().getBytes(StandardCharsets.UTF_8));
        List<String> warnings = validateFiles(files);
        if (policy == BlueprintCapture.NbtPolicy.FULL_NBT_EXPLICIT_OPT_IN) warnings.add("Full tile NBT was explicitly enabled; review inventories and personal data before sharing");
        return new ExportPlan(this, files, warnings, policy);
    }

    /** Writes only a new ZIP under exports, never an executable script or a published document. */
    public Receipt confirmExport(ExportPlan plan, String reviewedToken, String filename) throws IOException {
        if (plan == null || plan.owner != this || !plan.reviewToken.equals(reviewedToken)) throw new IOException("Reviewed export confirmation is required");
        if (filename == null || filename.contains("/") || !filename.endsWith(".zip")) throw new IOException("Use a portable ZIP filename");
        AuthoringAssetPaths.portableRelative(filename);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(new java.io.FilterOutputStream(bytes) {
            long written;
            @Override public void write(int value) throws IOException { if (++written > limits.compressedBytes) throw new IOException("Compressed export exceeds the pack budget"); out.write(value); }
            @Override public void write(byte[] data, int offset, int length) throws IOException {
                if (length > limits.compressedBytes - written) throw new IOException("Compressed export exceeds the pack budget");
                written += length; out.write(data, offset, length);
            }
        })) {
            for (Map.Entry<String, byte[]> entry : plan.files.entrySet()) {
                ZipEntry item = new ZipEntry(entry.getKey()); item.setTime(0); zip.putNextEntry(item);
                zip.write(entry.getValue()); zip.closeEntry();
                if (bytes.size() > limits.compressedBytes) throw new IOException("Compressed export exceeds the pack budget");
            }
        }
        if (bytes.size() > limits.compressedBytes) throw new IOException("Compressed export exceeds the pack budget");
        byte[] encoded = bytes.toByteArray(); String relative = "exports/" + filename;
        paths.ensureRoot();
        paths.writeNew(relative, encoded);
        return new Receipt(paths.resolve(relative), plan.entries, AuthoringDocument.hash(encoded));
    }

    private Map<String, byte[]> readZip(byte[] bytes) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>(); Set<String> names = new HashSet<>();
        long total = 0; int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > limits.entries) throw new IOException("Too many ZIP entries");
                String raw = entry.getName();
                String name = AuthoringAssetPaths.portableRelative(entry.isDirectory() && raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw);
                if (!names.add(AuthoringAssetPaths.comparisonKey(name))) throw new IOException("Duplicate or case-colliding ZIP path: " + name);
                long fileLimit = entry.isDirectory() ? 0 : fileByteLimit(name);
                ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (total > limits.expandedBytes) throw new IOException("Expanded ZIP byte budget exceeded");
                    if (entry.isDirectory() && read > 0) throw new IOException("Directory entries must not contain hidden payloads");
                    if (output.size() + (long) read > fileLimit) throw new IOException("ZIP entry exceeds its file-type byte budget: " + name);
                    output.write(buffer, 0, read);
                }
                if (!entry.isDirectory()) files.put(name, output.toByteArray());
                zip.closeEntry();
            }
        } catch (IllegalArgumentException malformed) { throw new IOException("Invalid ZIP filename encoding", malformed); }
        Set<String> fileKeys = new HashSet<>(); for (String file : files.keySet()) fileKeys.add(AuthoringAssetPaths.comparisonKey(file));
        for (String name : names) {
            int slash = name.indexOf('/');
            while (slash >= 0) {
                String prefix = name.substring(0, slash);
                if (fileKeys.contains(prefix)) throw new IOException("File/directory conflict: " + prefix);
                slash = name.indexOf('/', slash + 1);
            }
        }
        return files;
    }

    private List<String> validateFiles(Map<String, byte[]> files) throws IOException {
        if (files.size() > limits.entries || !files.containsKey("pack.json")) throw new IOException("A bounded pack.json manifest is required");
        Set<String> keys = new HashSet<>(); List<String> warnings = new ArrayList<>(); long total = 0;
        Set<String> imageReferences = new java.util.LinkedHashSet<>(); JsonObject manifest = null;
        PonderSchematic.DecodeBudget nbtBudget = newNbtBudget(); long imageBytes = 0;
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String name = AuthoringAssetPaths.portableRelative(entry.getKey());
            if (!keys.add(AuthoringAssetPaths.comparisonKey(name))) throw new IOException("Duplicate/case-colliding pack path");
            byte[] bytes = entry.getValue(); total += bytes.length;
            if (total > limits.expandedBytes || bytes.length > fileByteLimit(name)) throw new IOException("Expanded pack/file-type byte budget exceeded");
            if ("pack.json".equals(name)) {
                manifest = json(bytes);
                if (!manifest.has("formatVersion") || !manifest.get("formatVersion").isJsonPrimitive()
                    || !manifest.get("formatVersion").getAsJsonPrimitive().isNumber() || manifest.get("formatVersion").getAsDouble() != 1)
                    throw new IOException("Unsupported pack formatVersion");
            } else if (name.matches("documents/[a-z0-9_.-]+/[a-z0-9_./-]+\\.json")) {
                AuthoringDocument document;
                try { document = AuthoringDocument.fromJson(json(bytes)); }
                catch (RuntimeException malformed) { throw new IOException("Invalid authoring JSON: " + name, malformed); }
                AuthoringValidation review;
                try { review = schemaValidator.apply(document); }
                catch (RuntimeException invalid) { throw new IOException("Document schema review failed: " + name, invalid); }
                if (review == null) throw new IOException("Document schema reviewer returned no result: " + name);
                warnings.add(name + ": SCHEMA_ONLY - handler parameters and installed assets require full validation and compilation before publishing");
                for (AuthoringValidation.Issue issue : review.getIssues()) warnings.add(name + ": " + issue);
                imageReferences.addAll(ImageAssetPool.referencedIds(document));
            } else if (name.matches("assets/structures/[a-z0-9_.-]+/[a-z0-9_./-]+\\.nbt")) {
                PonderSchematic.loadExternal(new ByteArrayInputStream(bytes), nbtBudget);
            } else if (name.matches("assets/textures/[a-z0-9_.-]+/[a-z0-9_./-]+\\.png")) {
                ImageAssetPool.idFromPackPath(name);
                imageBytes += PngAssetValidator.validate(bytes, limits.decodedPngBytes - imageBytes);
            }
            else throw new IOException("Pack permits only manifest, document JSON, structure NBT and PNG textures: " + name);
        }
        for (String key : keys) { int slash = key.lastIndexOf('/'); while (slash >= 0) {
            if (keys.contains(key.substring(0, slash))) throw new IOException("Pack file/directory conflict");
            slash = key.lastIndexOf('/', slash - 1);
        } }
        if (manifest != null && manifest.has("imageAssets")) {
            if (!manifest.get("imageAssets").isJsonObject() || manifest.getAsJsonObject("imageAssets").entrySet().size() > limits.entries)
                throw new IOException("imageAssets must be a bounded local ID-to-pack-path object");
            for (Map.Entry<String, com.google.gson.JsonElement> mapping : manifest.getAsJsonObject("imageAssets").entrySet()) {
                String expected = "assets/" + ImageAssetPool.relativePath(mapping.getKey());
                if (!mapping.getValue().isJsonPrimitive() || !mapping.getValue().getAsJsonPrimitive().isString()
                    || !expected.equals(mapping.getValue().getAsString()) || !files.containsKey(expected))
                    throw new IOException("Image manifest mapping must match an included canonical texture path: " + mapping.getKey());
            }
        }
        for (String image : imageReferences) if (!files.containsKey("assets/" + ImageAssetPool.relativePath(image)))
            warnings.add("Missing image asset (requires explicit local installation before publishing): " + image);
        return warnings;
    }
    private static JsonObject json(byte[] bytes) throws IOException {
        if (bytes.length > AuthoringDocument.MAX_JSON_BYTES) throw new IOException("JSON file exceeds two MiB");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            AuthoringDocument.parse(text);
            return new JsonParser().parse(text).getAsJsonObject();
        } catch (RuntimeException invalid) { throw new IOException("Invalid JSON", invalid); }
    }
    private PonderSchematic.DecodeBudget newNbtBudget() { return new PonderSchematic.DecodeBudget(limits.decodedNbtBytes, limits.decodedBlocks); }
    private static long fileByteLimit(String name) throws IOException {
        if ("pack.json".equals(name) || name.matches("documents/[a-z0-9_.-]+/[a-z0-9_./-]+\\.json")) return AuthoringDocument.MAX_JSON_BYTES;
        if (name.matches("assets/structures/[a-z0-9_.-]+/[a-z0-9_./-]+\\.nbt")) return 64L * 1024 * 1024;
        if (name.matches("assets/textures/[a-z0-9_.-]+/[a-z0-9_./-]+\\.png")) return PngAssetValidator.MAX_FILE_BYTES;
        throw new IOException("Pack permits only manifest, document JSON, structure NBT and PNG textures: " + name);
    }
    private static byte[] readBoundedFile(Path file, long limit) throws IOException {
        if (limit < 0 || limit > 256L * 1024 * 1024) throw new IOException("Invalid file read budget");
        AuthoringAssetPaths.verifyAncestors(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Expected a regular non-symbolic file");
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; long total = 0;
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            int read; while ((read = input.read(buffer)) != -1) {
                total += read; if (total > limit) throw new IOException("File exceeds its bounded read budget"); output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }
    private static List<EntryInfo> inventory(Map<String, byte[]> files) {
        List<EntryInfo> entries = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) entries.add(new EntryInfo(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableList(entries);
    }

    /** Inspect central attributes because ZipInputStream does not expose symlink/reparse metadata. ZIP64 is unnecessary within these caps. */
    private void rejectSpecialZipEntries(byte[] bytes) throws IOException {
        java.nio.ByteBuffer input = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int eocd = -1;
        for (int p = bytes.length - 22; p >= Math.max(0, bytes.length - 65557); p--)
            if (input.getInt(p) == 0x06054b50 && p + 22 + unsignedShort(input, p + 20) == bytes.length) { eocd = p; break; }
        if (eocd < 0) throw new IOException("ZIP end record not found");
        if (unsignedShort(input, eocd + 4) != 0 || unsignedShort(input, eocd + 6) != 0) throw new IOException("Split ZIPs are not supported");
        int diskCount = unsignedShort(input, eocd + 8), count = unsignedShort(input, eocd + 10);
        long size = unsignedInt(input, eocd + 12), offset = unsignedInt(input, eocd + 16);
        if (count != diskCount || count == 65535 || count > limits.entries || offset + size != eocd) throw new IOException("Invalid/ZIP64 central directory");
        long start = offset;
        for (int i = 0; i < count; i++) {
            if (start < 0 || start + 46 > offset + size) throw new IOException("Truncated ZIP central entry");
            int p = (int) start;
            if (input.getInt(p) != 0x02014b50) throw new IOException("Invalid ZIP central entry");
            int madeBy = unsignedShort(input, p + 4), flags = unsignedShort(input, p + 8);
            if ((flags & 1) != 0) throw new IOException("Encrypted ZIP entries are not supported");
            int name = unsignedShort(input, p + 28), extra = unsignedShort(input, p + 30), comment = unsignedShort(input, p + 32);
            long attributes = unsignedInt(input, p + 38), local = unsignedInt(input, p + 42); int unixType = (int) ((attributes >> 16) & 0xf000);
            if (unixType == 0xa000 || (unixType != 0 && unixType != 0x8000 && unixType != 0x4000)
                || ((madeBy >> 8) == 0 && (attributes & 0x400) != 0)) throw new IOException("Symlink/reparse/special ZIP entries are forbidden");
            start += 46L + name + extra + comment;
            if (local >= offset || start > offset + size) throw new IOException("Invalid ZIP entry offsets");
        }
        if (start != offset + size) throw new IOException("Unexpected ZIP central bytes");
    }
    private static int unsignedShort(java.nio.ByteBuffer input, int at) { return input.getShort(at) & 65535; }
    private static long unsignedInt(java.nio.ByteBuffer input, int at) { return input.getInt(at) & 0xffffffffL; }
}
