package net.createmod.ponder.authoring;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderSceneBuilder;
import net.createmod.ponder.foundation.PonderStoryBoardEntry;
import net.createmod.ponder.foundation.PreviewActorTimeline;
import net.createmod.ponder.foundation.content.VanillaTutorialRecipes.RecipePreview;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry;
import net.createmod.ponder.foundation.ui.PonderGuiSnapshotRegistry.Snapshot;
import net.createmod.ponder.foundation.ui.VanillaLogisticsSnapshot;
import net.createmod.ponder.foundation.ui.VanillaRecipeSnapshot;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeOverlayForkTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void nativeTexturesHighlightsChildCaptionsLinesAndValueBoxesRoundTripTheirTargetsAndAppearance() throws Exception {
        PonderScene source = source((scene, util) -> {
            PonderScene target = scene.getScene();
            target.createOverlayEvent(47).guiTexture(texture("crafting_table"), 7, 11, 120, 60, 256, 256, 150, 75, -12, 9, false)
                .overlayId("parent").pointAt(new Vec3d(1.5, 2.5, 3.5)).targetPositions(Arrays.asList(BlockPos.ORIGIN, new BlockPos(2, 1, 0)))
                .scaleToParent(true).stretchTexture(true, 3).captionPosition(30, 20).captionOffset(2, 4).connectorVisible(false);
            target.createOverlayEvent(29).guiHighlight("parent", 18, 31, 16, 16).overlayId("highlight").palette(PonderPalette.RED).offset(3, -2);
            target.createOverlayEvent(31).text("\u5b50\u8bf4\u660e").parentOverlayId("parent").overlayId("caption")
                .guiOrigin(7, 9).captionPosition(-1, 15).captionOffset(2, 4).palette(PonderPalette.GREEN);
            scene.idle(5);
            target.createOverlayEvent(23).sceneLine(new Vec3d(1, 2, 3), new Vec3d(3, 2, 1), true).palette(PonderPalette.BLUE).overlayId("line");
            target.createOverlayEvent(22).valueBox(new Vec3d(2.5, 1.5, 0.5), new Vec3d(0.2, 0.3, 0.4)).text("value").overlayId("box");
            target.createOverlayEvent(21).sceneOutline(new AxisAlignedBB(0.25, 0.5, 0.75, 3.25, 2.5, 1.75)).outlineChasing(true).overlayId("aabb");
        });
        PonderScene copy = compile(fork(source));
        assertEquals(source.getOverlayEvents().size(), copy.getOverlayEvents().size());
        for (int index = 0; index < source.getOverlayEvents().size(); index++)
            assertOverlay(source.getOverlayEvents().get(index), copy.getOverlayEvents().get(index));
    }

    @Test void fourNativeMenuTexturesAreDeclarativeWithoutOpeningAnyContainer() throws Exception {
        PonderScene source = source((scene, util) -> {
            for (String menu : new String[] {"crafting_table", "furnace", "generic_54", "hopper"}) {
                scene.getScene().createOverlayEvent(30).guiTexture(texture(menu), 0, 0, 176, 76, 256, 256, 176, 76, 0, 0, false);
                scene.idle(2);
            }
        });
        PonderScene copy = compile(fork(source)); assertEquals(4, copy.getOverlayEvents().size());
        for (int index = 0; index < 4; index++) assertOverlay(source.getOverlayEvents().get(index), copy.getOverlayEvents().get(index));
    }

    @Test void knownRecipeSnapshotsFreezeSlotsAndFuelIndependentlyOfLaterGlobalIdReplacement() throws Exception {
        ItemStack input = new ItemStack(Blocks.IRON_ORE); NBTTagCompound tag = new NBTTagCompound(); tag.setString("label", "before"); input.setTagCompound(tag);
        RecipePreview recipe = RecipePreview.fromSnapshot(Collections.singletonList(input), new ItemStack(Items.COAL), new ItemStack(Items.IRON_INGOT), null, 1600);
        ResourceLocation id = VanillaRecipeSnapshot.register(recipe, true, true);
        PonderScene source = source((scene, util) -> scene.getScene().createOverlayEvent(60).guiSnapshot(id).overlayId("recipe").pointAt(new Vec3d(1.5, 1.5, 1.5)));
        JsonObject expected = NativeOverlayStepHandlers.snapshotDeclaration(source.getOverlayEvents().get(0));
        AuthoringDocument document = fork(source);
        input.getTagCompound().setString("label", "changed");
        VanillaRecipeSnapshot.register(RecipePreview.fromSnapshot(Collections.emptyList(), ItemStack.EMPTY, ItemStack.EMPTY, "missing", 0), true, true);
        PonderScene copy = compile(document);
        assertEquals(expected, NativeOverlayStepHandlers.snapshotDeclaration(copy.getOverlayEvents().get(0)));
        Snapshot restored = PonderGuiSnapshotRegistry.declaredSnapshotFor(copy.getOverlayEvents().get(0));
        VanillaRecipeSnapshot renderer = (VanillaRecipeSnapshot) restored.renderer;
        ItemStack external = renderer.getRecipePreview().getInputs().get(0); external.getTagCompound().setString("label", "outside");
        assertEquals("before", renderer.getRecipePreview().getInputs().get(0).getTagCompound().getString("label"));
        assertTrue(renderer.getRecipePreview().hasRemainingFuel());
        assertEquals(expected, NativeOverlayStepHandlers.snapshotDeclaration(compile(document).getOverlayEvents().get(0)));
    }

    @Test void knownLogisticsStagesRoundTripWithoutReusingTheSourceRenderer() throws Exception {
        for (VanillaLogisticsSnapshot.Stage stage : VanillaLogisticsSnapshot.Stage.values()) {
            ResourceLocation id = VanillaLogisticsSnapshot.register(stage);
            PonderScene source = source((scene, util) -> scene.getScene().createOverlayEvent(30).guiSnapshot(id));
            PonderScene copy = compile(fork(source));
            Snapshot before = PonderGuiSnapshotRegistry.declaredSnapshotFor(source.getOverlayEvents().get(0));
            Snapshot after = PonderGuiSnapshotRegistry.declaredSnapshotFor(copy.getOverlayEvents().get(0));
            assertNotSame(before.renderer, after.renderer);
            assertEquals(stage, ((VanillaLogisticsSnapshot) after.renderer).getStage());
        }
    }

    @Test void unknownProvidersAndEvenStaticallyWrappedUnknownRenderersRemainUnsupportedAndAreNeverInvoked() throws Exception {
        AtomicInteger calls = new AtomicInteger(); ResourceLocation id = new ResourceLocation("fork_test", "unknown");
        PonderGuiSnapshotRegistry.registerProvider(id, tick -> { calls.incrementAndGet(); return Snapshot.fullTexture(texture("furnace"), 176, 76); });
        PonderScene provider = source((scene, util) -> scene.getScene().createOverlayEvent(20).guiSnapshot(id));
        assertUnsupported(fork(provider)); assertEquals(0, calls.get());
        PonderGuiSnapshotRegistry.register(id, Snapshot.liveRenderer(176, 76, false, (x, y, w, h, tick, fade) -> calls.incrementAndGet()));
        assertUnsupported(fork(provider)); assertEquals(0, calls.get());
        PonderGuiSnapshotRegistry.register(id, Snapshot.fullTexture(texture("furnace"), 176, 76));
    }

    @Test void actualProviderIdentityPreventsStaleStaticMetadataAfterOverrideAndDynamicRemoval() {
        ResourceLocation id = new ResourceLocation("fork_test", "definition");
        PonderGuiSnapshotRegistry.register(id, Snapshot.fullTexture(texture("furnace"), 176, 76));
        PonderScene source = source((scene, util) -> scene.getScene().createOverlayEvent(20).guiSnapshot(id));
        PonderScene.OverlayEvent event = source.getOverlayEvents().get(0); assertNotNull(PonderGuiSnapshotRegistry.declaredSnapshotFor(event));
        AtomicInteger calls = new AtomicInteger(); PonderGuiSnapshotRegistry.registerProvider(id, tick -> { calls.incrementAndGet(); return null; });
        assertNull(PonderGuiSnapshotRegistry.declaredSnapshotFor(event)); assertEquals(0, calls.get());
        ResourceLocation dynamic = new ResourceLocation("fork_test", "dynamic_definition");
        PonderGuiSnapshotRegistry.SnapshotProvider declared = PonderGuiSnapshotRegistry.declaredProvider(Snapshot.fullTexture(texture("hopper"), 176, 76));
        PonderGuiSnapshotRegistry.registerDynamicProvider(dynamic, declared);
        PonderScene dynamicSource = source((scene, util) -> scene.getScene().createOverlayEvent(20).guiSnapshot(dynamic));
        assertNotNull(PonderGuiSnapshotRegistry.declaredSnapshotFor(dynamicSource.getOverlayEvents().get(0)));
        PonderGuiSnapshotRegistry.releaseProvider(declared);
        assertNull(PonderGuiSnapshotRegistry.declaredSnapshotFor(dynamicSource.getOverlayEvents().get(0)));
        PonderGuiSnapshotRegistry.register(id, Snapshot.fullTexture(texture("furnace"), 176, 76));
    }

    @Test void staticPointOfInterestRetainsEveryTickAndCoordinate() throws Exception {
        PonderScene source = source((scene, util) -> { scene.getScene().recordPointOfInterest(new Vec3d(1.5, 2, 3)); scene.idle(17); scene.getScene().recordPointOfInterest(new Vec3d(4, 5.5, 6)); });
        PonderScene copy = compile(fork(source)); assertEquals(2, copy.getPointOfInterestEvents().size());
        for (int index = 0; index < 2; index++) {
            assertEquals(source.getPointOfInterestEvents().get(index).getTick(), copy.getPointOfInterestEvents().get(index).getTick());
            assertEquals(source.getPointOfInterestEvents().get(index).getLocation(), copy.getPointOfInterestEvents().get(index).getLocation());
        }
    }

    @Test void malformedNativeTextureGeometryUnknownSnapshotsAndOversizedSlotNbtFailClosed() throws Exception {
        PonderScene source = source((scene, util) -> scene.getScene().createOverlayEvent(20).guiTexture(texture("furnace"), 0, 0, 176, 76, 256, 256, 176, 76, 0, 0, false));
        JsonObject root = fork(source).toJson(); JsonObject payload = nativePayload(root);
        payload.addProperty("textureU", 200);
        assertFalse(new AuthoringCompiler().validate(AuthoringDocument.fromJson(root), null).canPublish());
        payload.addProperty("textureU", 0); payload.addProperty("texture", "minecraft:../outside.png");
        assertFalse(new AuthoringCompiler().validate(AuthoringDocument.fromJson(root), null).canPublish());
        JsonObject recipeRoot = recipeFork(); JsonObject snapshot = nativePayload(recipeRoot).getAsJsonObject("snapshotData");
        snapshot.addProperty("kind", "arbitrary_callback"); assertFalse(new AuthoringCompiler().validate(AuthoringDocument.fromJson(recipeRoot), null).canPublish());
        snapshot.addProperty("kind", "vanilla_recipe");
        snapshot.getAsJsonObject("output").addProperty("nbt", "{large:\"" + String.join("", Collections.nCopies(65537, "x")) + "\"}");
        AuthoringValidation validation = new AuthoringCompiler().validate(AuthoringDocument.fromJson(recipeRoot), null);
        assertFalse(validation.canPublish());
        assertTrue(validation.getIssues().stream().anyMatch(issue -> issue.getMessage().contains("65536") || issue.getMessage().contains("64 KiB")));
    }

    @Test void explicitEmptyNativeTargetsAreValidButMalformedTargetArraysAreRejected() throws Exception {
        JsonObject root = fork(source((scene, util) -> scene.getScene().createOverlayEvent(20)
            .sceneOutline(new AxisAlignedBB(0, 0, 0, 1, 2, 3)))).toJson();
        assertTrue(new AuthoringCompiler().validate(AuthoringDocument.fromJson(root), null).canPublish());
        JsonObject payload = nativePayload(root); payload.addProperty("sourcePositions", "invalid");
        assertFalse(new AuthoringCompiler().validate(AuthoringDocument.fromJson(root), null).canPublish());
    }

    @Test void invalidStaticBoundsAreRejectedBeforeCreatingGeometry() throws Exception {
        JsonObject original = fork(source((scene, util) -> scene.getScene().createOverlayEvent(20)
            .sceneOutline(new AxisAlignedBB(0, 0, 0, 1, 2, 3)))).toJson();
        for (String bounds : new String[] {"[1,0,0,0,1,1]", "[0,0,0,257,1,1]", "[0,0,0,100,100,100]", "[0,0,0,30000001,1,1]", "[0,0,0,1,1]"}) {
            JsonObject root = AuthoringDocument.fromJson(original).toJson();
            nativePayload(root).add("bounds", new com.google.gson.JsonParser().parse(bounds));
            assertFalse(new AuthoringCompiler().validate(AuthoringDocument.fromJson(root), null).canPublish(), bounds);
        }
    }

    @Test void historicalSlotSnapshotsCannotDeclareAnInteractiveOrCurrentlyValidatedRecipeMode() throws Exception {
        JsonObject root = recipeFork();
        assertEquals("recorded_static", nativePayload(root).getAsJsonObject("snapshotData").get("presentation").getAsString());
        nativePayload(root).getAsJsonObject("snapshotData").addProperty("presentation", "interactive");
        assertFalse(new AuthoringCompiler().validate(AuthoringDocument.fromJson(root), null).canPublish());
    }

    @Test void entranceMovesRetainTheirQuadraticNonwalkingFlagThroughForkAndReverseReplay() throws Exception {
        JsonObject spawn = AuthoringModelTest.step("create_entity"); spawn.addProperty("entity", "minecraft:player");
        spawn.add("pos", AuthoringModelTest.pos(2, 3, 4)); spawn.addProperty("entranceAnimation", "down"); spawn.addProperty("entranceDuration", 20);
        PonderScene source = new AuthoringCompiler().compile(AuthoringModelTest.document(spawn), null).get(0);
        AuthoringDocument document = fork(source); PonderScene copy = compile(document);
        assertTrue(copy.getActorEvents().stream().anyMatch(event -> event.getType() == PonderScene.ActorEventType.MOVE && event.isEntranceMotion() && !event.hasWalkAnimation()));
        for (int tick : Arrays.asList(0, 10, 20, 10, 0))
            assertEquals(PreviewActorTimeline.evaluate(source.getActorEvents(), tick).get(0).position,
                PreviewActorTimeline.evaluate(copy.getActorEvents(), tick).get(0).position);
    }

    @Test void customInstantAndLegacyVisibilityFadesRetainTheirRuntimeBehaviorAfterFork() throws Exception {
        for (int[] values : new int[][] {{6, 9, 1}, {1, 1, 0}, {0, 0, 0}}) {
            PonderScene source = source((scene, util) -> {
                scene.world().setBlock(BlockPos.ORIGIN, Blocks.STONE.getDefaultState(), false);
                scene.getScene().recordWorldEvent(PonderScene.WorldEventType.SHOW_SECTION, Collections.singletonList(BlockPos.ORIGIN), null,
                    values[2] == 1 ? EnumFacing.WEST : null, values[0], null, null, null);
                scene.idle(20);
                scene.getScene().recordWorldEvent(PonderScene.WorldEventType.HIDE_SECTION, Collections.singletonList(BlockPos.ORIGIN), null,
                    values[2] == 1 ? EnumFacing.NORTH : null, values[1], null, null, null);
                scene.idle(20);
            });
            PonderScene copy = compile(fork(source));
            for (int tick : Arrays.asList(0, 3, 5, 20, 24, 28, 35, 10, 0)) {
                assertEquals(runtimeAttribute(source, tick, "visible"), runtimeAttribute(copy, tick, "visible"));
                assertEquals((Float) runtimeAttribute(source, tick, "fade"), (Float) runtimeAttribute(copy, tick, "fade"), 0.00001F);
                assertEquals((Float) runtimeAttribute(source, tick, "fadeOffsetX"), (Float) runtimeAttribute(copy, tick, "fadeOffsetX"), 0.00001F);
            }
        }
    }

    @Test void sameBlockSetWithoutANewSnapshotClearsOldTileNbtAfterForkButReplaceKeepsIt() throws Exception {
        for (boolean set : new boolean[] {true, false}) {
            PonderScene source = source((scene, util) -> {
                scene.world().setBlock(BlockPos.ORIGIN, Blocks.CHEST.getDefaultState(), false);
                scene.world().modifyBlockEntityNBT(util.select().position(BlockPos.ORIGIN), TileEntity.class, tag -> tag.setInteger("old", 1));
                scene.idle(5);
                if (set) scene.world().setBlock(BlockPos.ORIGIN, Blocks.CHEST.getDefaultState(), false);
                else scene.world().replaceBlocks(util.select().position(BlockPos.ORIGIN), Blocks.CHEST.getDefaultState(), false);
                scene.idle(5);
            });
            PonderScene copy = compile(fork(source));
            assertEquals(runtimeAttribute(source, 0, "tileNbt"), runtimeAttribute(copy, 0, "tileNbt"));
            assertEquals(runtimeAttribute(source, 5, "tileNbt"), runtimeAttribute(copy, 5, "tileNbt"));
            if (set) assertNull(runtimeAttribute(copy, 5, "tileNbt"));
            else assertNotNull(runtimeAttribute(copy, 5, "tileNbt"));
        }
    }

    private static JsonObject recipeFork() throws Exception {
        RecipePreview recipe = RecipePreview.fromSnapshot(Collections.singletonList(new ItemStack(Blocks.IRON_ORE)), new ItemStack(Items.COAL), new ItemStack(Items.IRON_INGOT), null, 1600);
        ResourceLocation id = VanillaRecipeSnapshot.register(recipe, true, true);
        return fork(source((scene, util) -> scene.getScene().createOverlayEvent(20).guiSnapshot(id))).toJson();
    }
    private static JsonObject nativePayload(JsonObject root) {
        for (JsonElement step : root.getAsJsonArray("scenes").get(0).getAsJsonObject().getAsJsonArray("steps"))
            if ("native_overlay".equals(step.getAsJsonObject().get("type").getAsString())) return step.getAsJsonObject().getAsJsonObject("overlay");
        throw new AssertionError("No native overlay");
    }
    private static PonderScene source(PonderStoryBoard board) {
        PonderScene source = new PonderScene(new PonderStoryBoardEntry(board, "fork_test", new ResourceLocation("fork_test", "native"),
            new ResourceLocation("minecraft:book")), new PonderLocalization()); source.program(new PonderSceneBuilder(source), source.getSceneBuildingUtil()); return source;
    }
    private static AuthoringDocument fork(PonderScene source) throws Exception { return AuthoringSceneForker.fork(source, "local:native_copy", (id, asset) -> fail("Unexpected asset")); }
    private static PonderScene compile(AuthoringDocument document) { return new AuthoringCompiler().compile(document, null).get(0); }
    private static ResourceLocation texture(String name) { return new ResourceLocation("minecraft", "textures/gui/container/" + name + ".png"); }
    private static void assertUnsupported(AuthoringDocument document) {
        assertTrue(document.getScenes().get(0).getAsJsonArray("steps").toString().contains("unsupported_builtin_event"));
        assertFalse(new AuthoringCompiler().validate(document, null).canPublish());
    }
    private static void assertOverlay(PonderScene.OverlayEvent a, PonderScene.OverlayEvent b) {
        assertEquals(a.getTick(), b.getTick()); assertEquals(a.getType(), b.getType()); assertEquals(a.getDuration(), b.getDuration());
        assertEquals(a.getText(), b.getText()); assertEquals(a.getPalette(), b.getPalette()); assertEquals(a.getPointAt(), b.getPointAt());
        assertEquals(a.getSourcePositions(), b.getSourcePositions()); assertEquals(a.getTargetPositions(), b.getTargetPositions());
        assertEquals(a.getOverlayId(), b.getOverlayId()); assertEquals(a.getParentOverlayId(), b.getParentOverlayId());
        assertEquals(a.getTextureLocation(), b.getTextureLocation()); assertEquals(a.getTextureU(), b.getTextureU()); assertEquals(a.getTextureV(), b.getTextureV());
        assertEquals(a.getRegionWidth(), b.getRegionWidth()); assertEquals(a.getRegionHeight(), b.getRegionHeight());
        assertEquals(a.getTextureWidth(), b.getTextureWidth()); assertEquals(a.getTextureHeight(), b.getTextureHeight());
        assertEquals(a.getDisplayWidth(), b.getDisplayWidth()); assertEquals(a.getDisplayHeight(), b.getDisplayHeight());
        assertEquals(a.getOffsetX(), b.getOffsetX()); assertEquals(a.getOffsetY(), b.getOffsetY()); assertEquals(a.isFramed(), b.isFramed());
        assertEquals(a.isScaleToParent(), b.isScaleToParent()); assertEquals(a.isStretchTexture(), b.isStretchTexture()); assertEquals(a.getStretchBorder(), b.getStretchBorder());
        assertEquals(a.getCaptionX(), b.getCaptionX()); assertEquals(a.getCaptionY(), b.getCaptionY());
        assertEquals(a.getCaptionOffsetX(), b.getCaptionOffsetX()); assertEquals(a.getCaptionOffsetY(), b.getCaptionOffsetY());
        assertEquals(a.getGuiX(), b.getGuiX()); assertEquals(a.getGuiY(), b.getGuiY()); assertEquals(a.getGuiWidth(), b.getGuiWidth()); assertEquals(a.getGuiHeight(), b.getGuiHeight());
        assertEquals(a.getLineStart(), b.getLineStart()); assertEquals(a.getLineEnd(), b.getLineEnd()); assertEquals(a.isLineWide(), b.isLineWide());
        assertEquals(a.getValueBoxCenter(), b.getValueBoxCenter()); assertEquals(a.getValueBoxExpand(), b.getValueBoxExpand());
        assertEquals(a.getSceneBounds(), b.getSceneBounds()); assertEquals(a.isOutlineChasing(), b.isOutlineChasing());
        assertEquals(a.isConnectorVisible(), b.isConnectorVisible()); assertEquals(a.isPlaceNearTarget(), b.isPlaceNearTarget());
    }
    private static Object runtimeAttribute(PonderScene scene, int tick, String name) throws Exception {
        Class<?> runtime = Class.forName("net.createmod.ponder.foundation.ui.PonderSceneRuntime"); Method build = runtime.getDeclaredMethod("buildState", PonderScene.class, int.class); build.setAccessible(true);
        Object state = build.invoke(null, scene, tick); Field blocks = state.getClass().getDeclaredField("blocksByPosition"); blocks.setAccessible(true);
        Object block = ((Map<?, ?>) blocks.get(state)).get(BlockPos.ORIGIN); Field value = block.getClass().getDeclaredField(name); value.setAccessible(true); return value.get(block);
    }
}
