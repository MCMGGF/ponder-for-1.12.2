package net.createmod.ponder.foundation.ui.sandbox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SandboxCacheMaintenanceTest {
    @TempDir Path root;

    @Test
    void aSecondClientCanReadWithoutEvictingTheFirstClientsFrames() throws Exception {
        Path cache = root.toRealPath();
        Path id = Files.createDirectories(cache.resolve("recordings").resolve("a".repeat(64)));
        Path old = generation(id, "1".repeat(64), Instant.now().minus(Duration.ofDays(40)));
        Path java = ProcessHandle.current().info().command().map(Path::of).orElseThrow();
        String classes = Path.of(CacheReader.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        Process reader = new ProcessBuilder(java.toString(), "-cp", classes, CacheReader.class.getName(),
            cache.resolve(".session.lock").toString()).start();
        try {
            assertEquals(1, reader.getInputStream().read());
            try (var session = SandboxCacheMaintenance.open(cache, Set.of())) {
                assertTrue(Files.exists(old));
            }
        } finally {
            reader.getOutputStream().close();
            reader.destroyForcibly().waitFor();
        }
        try (var session = SandboxCacheMaintenance.open(cache, Set.of())) {
            assertFalse(Files.exists(old));
        }
    }

    public static final class CacheReader {
        public static void main(String[] args) throws Exception {
            try (var file = FileChannel.open(Path.of(args[0]), StandardOpenOption.CREATE, StandardOpenOption.READ,
                     StandardOpenOption.WRITE);
                 var lock = file.lock(0, Long.MAX_VALUE, true)) {
                System.out.write(1);
                System.out.flush();
                System.in.read();
            }
        }
    }

    @Test
    void evictsOldestFirstAndPreservesSelectedAndUnknownDirectories() throws Exception {
        Path cache = root.toRealPath();
        Instant now = Instant.now();
        Path id = Files.createDirectories(cache.resolve("recordings").resolve("a".repeat(64)));
        Path selected = generation(id, "1".repeat(64), now.minus(Duration.ofDays(40)));
        Path older = generation(id, "2".repeat(64), now.minus(Duration.ofDays(4)));
        Path newer = generation(id, "3".repeat(64), now);
        Path foreign = generation(id, "notes", now.minus(Duration.ofDays(45)));
        SandboxCacheMaintenance.sweep(cache, Set.of(selected), now, 20);
        assertTrue(Files.exists(selected));
        assertFalse(Files.exists(older));
        assertTrue(Files.exists(newer));
        assertTrue(Files.exists(foreign));
    }

    @Test
    void preservesLiveWorkersAndUnidentifiedJobs() throws Exception {
        Path cache = root.toRealPath();
        Instant now = Instant.now();
        Path jobs = Files.createDirectory(cache.resolve("jobs"));
        for (String name : Set.of("live", "unknown", "dead")) {
            Path job = Files.createDirectory(jobs.resolve("record-" + name));
            if (!name.equals("unknown")) Files.writeString(job.resolve("worker.pid"),
                name.equals("live") ? Long.toString(ProcessHandle.current().pid()) : "2147483646");
            Files.setLastModifiedTime(job, FileTime.from(now.minus(Duration.ofDays(8))));
        }
        SandboxCacheMaintenance.sweep(cache, Set.of(), now, 100);
        assertTrue(Files.exists(jobs.resolve("record-live")));
        assertTrue(Files.exists(jobs.resolve("record-unknown")));
        assertFalse(Files.exists(jobs.resolve("record-dead")));
    }

    private Path generation(Path id, String key, Instant timestamp) throws Exception {
        Path path = Files.createDirectory(id.resolve(key));
        Files.write(path.resolve("frames.json"), new byte[10]);
        Files.setLastModifiedTime(path, FileTime.from(timestamp));
        return path;
    }
}
