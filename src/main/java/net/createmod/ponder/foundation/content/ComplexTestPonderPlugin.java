package net.createmod.ponder.foundation.content;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;

/**
 * Registers vanilla-only scenes intended for renderer and UI stress testing.
 */
public class ComplexTestPonderPlugin implements PonderPlugin {

    private static final ResourceLocation COMPLEX_TAG = Ponder.asResource("complex_test");
    private static final ResourceLocation FACTORY = new ResourceLocation("minecraft", "observer");
    private static final ResourceLocation REDSTONE = new ResourceLocation("minecraft", "redstone");
    private static final ResourceLocation ASSEMBLY = new ResourceLocation("minecraft", "beacon");
    private static final ResourceLocation LOGISTICS = new ResourceLocation("minecraft", "hopper");
    private static final ResourceLocation GUI_LAB = new ResourceLocation("minecraft", "comparator");

    @Override
    public String getModId() {
        return Ponder.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.addStoryBoard(FACTORY, "complex/factory_line", ComplexTestScenes::factoryLine, COMPLEX_TAG);
        helper.addStoryBoard(REDSTONE, "complex/redstone_matrix", ComplexTestScenes::redstoneMatrix, COMPLEX_TAG);
        helper.addStoryBoard(ASSEMBLY, "complex/assembly_core", ComplexTestScenes::assemblyCore, COMPLEX_TAG);
        helper.addStoryBoard(LOGISTICS, "complex/logistics_hub", ComplexTestScenes::logisticsHub, COMPLEX_TAG);
        helper.addStoryBoard(GUI_LAB, "complex/gui_lab", ComplexTestScenes::guiLab, COMPLEX_TAG);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(COMPLEX_TAG)
            .title("Complex Feature Tests")
            .description("Large vanilla scenes covering motion, redstone, actors, particles, overlays and GUI panels.")
            .item(Blocks.BEACON)
            .addToIndex()
            .register();

        for (ResourceLocation component : new ResourceLocation[] {
            FACTORY, REDSTONE, ASSEMBLY, LOGISTICS, GUI_LAB
        }) {
            helper.addTagToComponent(component, COMPLEX_TAG);
        }
    }
}
