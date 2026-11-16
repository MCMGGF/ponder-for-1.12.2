package net.createmod.ponder.foundation.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.SoundEvent;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import org.junit.jupiter.api.Test;

class PreviewSoundPlaybackTest {
    @Test void onlyActualContiguousForwardTicksCrossCuesIncludingInitialZeroOnce() {
        PreviewSoundTimeline timeline = new PreviewSoundTimeline(); Object scene = new Object();
        List<SoundEvent> events = Arrays.asList(cue(0), cue(1), cue(4));
        assertEquals(2, timeline.advance(scene, events, 0, 1).size());
        assertTrue(timeline.advance(scene, events, 0, 1).isEmpty());
        assertTrue(timeline.advance(scene, events, 1, 2).isEmpty());
        assertTrue(timeline.advance(scene, events, 2, 4).isEmpty());
        assertTrue(timeline.advance(scene, events, 4, 3).isEmpty());
        timeline.seek(scene, 4); assertTrue(timeline.advance(scene, events, 4, 5).isEmpty());
    }

    @Test void manualSeekNeverPlaysTheDestinationAndExplicitReplayCanPlayZeroAgain() {
        PreviewSoundTimeline timeline = new PreviewSoundTimeline(); Object scene = new Object();
        List<SoundEvent> events = Arrays.asList(cue(0), cue(1), cue(4));
        timeline.seek(scene, 0); assertEquals(1, timeline.advance(scene, events, 0, 1).size());
        timeline.seek(scene, 4); assertTrue(timeline.advance(scene, events, 4, 5).isEmpty());
        timeline.reset(scene); assertEquals(2, timeline.advance(scene, events, 0, 1).size());
    }

    @Test void sceneSwitchDoesNotReplayAlreadyElapsedCues() {
        PreviewSoundTimeline timeline = new PreviewSoundTimeline();
        List<SoundEvent> events = Arrays.asList(cue(0), cue(8));
        assertTrue(timeline.advance(new Object(), events, 5, 6).isEmpty());
        assertEquals(1, timeline.advance(new Object(), events, 7, 8).size());
        assertTrue(timeline.advance(null, events, 0, 1).isEmpty());
    }

    @Test void pauseSeekAndCloseStopOnlySessionOwnedHandles() {
        FakeAudio access = new FakeAudio(); PreviewSoundPlayback playback = new PreviewSoundPlayback(access);
        PonderScene scene = scene();
        playback.advance(scene, 0, 1); assertEquals(1, access.played.size());
        Object first = access.played.get(0); playback.pause(); assertEquals(Arrays.asList(first), access.stopped);
        playback.advance(scene, 1, 2); assertEquals(1, access.played.size());
        playback.seek(scene, 0); playback.advance(scene, 0, 1); assertEquals(1, access.played.size());
        playback.reset(scene); playback.advance(scene, 0, 1); assertEquals(2, access.played.size());
        playback.close(); assertEquals(2, access.stopped.size());
        assertFalse(access.stopped.contains(access.unrelated));
    }

    @Test void finishedHandlesAreReleasedAndTheMaximumActiveCountIsBounded() {
        FakeAudio access = new FakeAudio(); PreviewSoundPlayback playback = new PreviewSoundPlayback(access);
        PonderScene scene = scene();
        for (int i = 0; i < 15; i++) scene.recordSound(new ResourceLocation("minecraft:block.note.harp"), SoundCategory.MASTER, 1, 1);
        playback.advance(scene, 0, 1);
        assertEquals(16, access.played.size());
        assertEquals(0, access.stopped.size());
        access.finished = true; playback.advance(scene, 1, 2); playback.close();
        assertEquals(0, access.stopped.size());
    }

    @Test void soundEventRejectsNonFiniteOrUnboundedValues() {
        ResourceLocation id = new ResourceLocation("minecraft:block.note.harp");
        assertThrows(IllegalArgumentException.class, () -> new SoundEvent(0, id, SoundCategory.MASTER, Float.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> new SoundEvent(0, id, SoundCategory.MASTER, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SoundEvent(-1, id, SoundCategory.MASTER, 1, 1));
    }

    private static SoundEvent cue(int tick) { return new SoundEvent(tick,new ResourceLocation("minecraft:block.note.harp"),SoundCategory.MASTER,1,1); }
    private static PonderScene scene() {
        ResourceLocation id=new ResourceLocation("test:sound");
        PonderScene scene=new PonderScene(new PonderStoryBoardEntry(null,"test",id,id),new PonderLocalization());
        scene.recordSound(new ResourceLocation("minecraft:block.note.harp"),SoundCategory.MASTER,1,1);
        return scene;
    }
    private static final class FakeAudio implements PreviewSoundPlayback.Access {
        final List<Object> played=new ArrayList<Object>(), stopped=new ArrayList<Object>();
        final Object unrelated=new Object(); boolean finished;
        @Override public Object play(SoundEvent event) { Object handle=new Object(); played.add(handle); return handle; }
        @Override public boolean playing(Object handle) { return !finished; }
        @Override public void stop(Object handle) { stopped.add(handle); }
    }
}
