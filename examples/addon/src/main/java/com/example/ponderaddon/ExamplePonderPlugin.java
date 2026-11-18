package com.example.ponderaddon;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class ExamplePonderPlugin implements PonderPlugin {
    private static final ResourceLocation MECHANISMS = id("mechanisms");
    private static final ResourceLocation PULSE_TAG = id("codec");
    private static final ResourceLocation SCHEMATIC = id("codec_demo");
    private static final ResourceLocation PISTON = minecraft("piston");
    private static final ResourceLocation STICKY_PISTON = minecraft("sticky_piston");
    private static final ResourceLocation PAPER = minecraft("paper");

    @Override
    public String getModId() {
        return ExampleAddon.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(PISTON, SCHEMATIC, ExamplePonderPlugin::pistonScene, MECHANISMS)
            .identifiedBy(id("service_loader"));
        helper.addStoryBoard(STICKY_PISTON, SCHEMATIC, ExamplePonderPlugin::stickyPistonScene, MECHANISMS)
            .identifiedBy(id("imc_registration"));
        helper.addStoryBoard(PAPER, SCHEMATIC, ExamplePonderPlugin::localPulseScene, PULSE_TAG)
            .identifiedBy(id("codec_sync"));
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(MECHANISMS)
            .title("Example Mechanisms")
            .description("Scenes registered by a separate addon through the public Ponder API.")
            .item(Blocks.PISTON)
            .addToIndex()
            .register();
        helper.addToTag(MECHANISMS)
            .add(PISTON)
            .add(STICKY_PISTON);
        helper.registerTag(PULSE_TAG)
            .title("Local Pulse")
            .description("An outline and success marker animated by a local Java scene.")
            .item(Items.REDSTONE)
            .addToIndex()
            .register();
        helper.addToTag(PULSE_TAG).add(PAPER);
    }

    @Override
    public void registerSharedText(SharedTextRegistrationHelper helper) {
        helper.registerSharedText("piston.summary", "Sections can move independently from the virtual world");
        helper.registerSharedText("pulse.summary", "A local Java step highlights this block.");
    }

    private static void pistonScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("service_loader", "Moving Independent Sections");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        Selection machine = util.select().layersFrom(1);
        scene.world().showSection(machine, EnumFacing.DOWN);
        scene.idle(20);
        scene.overlay().showText(65)
            .text(PonderIndex.getLangAccess().getShared(id("piston.summary")))
            .pointAt(util.vector().centerOf(2, 1, 2))
            .placeNearTarget()
            .attachKeyFrame();
        scene.overlay().showControls(util.vector().topOf(2, 1, 2), Pointing.DOWN, 35)
            .rightClick()
            .withItem(new ItemStack(Blocks.PISTON));
        scene.overlay().showOutline(PonderPalette.INPUT, "input", util.select().position(2, 1, 2), 40);
        scene.overlay().showLine(PonderPalette.OUTPUT, util.vector().centerOf(2, 1, 2),
            util.vector().centerOf(3, 1, 2), 40);
        scene.idle(25);

        ElementLink<WorldSectionElement> moving = scene.world().makeSectionIndependent(
            util.select().position(3, 1, 2));
        scene.world().configureCenterOfRotation(moving, util.vector().centerOf(3, 1, 2));
        scene.world().configureStabilization(moving, util.vector().centerOf(3, 1, 2));
        scene.world().moveSection(moving, new Vec3d(0, 1, 0), 20);
        scene.world().rotateSection(moving, 0, 90, 0, 20);
        scene.effects().emitParticles(util.vector().centerOf(3, 1, 2),
            scene.effects().simpleParticleEmitter(EnumParticleTypes.VILLAGER_HAPPY, new Vec3d(0, .04, 0)), 1, 20);
        scene.idle(25);

        scene.world().setBlock(util.grid().at(2, 1, 2), Blocks.PISTON.getDefaultState(), true);
        scene.world().modifyBlock(util.grid().at(2, 1, 2),
            state -> state.withProperty(BlockPistonBase.FACING, EnumFacing.EAST), false);
        scene.world().createItemEntity(util.vector().topOf(2, 1, 2),
            new Vec3d(0, .1, 0), new ItemStack(Blocks.PISTON));
        scene.special().movePointOfInterest(util.vector().centerOf(2, 1, 2));
        scene.rotateCameraY(30);
        scene.idle(30);
        scene.world().hideIndependentSection(moving, EnumFacing.UP);
        scene.markAsFinished();
    }

    private static void stickyPistonScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("imc_registration", "Sticky Piston Demonstration");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.world().showSection(util.select().layersFrom(1), EnumFacing.DOWN);
        scene.idle(40);
        scene.effects().indicateSuccess(util.grid().at(2, 1, 2));
        scene.idle(20);
        scene.markAsFinished();
    }

    private static void localPulseScene(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("codec_sync", "Local Pulse");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);
        scene.world().showSection(util.select().layersFrom(1), EnumFacing.DOWN);
        scene.overlay().showText(80)
            .text(PonderIndex.getLangAccess().getShared(id("pulse.summary")))
            .pointAt(new Vec3d(2.5, 2.0, 2.5))
            .placeNearTarget();
        pulse(scene, util, util.grid().at(2, 1, 2), PonderPalette.GREEN, 40);
        scene.addKeyframe();
        scene.idle(30);
        scene.markAsFinished();
    }

    private static void pulse(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, PonderPalette color,
        int duration) {
        scene.overlay().showOutline(color, id("pulse"), util.select().position(pos), duration);
        scene.effects().indicateSuccess(pos);
        scene.idle(duration);
    }

    private static ResourceLocation minecraft(String path) {
        return new ResourceLocation("minecraft", path);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(ExampleAddon.MOD_ID, path);
    }
}
