package net.createmod.ponder.foundation.ui.sandbox;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import javax.imageio.ImageIO;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.SnapshotProvider;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.Snapshot;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.ResettableSnapshotProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RecordedGuiPlaybackTest {
    @TempDir Path root;

    @Test
    void activeRecordingWaitsForPublicationAndSeekingToHiddenFramesDoesNotBlockPlayback() throws Exception {
        var raw = new com.google.gson.JsonObject();
        raw.addProperty("test", "cold-cache");
        String id = SandboxRecordings.id(raw);
        SnapshotProvider player = SandboxRecordings.onView(raw, 20);
        assertFalse(player.waitingForRecording());
        assertNull(player.provide(19));
        assertNotNull(player.provide(20));
        assertTrue(player.waitingForRecording());
        RecordedGuiPlayback.publish(id, generation("ready", 14, 24));
        assertFalse(player.waitingForRecording());
        assertNull(player.provide(26));
        assertEquals(14, player.provide(20).displayWidth);
    }

    @Test
    void requestsOnViewAfterTheOverlayStartsAndKeepsRecordedHiddenFramesHidden() throws Exception {
        String id = "on-view";
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        SnapshotProvider player = RecordedGuiPlayback.provider(id, 12, calls::incrementAndGet);
        assertNull(player.provide(Float.NaN));
        assertNull(player.provide(11));
        assertEquals(0, calls.get());
        player.provide(12);
        assertEquals(1, calls.get());
        RecordedGuiPlayback.publish(id, generation("view", 15, 21));
        assertEquals(15, player.provide(12).displayWidth);
        assertNull(player.provide(18));
        assertEquals(1, calls.get());
    }

    @Test
    void pendingPreviewReleasesItsScreenOnPublicationAndRetainsRecordingIdentity() throws Exception {
        var raw = new com.google.gson.JsonObject();
        raw.addProperty("test", "native-preview");
        String id = SandboxRecordings.id(raw);
        raw.add("preview", new com.google.gson.JsonObject());
        assertEquals(id, SandboxRecordings.id(raw));
        assertTrue(raw.has("preview"));

        var preview = new RecordedGuiPlayback.PendingPreview() {
            private int resets;

            @Override
            public Snapshot previewSnapshot(float tick, String statusKey) {
                assertEquals("ponder.sandbox.waiting", statusKey);
                return Snapshot.liveRenderer(192, 128, true, (x, y, width, height, currentTick, fade) -> { });
            }

            @Override
            public void reset() { resets++; }
        };
        SnapshotProvider player = SandboxRecordings.onView(raw, 20, preview);
        assertNull(player.provide(19));
        assertEquals(192, player.provide(25).displayWidth);
        assertTrue(player.waitingForRecording());
        RecordedGuiPlayback.publish(id, generation("preview-ready", 14, 24));
        assertFalse(player.waitingForRecording());
        assertEquals(24, player.provide(25).displayWidth);
        assertEquals(1, preview.resets);
        assertNull(player.provide(26));
        assertEquals(1, preview.resets);
    }

    @Test
    void publishedRecordingKeepsColdPreviewUntilFirstVisibleFrameOnly() throws Exception {
        var raw = new com.google.gson.JsonObject();
        raw.addProperty("test", "leading-hidden-frames");
        String id = SandboxRecordings.id(raw);
        var preview = new RecordedGuiPlayback.PendingPreview() {
            private int resets;
            private String lastStatus;

            @Override
            public Snapshot previewSnapshot(float tick, String statusKey) {
                lastStatus = statusKey;
                return Snapshot.liveRenderer(192, 128, true, (x, y, width, height, currentTick, fade) -> { });
            }

            @Override
            public void reset() { resets++; }
        };
        SnapshotProvider cold = SandboxRecordings.onView(raw, 20, preview);
        SnapshotProvider seeking = SandboxRecordings.onView(raw, 20, preview);
        assertEquals(192, cold.provide(21).displayWidth);
        assertEquals(192, seeking.provide(21).displayWidth);
        assertTrue(cold.waitingForRecording());

        Path generation = generation("leading-hidden", 14, 24);
        Files.writeString(generation.resolve("frames.json"),
            """
            {"duration":10,"frames":[{"tick":0,"file":"frames/a.png","hidden":true},
             {"tick":5,"file":"frames/b.png"},{"tick":6,"file":"frames/a.png","hidden":true},
             {"tick":8,"file":"frames/b.png"}]}
            """);
        RecordedGuiPlayback.publish(id, generation);
        assertFalse(cold.waitingForRecording());
        assertEquals(192, cold.provide(24).displayWidth);
        assertEquals("ponder.sandbox.ready", preview.lastStatus);
        assertEquals(0, preview.resets);
        assertEquals(24, cold.provide(25).displayWidth);
        assertNull(cold.provide(26));
        assertNull(cold.provide(21));
        assertEquals(1, preview.resets);

        assertNull(seeking.provide(26));
        assertNull(seeking.provide(21));
        assertEquals(2, preview.resets);
        assertNull(SandboxRecordings.onView(raw, 20, preview).provide(21));
        assertEquals(2, preview.resets);
    }

    @Test
    void sparseFramesSeekInBothDirectionsAndHoldTheLastFrame() throws Exception {
        Path generation = generation("first", 11, 22);
        RecordedGuiPlayback.publish("seeking", generation);
        SnapshotProvider player = RecordedGuiPlayback.provider("seeking", 10);
        assertNull(player.provide(9));
        assertNull(player.provide(Float.NaN));
        assertEquals(11, player.provide(10).displayWidth);
        assertEquals(22, player.provide(15).displayWidth);
        assertEquals(11, player.provide(14.9F).displayWidth);
        assertNull(player.provide(17));
        assertEquals(22, player.provide(500).displayWidth);
    }

    @Test
    void activeViewPinsItsGenerationAcrossPublicationRemovalAndSeeking() throws Exception {
        String id = "generation-test";
        ResettableSnapshotProvider player = (ResettableSnapshotProvider) RecordedGuiPlayback.provider(id, 0);
        assertNull(player.provide(0));
        RecordedGuiPlayback.publish(id, generation("first", 11, 22));
        Path corrupt = generation("bad", 32, 64);
        Files.write(corrupt.resolve("frames/b.png"), new byte[] {0});
        assertThrows(IOException.class, () -> RecordedGuiPlayback.publish(id, corrupt));
        assertEquals(11, player.provide(0).displayWidth);
        RecordedGuiPlayback.publish(id, generation("second", 33, 44));
        assertEquals(22, player.provide(5).displayWidth);
        assertEquals(11, player.provide(0).displayWidth);
        assertEquals(44, RecordedGuiPlayback.provider(id, 0).provide(5).displayWidth);
        player.reset();
        assertEquals(33, player.provide(0).displayWidth);
        RecordedGuiPlayback.retain(Set.of("seeking"));
        assertEquals(44, player.provide(5).displayWidth);
        player.reset();
        assertNull(player.provide(0));
    }

    private Path generation(String name, int firstWidth, int secondWidth) throws IOException {
        Path dir = Files.createDirectory(root.resolve(name));
        Path frames = Files.createDirectory(dir.resolve("frames"));
        ImageIO.write(new BufferedImage(firstWidth, 8, BufferedImage.TYPE_INT_ARGB), "png", frames.resolve("a.png").toFile());
        ImageIO.write(new BufferedImage(secondWidth, 8, BufferedImage.TYPE_INT_ARGB), "png", frames.resolve("b.png").toFile());
        Files.writeString(dir.resolve("frames.json"),
            """
            {"duration":10,"frames":[{"tick":0,"file":"frames/a.png"},{"tick":5,"file":"frames/b.png"},
             {"tick":6,"file":"frames/a.png","hidden":true},{"tick":8,"file":"frames/b.png"}]}
            """);
        return dir;
    }
}
