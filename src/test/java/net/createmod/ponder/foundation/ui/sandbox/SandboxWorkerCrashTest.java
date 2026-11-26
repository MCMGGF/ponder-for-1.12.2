package net.createmod.ponder.foundation.ui.sandbox;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SandboxWorkerCrashTest {
    @TempDir Path job;

    @Test
    void distinguishesStartupCrashFromCrashAfterActionsBegin() throws Exception {
        Path reports = Files.createDirectories(job.resolve("game/crash-reports"));
        Path report = Files.writeString(reports.resolve("crash-2026-11-26-client.txt"), "Minecraft Crash Report");
        String binary = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Process exited = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", binary).toString(),
            "-version").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertEquals(0, exited.waitFor());
        var startup = assertThrows(SandboxProfile.WorkerCrash.class, () -> SandboxProfile.awaitWorker(exited, job));
        assertEquals(report, startup.report);
        assertTrue(startup.beforeScript);
        Files.createFile(job.resolve("script-started"));
        var inScript = assertThrows(SandboxProfile.WorkerCrash.class, () -> SandboxProfile.awaitWorker(exited, job));
        assertFalse(inScript.beforeScript);
        Files.delete(report);
        Files.writeString(reports.resolve("explanation.txt"), "Not a crash report");
        assertDoesNotThrow(() -> SandboxProfile.awaitWorker(exited, job));
    }
}
