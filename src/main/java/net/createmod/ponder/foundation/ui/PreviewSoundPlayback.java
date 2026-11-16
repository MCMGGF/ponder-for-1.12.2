package net.createmod.ponder.foundation.ui;

import java.util.ArrayList;
import java.util.List;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.SoundEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;

/** Session-owned local sound handles. It never stops unrelated Minecraft sounds. */
final class PreviewSoundPlayback {
    interface Access { Object play(SoundEvent event); boolean playing(Object handle); void stop(Object handle); }
    private final Access access;
    private final PreviewSoundTimeline timeline = new PreviewSoundTimeline();
    private final List<Object> handles = new ArrayList<Object>();
    private boolean unavailable;
    PreviewSoundPlayback() { this(new NativeAccess()); }
    PreviewSoundPlayback(Access access) { this.access = access; }

    void reset(PonderScene scene) { pause(); timeline.reset(scene); }
    void seek(PonderScene scene, int tick) { pause(); timeline.seek(scene, tick); }
    void advance(PonderScene scene, int before, int after) {
        if (scene == null) { close(); return; }
        List<SoundEvent> crossed = timeline.advance(scene, scene.getSoundEvents(), before, after);
        if (unavailable) return;
        try {
            for (int i = handles.size() - 1; i >= 0; i--) if (!access.playing(handles.get(i))) handles.remove(i);
            for (SoundEvent event : crossed) {
                if (event.getVolume() <= 0) continue;
                if (handles.size() >= 16) access.stop(handles.remove(0));
                Object handle = access.play(event); if (handle != null) handles.add(handle);
            }
        } catch (RuntimeException | LinkageError invalid) { failed(invalid); pause(); }
    }
    void pause() {
        try { for (Object handle : handles) {
            try { access.stop(handle); } catch (RuntimeException | LinkageError invalid) { failed(invalid); }
        } } finally { handles.clear(); }
    }
    void close() { pause(); timeline.reset(null); }
    private void failed(Throwable invalid) {
        if (!unavailable) net.createmod.ponder.Ponder.LOGGER.warn("Preview audio disabled for this session; owned resources are being released", invalid);
        unavailable = true;
    }

    private static final class NativeAccess implements Access {
        @Override public Object play(SoundEvent event) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.player == null || mc.world == null) return null;
            ISound sound = new PositionedSoundRecord(event.getSoundId(), event.getCategory(), event.getVolume(), event.getPitch(),
                false, 0, ISound.AttenuationType.NONE, (float)mc.player.posX, (float)mc.player.posY, (float)mc.player.posZ);
            mc.getSoundHandler().playSound(sound); return sound;
        }
        @Override public boolean playing(Object handle) { return Minecraft.getMinecraft().getSoundHandler().isSoundPlaying((ISound)handle); }
        @Override public void stop(Object handle) { Minecraft.getMinecraft().getSoundHandler().stopSound((ISound)handle); }
    }
}
