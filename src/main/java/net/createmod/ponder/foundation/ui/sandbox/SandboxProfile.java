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
                Path source = pack.getResourcePackFile().toPath().toAbsolutePath();
                packs.add(new Pack(source, source.getFileName().toString()));
            }
        }
        if (repository.getServerResourcePack() instanceof AbstractResourcePack serverPack) {
            Path source = serverPack.getResourcePackFile().toPath().toAbsolutePath();
            String name = "ponder-server-pack.zip";
            while (hasPackName(packs, name)) name = "_" + name;
            packs.add(new Pack(source, name));
        }
        String cp = System.getProperty("java.class.path");
        return new SandboxProfile(mc.gameDir.toPath().toAbsolutePath().normalize(), assets.toPath().toAbsolutePath(),
            List.copyOf(packs), mc.getVersion(), mc.gameSettings.language, mc.gameSettings.guiScale,
            mc.gameSettings.forceUnicodeFont, mc.displayWidth, mc.displayHeight, cp);
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
        boolean firstFile = true;
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).sorted().toList()) {
                Path relative = root.relativize(file);
                if (gameInput && (skipFile(root, file) || separateSceneInput(root, relative) || loadingTimings(root, relative)))
                    continue;
                if (firstFile) {
                    update(digest, root.getFileName().toString());
                    firstFile = false;
                }
                update(digest, relative.toString());
                update(digest, Long.toString(Files.size(file)));
                try (InputStream in = Files.newInputStream(file)) {
                    int count;
                    while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
                }
            }
        }
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

    private static boolean skipFile(Path root, Path file) {
        if (root.getFileName().toString().equals("mods")) {
            String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            return !name.endsWith(".jar") && !name.endsWith(".zip");
        }
        return false;
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
            if (!child.waitFor(30, TimeUnit.MINUTES))
                throw new IOException("Sandbox worker exceeded 30 minutes");
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
