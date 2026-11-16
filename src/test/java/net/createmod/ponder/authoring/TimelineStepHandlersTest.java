package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.stream.Collectors;
import com.google.gson.JsonObject;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderScene.ActorEventType;
import net.createmod.ponder.foundation.PonderScene.ActorKind;
import net.createmod.ponder.foundation.PreviewActorTimeline;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TimelineStepHandlersTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void soundCuesUseRegisteredIdsCategoriesAndExplicitModernMappings() {
        JsonObject sound = sound(); sound.addProperty("source", "block"); sound.addProperty("soundVolume", 0.4); sound.addProperty("pitch", 1.5);
        PonderScene scene = compile(sound, idle(3), sound());
        assertEquals(2, scene.getSoundEvents().size());
        assertEquals(SoundCategory.BLOCKS, scene.getSoundEvents().get(0).getCategory());
        assertEquals(0, scene.getSoundEvents().get(0).getTick()); assertEquals(3, scene.getSoundEvents().get(1).getTick());
        JsonObject modern = sound(); modern.addProperty("sound", "minecraft:block.note_block.harp");
        assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(modern), null).hasErrors());
        JsonObject root = AuthoringModelTest.document(modern).toJson(), mappings = new JsonObject();
        mappings.addProperty("minecraft:block.note_block.harp", "minecraft:block.note.harp"); root.add("idMappings", mappings);
        assertEquals("minecraft:block.note.harp", new AuthoringCompiler().compile(AuthoringDocument.fromJson(root), null).get(0)
            .getSoundEvents().get(0).getSoundId().toString());
    }

    @Test void badSoundCategoryVolumePitchAndMissingSoundFailClosed() {
        for (String field : new String[] {"source", "soundVolume", "pitch", "sound"}) {
            JsonObject step = sound();
            if ("soundVolume".equals(field)) step.addProperty(field, 5);
            else if ("pitch".equals(field)) step.addProperty(field, 0);
            else step.addProperty(field, "missing:invalid");
            assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(step), null).hasErrors(), field);
        }
    }

    @Test void entityPatchesTargetCurrentPositionKindAndCapturedLinks() {
        JsonObject player = player(0, "worker"), other = player(6, "other");
        JsonObject move = patch(false, "{}"); move.addProperty("linkId", "worker"); move.add("offset", AuthoringModelTest.pos(2, 0, 0)); move.addProperty("duration", 4);
        JsonObject selected = patch(false, "{CustomName:\"Target\",Rotation:[90f,20f]}");
        selected.remove("fullScene"); selected.add("blockPos", AuthoringModelTest.pos(2, 0, 0));
        PonderScene scene = compile(player, other, move, idle(4), selected);
        List<PreviewActorTimeline.State> states = PreviewActorTimeline.evaluate(scene.getActorEvents(), 4);
        assertEquals(new Vec3d(2, 0, 0), states.get(0).position); assertEquals("Target", states.get(0).displayName);
        assertNull(states.get(1).displayName); assertEquals(90, states.get(0).yaw, 0.001);
        assertEquals(0.5, PreviewActorTimeline.evaluate(scene.getActorEvents(), 0).get(0).position.x, 0.0001);
    }

    @Test void fullSceneOverridesSelectionButLinkedSelectionStillFilters() {
        JsonObject all = patch(false, "{Invisible:1b}"); all.add("blockPos", AuthoringModelTest.pos(99, 0, 0));
        PonderScene full = compile(player(0, "one"), all);
        assertFalse(PreviewActorTimeline.evaluate(full.getActorEvents(), 0).get(0).visible);
        all.addProperty("linkId", "one");
        PonderScene linked = compile(player(0, "one"), all);
        assertTrue(PreviewActorTimeline.evaluate(linked.getActorEvents(), 0).get(0).visible);
    }

    @Test void itemPatchesDeepMergeCustomTagsAndSupportItemWrapper() {
        JsonObject item = item(); item.addProperty("nbt", "{display:{Name:\"Old\"},mmce:{keep:1,change:2}}");
        JsonObject merge = patch(true, "{display:{Name:\"New\"},mmce:{change:3}}");
        JsonObject wrapper = patch(true, "{Item:{id:\"minecraft:diamond\",Count:3b,Damage:1s,tag:{mmce:{extra:4}}}}");
        PonderScene scene = compile(item, idle(2), merge, idle(2), wrapper);
        ItemStack current = PreviewActorTimeline.evaluate(scene.getActorEvents(), 4).get(0).getItemStack();
        assertEquals(Items.DIAMOND, current.getItem()); assertEquals(3, current.getCount()); assertEquals(1, current.getMetadata());
        assertEquals("New", current.getDisplayName());
        assertEquals(1, current.getTagCompound().getCompoundTag("mmce").getInteger("keep"));
        assertEquals(3, current.getTagCompound().getCompoundTag("mmce").getInteger("change"));
        assertEquals(4, current.getTagCompound().getCompoundTag("mmce").getInteger("extra"));
        assertEquals("Old", PreviewActorTimeline.evaluate(scene.getActorEvents(), 0).get(0).getItemStack().getDisplayName());
    }

    @Test void itemFilterUsesTheCurrentPatchedItemNotItsSpawnItem() {
        JsonObject first = patch(true, "{Item:{id:\"minecraft:diamond\"}}"); first.addProperty("item", "minecraft:compass");
        JsonObject second = patch(true, "{display:{Name:\"Selected diamond\"}}"); second.addProperty("item", "minecraft:diamond");
        ItemStack current = PreviewActorTimeline.evaluate(compile(item(), first, second).getActorEvents(), 0).get(0).getItemStack();
        assertEquals("Selected diamond", current.getDisplayName());
    }

    @Test void itemMotionIsFinitePreviewMovementWithoutEntityConstruction() {
        JsonObject item = item(); item.addProperty("nbt", "{Motion:[0.1d,0d,0d]}"); item.addProperty("duration", 10);
        PonderScene scene = compile(item);
        assertEquals(1, scene.getActorEvents().stream().filter(event -> event.getType() == ActorEventType.MOVE).count());
        assertEquals(1, PreviewActorTimeline.evaluate(scene.getActorEvents(), 10).get(0).position.x, 0.0001);
        assertFalse(scene.getOperationLog().stream().anyMatch(line -> line.startsWith("world.createEntity(")));
    }

    @Test void initialEntityNbtAndPitchBecomeAbsolutePreviewState() {
        JsonObject player = player(0, "worker"); player.addProperty("nbt", "{Pos:[4,2,1],Rotation:[90f,20f],Invisible:1b}");
        PreviewActorTimeline.State state = PreviewActorTimeline.evaluate(compile(player).getActorEvents(), 0).get(0);
        assertEquals(new Vec3d(4, 2, 1), state.position); assertFalse(state.visible); assertEquals(90, state.yaw, 0.001);
        JsonObject cart = AuthoringModelTest.step("create_entity"); cart.addProperty("entity", "minecraft:minecart"); cart.addProperty("pitch", 25); cart.addProperty("yaw", 40);
        state = PreviewActorTimeline.evaluate(compile(cart).getActorEvents(), 0).get(0);
        assertEquals(40, state.yaw, 0.001); assertEquals(25, state.rotation.x, 0.001);
    }

    @Test void identityLifecycleUnknownEntityTagsAndDangerousItemRenderDataAreRejected() {
        for (String nbt : new String[] {"{UUIDMost:1L}", "{Age:1s}", "{Health:5f}", "{NoGravity:1b}", "{Unknown:1}"})
            assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(player(0, "p"), patch(false, nbt)), null).hasErrors(), nbt);
        for (String key : new String[] {"SkullOwner", "BlockEntityTag", "ForgeCaps"}) {
            JsonObject item = item(); item.addProperty("nbt", "{" + key + ":{x:1}}");
            assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(item), null).hasErrors(), key);
        }
    }

    @Test void badVectorShapeCoordinatesRotationAndItemWrapperAreRejected() {
        for (String nbt : new String[] {"{Pos:[1d,2d]}", "{Pos:[30000001d,0d,0d]}", "{Rotation:[0f,91f]}",
            "{Invisible:2b}", "{Item:{Count:65}}", "{Item:{id:\"missing:invalid\"}}", "{Item:{UUID:1}}"}) {
            boolean item = nbt.contains("Item");
            assertTrue(new AuthoringCompiler().validate(AuthoringModelTest.document(item ? item() : player(0, "p"), patch(item, nbt)), null).hasErrors(), nbt);
        }
    }

    @Test void overlapAndUnknownLinksFailRatherThanGuessingOrDroppingTargets() {
        JsonObject move = patch(false, "{}"); move.add("offset", AuthoringModelTest.pos(5, 0, 0)); move.addProperty("duration", 10);
        JsonObject absolute = patch(false, "{Pos:[2d,0d,0d]}");
        assertThrows(IllegalArgumentException.class, () -> compile(player(0, "p"), move, absolute));
        absolute.addProperty("linkId", "missing");
        assertThrows(IllegalArgumentException.class, () -> compile(player(0, "p"), absolute));
    }

    @Test void clearAlsoIncludesNativeActorsButItemOnlyLeavesPlayers() {
        JsonObject nativePlayer = AuthoringModelTest.step("actor_action"); nativePlayer.addProperty("action", "SPAWN");
        nativePlayer.addProperty("kind", "PLAYER"); nativePlayer.addProperty("actorId", 7); nativePlayer.add("pos", AuthoringModelTest.pos(0, 0, 0));
        PonderScene all = compile(nativePlayer, item(), AuthoringModelTest.step("clear_entities"));
        assertEquals(2, all.getActorEvents().stream().filter(event -> event.getType() == ActorEventType.HIDE).count());
        PonderScene items = compile(nativePlayer, item(), AuthoringModelTest.step("clear_item_entities"));
        assertEquals(1, items.getActorEvents().stream().filter(event -> event.getType() == ActorEventType.HIDE).count());
        assertEquals(ActorKind.ITEM, items.getActorEvents().stream().filter(event -> event.getType() == ActorEventType.HIDE).findFirst().get().getActorKind());
    }

    @Test void eventNbtAndStackSnapshotsAreDefensiveAndSeekingIsPure() throws Exception {
        PonderScene scene = compile(item(), idle(3), patch(true, "{Item:{Count:4b,tag:{display:{Name:\"Four\"}}}}"));
        PonderScene.ActorEvent patch = scene.getActorEvents().stream().filter(event -> event.getType() == ActorEventType.NBT_PATCH).findFirst().get();
        patch.getActorNbt().removeTag("Item"); patch.getItemStack().setCount(99);
        assertTrue(patch.getActorNbt().hasKey("Item")); assertEquals(4, patch.getItemStack().getCount());
        List<String> before = scene.getActorEvents().stream().map(event -> event.getActorNbt().toString()).collect(Collectors.toList());
        for (int tick : new int[] {4, 0, 3, 1, 4}) PreviewActorTimeline.evaluate(scene.getActorEvents(), tick);
        assertEquals(before, scene.getActorEvents().stream().map(event -> event.getActorNbt().toString()).collect(Collectors.toList()));
    }

    @Test void forkRoundTripRetainsSoundActorPatchesAndWalkingFlagsIncludingNativeIds() throws Exception {
        JsonObject nativePlayer = AuthoringModelTest.step("actor_action"); nativePlayer.addProperty("action", "SPAWN");
        nativePlayer.addProperty("kind", "PLAYER"); nativePlayer.addProperty("actorId", 7); nativePlayer.add("pos", AuthoringModelTest.pos(0, 0, 0));
        PonderScene original = compile(nativePlayer, item(), idle(2), patch(false, "{Pos:[3d,0d,0d],CustomName:\"Worker\"}"),
            patch(true, "{display:{Name:\"Preview\"}}"), sound());
        AuthoringDocument fork = AuthoringSceneForker.fork(original, "local:actor_sound_fork", (id, asset) -> fail());
        PonderScene copy = new AuthoringCompiler().compile(fork, null).get(0);
        for (int tick : new int[] {0, 2, 10, 1, 2}) {
            List<PreviewActorTimeline.State> expected = PreviewActorTimeline.evaluate(original.getActorEvents(), tick);
            List<PreviewActorTimeline.State> actual = PreviewActorTimeline.evaluate(copy.getActorEvents(), tick);
            assertEquals(expected.size(), actual.size());
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).position, actual.get(i).position); assertEquals(expected.get(i).getNbt(), actual.get(i).getNbt());
                assertTrue(ItemStack.areItemStacksEqual(expected.get(i).getItemStack(), actual.get(i).getItemStack()));
            }
        }
        assertEquals(original.getSoundEvents().size(), copy.getSoundEvents().size());
        assertEquals(original.getSoundEvents().get(0).getTick(), copy.getSoundEvents().get(0).getTick());
        assertEquals(original.getSoundEvents().get(0).getSoundId(), copy.getSoundEvents().get(0).getSoundId());
    }

    @Test void boundedSoundCueCountsFailBeforeAddingAnotherCue() {
        PonderScene scene = compile(sound());
        for (int i = 1; i < 256; i++) { scene.addIdleTicks(1); scene.recordSound(scene.getSoundEvents().get(0).getSoundId(), SoundCategory.MASTER, 1, 1); }
        assertThrows(IllegalArgumentException.class, () -> scene.recordSound(scene.getSoundEvents().get(0).getSoundId(), SoundCategory.MASTER, 1, 1));
        assertEquals(256, scene.getSoundEvents().size());
        net.minecraft.util.ResourceLocation id=new net.minecraft.util.ResourceLocation("test:cue_limit");
        PonderScene simultaneous=new PonderScene(new net.createmod.ponder.foundation.PonderStoryBoardEntry(null,"test",id,id),
            new net.createmod.ponder.foundation.registration.PonderLocalization());
        for(int i=0;i<16;i++) simultaneous.recordSound(scene.getSoundEvents().get(0).getSoundId(),SoundCategory.MASTER,1,1);
        assertThrows(IllegalArgumentException.class, () -> simultaneous.recordSound(scene.getSoundEvents().get(0).getSoundId(),SoundCategory.MASTER,1,1));
        assertEquals(16,simultaneous.getSoundEvents().size());
    }

    private static PonderScene compile(JsonObject... steps) { return new AuthoringCompiler().compile(AuthoringModelTest.document(steps), null).get(0); }
    private static JsonObject player(int x, String link) { JsonObject s=AuthoringModelTest.step("create_entity"); s.addProperty("entity","minecraft:player"); s.add("pos",AuthoringModelTest.pos(x,0,0)); s.addProperty("linkId",link); return s; }
    private static JsonObject item() { JsonObject s=AuthoringModelTest.step("create_item_entity"); s.addProperty("item","minecraft:compass"); s.add("pos",AuthoringModelTest.pos(0,0,0)); return s; }
    private static JsonObject patch(boolean item, String nbt) { JsonObject s=AuthoringModelTest.step(item?"modify_item_entities_nbt":"modify_entities_nbt"); s.addProperty("fullScene",true); s.addProperty("nbt",nbt); return s; }
    private static JsonObject idle(int ticks) { JsonObject s=AuthoringModelTest.step("idle"); s.addProperty("duration",ticks); return s; }
    private static JsonObject sound() { JsonObject s=AuthoringModelTest.step("play_sound"); s.addProperty("sound","minecraft:block.note.harp"); return s; }
}
