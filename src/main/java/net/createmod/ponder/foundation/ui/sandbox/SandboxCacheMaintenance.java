package net.createmod.ponder.foundation.ui.sandbox;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Set;

/** Sweep only before publication, while no other client can be using this cache. */
final class SandboxCacheMaintenance {
    private static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final String HASH = "[0-9a-f]{64}";
    private static final LinkOption NO_LINK = LinkOption.NOFOLLOW_LINKS;

    private record Generation(Path path, Instant modified, long bytes) {}

    static FileChannel open(Path cache, Set<Path> protectedGenerations) throws IOException {
        Path root = Files.createDirectories(cache).toRealPath();
        FileChannel channel = FileChannel.open(root.resolve(".session.lock"), StandardOpenOption.CREATE,
            StandardOpenOption.READ, StandardOpenOption.WRITE, NO_LINK);
        try {
            try (FileLock exclusive = channel.tryLock()) {
                if (exclusive != null && root.equals(cache.toAbsolutePath().normalize())) {
                    try {
                        sweep(root, protectedGenerations, Instant.now(), MAX_BYTES);
                    } catch (IOException | SecurityException unavailable) {
                        net.createmod.ponder.Ponder.LOGGER.warn("Skipping sandbox cache cleanup", unavailable);
                    }
                }
            }
            channel.lock(0, Long.MAX_VALUE, true);
            return channel;
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
    }

    static void trackWorker(Path job, Process worker) throws IOException {
        Files.writeString(job.resolve("worker.pid"), Long.toString(worker.pid()), StandardOpenOption.CREATE_NEW);
    }

    static void sweep(Path root, Set<Path> protectedGenerations, Instant now, long budget) throws IOException {
        Path jobs = root.resolve("jobs");
        if (plainDirectory(jobs)) {
            try (var list = Files.newDirectoryStream(jobs, "record-*")) {
                for (Path job : list) {
                    Path pid = job.resolve("worker.pid");
                    if (!plainDirectory(job) || !Files.isRegularFile(pid, NO_LINK) || Files.size(pid) > 20
                        || !modified(job).isBefore(now.minus(Duration.ofDays(7)))) continue;
                    String owner = Files.readString(pid);
                    try {
                        long id = Long.parseLong(owner);
                        if (id > 0 && ProcessHandle.of(id).isEmpty()) deleteTree(job);
                    } catch (NumberFormatException incomplete) { /* Preserve unidentified jobs. */ }
                }
            }
        }
        Path recordings = root.resolve("recordings");
        if (!plainDirectory(recordings)) return;
        var generations = new ArrayList<Generation>();
        long total = 0;
        try (var ids = Files.newDirectoryStream(recordings)) {
            for (Path id : ids) {
                if (!id.getFileName().toString().matches(HASH) || !plainDirectory(id)) continue;
                try (var dirs = Files.newDirectoryStream(id)) {
                    for (Path dir : dirs) {
                        if (!dir.getFileName().toString().matches(HASH + "(?:\\.invalid--?[0-9]+)?")
                            || !plainDirectory(dir)) continue;
                        long bytes = size(dir);
                        total += bytes;
                        if (!protectedGenerations.contains(dir))
                            generations.add(new Generation(dir, modified(dir), bytes));
                    }
                }
            }
        }
        generations.sort(Comparator.comparing(Generation::modified));
        for (Generation generation : generations) {
            if (total <= budget && !generation.modified.isBefore(now.minus(Duration.ofDays(30)))) break;
            deleteTree(generation.path);
            total -= generation.bytes;
        }
    }

    private static Instant modified(Path dir) throws IOException {
        return Files.getLastModifiedTime(dir, NO_LINK).toInstant();
    }

    private static boolean plainDirectory(Path path) throws IOException {
        return Files.isDirectory(path, NO_LINK) && path.equals(path.toRealPath());
    }

    private static long size(Path dir) throws IOException {
        long[] bytes = {0};
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                if (!plainDirectory(path)) throw new IOException("Unexpected linked cache directory " + path);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) bytes[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return bytes[0];
    }

    private static void deleteTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!plainDirectory(dir)) throw new IOException("Unexpected linked cache directory " + dir);
                return FileVisitResult.CONTINUE;
            }
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
