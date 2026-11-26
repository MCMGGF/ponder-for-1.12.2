package net.createmod.ponder.foundation.ui.sandbox;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.createmod.ponder.foundation.ui.sandbox.worker.SandboxWindowAgent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Launch inputs are reconstructed from installed libraries and pack files. Never copy the host session or saves. */
record SandboxProfile(Path game, Path assets, List<Pack> packs, String version, String language,
                      int guiScale, boolean unicode, int width, int height, String classpath) {
    record Pack(Path source, String name) {}
    private static final List<String> INPUTS = List.of("mods", "config", "scripts", "resources", "patchouli_books",
        "fancymenu_data", "fancymenu_setups");

    static SandboxProfile capture(Minecraft mc) {
        File assets = ReflectionHelper.getPrivateValue(Minecraft.class, mc, "fileAssets", "field_110446_Y");
        List<Pack> packs = new ArrayList<>();
        var repository = mc.getResourcePackRepository();
        for (var entry : repository.getRepositoryEntries()) {
            if (entry.getResourcePack() instanceof AbstractResourcePack pack) {
                Path source = packPath(pack);
                packs.add(new Pack(source, source.getFileName().toString()));
            }
        }
        if (repository.getServerResourcePack() instanceof AbstractResourcePack serverPack) {
            Path source = packPath(serverPack);
            String name = "ponder-server-pack.zip";
            while (hasPackName(packs, name)) name = "_" + name;
            packs.add(new Pack(source, name));
        }
        String cp = System.getProperty("java.class.path");
        return new SandboxProfile(mc.gameDir.toPath().toAbsolutePath().normalize(), assets.toPath().toAbsolutePath(),
            List.copyOf(packs), mc.getVersion(), mc.gameSettings.language, mc.gameSettings.guiScale,
            mc.gameSettings.forceUnicodeFont, mc.displayWidth, mc.displayHeight, cp);
    }

    private static Path packPath(AbstractResourcePack pack) {
        File file = ReflectionHelper.getPrivateValue(AbstractResourcePack.class, pack, "resourcePackFile", "field_110597_b");
        return file.toPath().toAbsolutePath();
    }

    private static boolean hasPackName(List<Pack> packs, String name) {
        return packs.stream().anyMatch(pack -> pack.name().equalsIgnoreCase(name));
    }

    private List<Path> inputs() {
        List<Path> paths = new ArrayList<>();
        for (String dir : INPUTS) paths.add(game.resolve(dir));
        return paths;
    }

    String fingerprint() throws IOException {
        MessageDigest digest = SandboxRecordings.digest();
        update(digest, "sandbox-v1/" + version + "/" + language + "/" + guiScale + "/" + unicode + "/" + width + "/" + height);
        byte[] buffer = new byte[65536];
        for (Path dir : inputs()) hashTree(digest, dir, buffer, true);
        for (Pack pack : packs) {
            update(digest, pack.name());
            hashTree(digest, pack.source(), buffer, false);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static void hashTree(MessageDigest digest, Path root, byte[] buffer, boolean gameInput) throws IOException {
        if (!Files.exists(root)) return;
        boolean configuration = gameInput && root.getFileName().toString().equals("config");
        boolean firstFile = true;
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                Path relative = root.relativize(file);
                String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (gameInput && (skipFile(root, file) || separateSceneInput(root, relative) || loadingTimings(root, relative))
                    || configuration && name.contains("cache") && (name.endsWith(".dat") || name.endsWith(".nbt") || name.endsWith(".bin")))
                    continue;
                if (firstFile) {
                    update(digest, root.getFileName().toString());
                    firstFile = false;
                }
                update(digest, relative.toString());
                if (configuration && (name.endsWith(".cfg") || name.endsWith(".ini") || name.endsWith(".properties"))) {
                    hashConfig(digest, file, name);
                    continue;
                }
                update(digest, Long.toString(Files.size(file)));
                try (InputStream in = Files.newInputStream(file)) {
                    int count;
                    while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
                }
            }
        }
    }

    private static final Pattern FORGE_LIST = Pattern.compile("[A-Za-z]:.+\\s*<");

    private static void hashConfig(MessageDigest digest, Path file, String name) throws IOException {
        MessageDigest content = SandboxRecordings.digest();
        long length = 0;
        boolean continuation = false;
        boolean list = false;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (!list && !continuation && (trimmed.startsWith("#")
                    || name.endsWith(".ini") && trimmed.startsWith(";")
                    || name.endsWith(".properties") && trimmed.startsWith("!"))) continue;
                byte[] bytes = line.getBytes(StandardCharsets.ISO_8859_1);
                content.update(bytes);
                content.update((byte) '\n');
                length += bytes.length + 1L;
                if (list) {
                    list = !trimmed.equals(">");
                    continue;
                }
                if (!continuation && name.endsWith(".cfg") && trimmed.endsWith("<"))
                    list = FORGE_LIST.matcher(trimmed).matches();
                int end = line.length();
                while (end > 0 && line.charAt(end - 1) == '\\') end--;
                continuation = (line.length() - end) % 2 != 0;
            }
        }
        update(digest, Long.toString(length));
        digest.update(content.digest());
    }

    // Each resolved scene has its own digest. Editing one JSON file must leave unrelated recordings usable.
    static boolean separateSceneInput(Path root, Path relative) {
        String group = root.getFileName().toString();
        return (group.equals("config") || group.equals("scripts")) && relative.getNameCount() > 1
            && relative.getName(0).toString().equals("ponder")
            && relative.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".json");
    }

    private static boolean loadingTimings(Path root, Path relative) {
        if (!root.getFileName().toString().equals("config") || relative.getNameCount() != 1) return false;
        String name = relative.toString();
        return name.contains("load") && (name.endsWith("_timings.dat") || name.endsWith("_timings.nbt"));
    }

    private static boolean skipFile(Path root, Path file) throws IOException {
        Path relative = root.relativize(file);
        if (root.endsWith("fancymenu_data") && relative.equals(Path.of("last_world.fmdata"))) return true;
        if (root.endsWith("config") && relative.equals(Path.of("jei", "worldSettings.cfg")))
            return containsOnlyWorldPreferences(file);
        if (root.getFileName().toString().equals("mods")) {
            String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            return !name.endsWith(".jar") && !name.endsWith(".zip");
        }
        return false;
    }

    private static final Pattern WORLD_GROUP = Pattern.compile("world-?[0-9]+\\s*\\{");
    private static final Pattern FORGE_SETTING = Pattern.compile("[BIDS]:[^=]+=[^\\r\\n]*");

    private static boolean containsOnlyWorldPreferences(Path file) throws IOException {
        boolean inWorld = false;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String value = line.strip();
                if (value.isEmpty() || value.startsWith("#")) continue;
                if (!inWorld) {
                    if (value.startsWith("~CONFIG_VERSION:")) continue;
                    if (!WORLD_GROUP.matcher(value).matches()) return false;
                    inWorld = true;
                } else if (value.equals("}")) {
                    inWorld = false;
                } else if (!FORGE_SETTING.matcher(value).matches()) {
                    return false;
                }
            }
        }
        return !inWorld;
    }

    private static void copyTree(Path src, Path dst) throws IOException {
        if (!Files.exists(src, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(src)) {
            for (Path file : (Iterable<Path>) paths::iterator) {
                Path target = dst.resolve(src.relativize(file));
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) Files.createDirectories(target);
                else if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !skipFile(src, file)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(file, target);
                }
            }
        }
    }

    void record(Path job) throws IOException {
        Path isolated = Files.createDirectory(job.resolve("game"));
        for (Path src : inputs()) copyTree(src, isolated.resolve(src.getFileName()));
        for (Pack pack : packs) copyTree(pack.source(), isolated.resolve("resourcepacks").resolve(pack.name()));
        List<String> options = new ArrayList<>();
        // Preserve resource ordering and client text settings without carrying over lastServer or credentials.
        var packNames = new com.google.gson.JsonArray();
        for (Pack pack : packs) packNames.add(pack.name());
        options.add("resourcePacks:" + packNames);
        options.add("lang:" + language);
        options.add("guiScale:" + guiScale);
        options.add("forceUnicodeFont:" + unicode);
        options.addAll(List.of("fullscreen:false", "pauseOnLostFocus:false", "renderDistance:2",
            "enableVsync:false", "maxFps:30", "soundCategory_master:0.0", "tutorialStep:none"));
        Files.write(isolated.resolve("options.txt"), options, StandardCharsets.UTF_8);
        String foundation = "top.outlands.foundation.boot.Foundation";
        try { Class.forName(foundation, false, SandboxProfile.class.getClassLoader()); }
        catch (ClassNotFoundException absent) { throw new IOException("Cleanroom Foundation launcher is required", absent); }
        List<String> args = new ArrayList<>();
        SandboxWindowAgent.create(job);
        args.add("-javaagent:../worker-window.jar");
        long heap = Math.clamp(Runtime.getRuntime().maxMemory() / (1024 * 1024), 768, 4096);
        heap = Math.max(768, Long.getLong("ponder.sandbox.maxHeapMiB", heap));
        args.addAll(List.of("-Xms128m", "-Xmx" + heap + "m",
            "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow", "-Dfile.encoding=UTF-8",
            "-Djava.library.path=" + absolutePaths(System.getProperty("java.library.path", "")),
            "-D" + SandboxRecordings.JOB + "=..", "-Dponder.sandbox.parent=" + ProcessHandle.current().pid(),
            "-cp", absolutePaths(classpath), foundation,
            "--gameDir", isolated.toString(), "--assetsDir", assets.toString(), "--assetIndex", "1.12",
            "--version", version, "--username", "PonderRecorder", "--uuid",
            UUID.nameUUIDFromBytes("OfflinePlayer:PonderRecorder".getBytes(StandardCharsets.UTF_8)).toString().replace("-", ""),
            "--accessToken", "0", "--userType", "legacy", "--tweakClass", "net.minecraftforge.fml.common.launcher.FMLTweaker",
            "--width", Integer.toString(width), "--height", Integer.toString(height)));
        Charset nativeEncoding = Charset.forName(System.getProperty("native.encoding", Charset.defaultCharset().name()));
        Path bootstrap = null;
        try {
            if (!nativeEncoding.newEncoder().canEncode(job + "\n" + String.join("\n", args))) {
                bootstrap = SandboxWindowAgent.prepareUnicodeLaunch(job, args, foundation, nativeEncoding);
            }
            runWorker(job, isolated, args, nativeEncoding);
        } finally {
            if (bootstrap != null) Files.deleteIfExists(bootstrap);
        }

    }

    private static void runWorker(Path job, Path isolated, List<String> args, Charset nativeEncoding) throws IOException {
        Path argfile = job.resolve("launch.args");
        Files.write(argfile, args.stream().map(SandboxProfile::quote).toList(), nativeEncoding);
        String java = System.getProperty("os.name", "").startsWith("Windows") ? "javaw.exe" : "java";
        Path binary = Path.of(System.getProperty("java.home"), "bin", java);
        ProcessBuilder builder = new ProcessBuilder(binary.toString(), "@../launch.args").directory(isolated.toFile())
            .redirectErrorStream(true).redirectOutput(job.resolve("worker.log").toFile());
        for (String key : List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS")) builder.environment().remove(key);
        Process child = builder.start();
        try {
            SandboxCacheMaintenance.trackWorker(job, child);
            awaitWorker(child, job);
            Path error = job.resolve("error.txt");
            if (Files.exists(error)) throw new IOException(Files.readString(error));
            if (child.exitValue() != 0 || !Files.isRegularFile(job.resolve("frames.json")))
                throw new IOException("Sandbox worker exited " + child.exitValue() + " without a complete recording");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while recording", interrupted);
        } finally {
            if (child.isAlive()) child.destroyForcibly().onExit().join();
        }
    }

    static final class WorkerCrash extends IOException {
        final Path report;
        final boolean beforeScript;

        WorkerCrash(Path job, Path report) {
            super("Sandbox worker generated " + report.getFileName());
            this.report = report;
            this.beforeScript = !Files.exists(job.resolve("script-started"));
        }
    }

    static void awaitWorker(Process child, Path job) throws IOException, InterruptedException {
        Path reports = job.resolve("game/crash-reports");
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(30);
        while (true) {
            boolean exited = child.waitFor(1, TimeUnit.SECONDS);
            if (Files.isDirectory(reports, LinkOption.NOFOLLOW_LINKS)) {
                try (var files = Files.newDirectoryStream(reports, "crash-*.txt")) {
                    for (Path report : files) {
                        if (Files.isRegularFile(report, LinkOption.NOFOLLOW_LINKS) && Files.size(report) > 0)
                            throw new WorkerCrash(job, report);
                    }
                }
            }
            if (exited) return;
            if (System.nanoTime() - deadline >= 0) throw new IOException("Sandbox worker exceeded 30 minutes");
        }
    }

    private static String absolutePaths(String value) {
        return java.util.Arrays.stream(value.split(Pattern.quote(File.pathSeparator)))
            .map(part -> Path.of(part).toAbsolutePath().normalize().toString()).collect(java.util.stream.Collectors.joining(File.pathSeparator));
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static void removeJob(Path jobs, Path job) throws IOException {
        if (!jobs.toRealPath().equals(job.toRealPath().getParent()) || !job.getFileName().toString().startsWith("record-"))
            throw new IOException("Unexpected sandbox job location " + job);
        Files.walkFileTree(job, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
