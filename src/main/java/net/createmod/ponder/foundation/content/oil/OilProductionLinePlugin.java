package net.createmod.ponder.foundation.content.oil;

import java.util.Arrays;
import java.util.List;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.foundation.content.mmce.MmceMachineDescriptor;
import net.createmod.ponder.foundation.content.mmce.MmceMachineIndex;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.registration.PonderComponentMatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/** Additional process lessons; the existing machine storyboards remain unchanged. */
public final class OilProductionLinePlugin implements PonderPlugin {
    public static final ResourceLocation TAG = Ponder.asResource("early_oil_line");
    public static final ResourceLocation COMPONENT = Ponder.asResource("production/early_oil_line");
    public static final List<String> CHAPTERS = java.util.Collections.unmodifiableList(Arrays.asList(
        "overview", "steam", "pumping", "distillation", "power", "troubleshooting"));

    @Override
    public String getModId() {
        return Ponder.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        if (!hasMachines()) {
            return;
        }
        ItemStack icon = findMachine(OilLineRecipeFacts.MACHINE_DISTILLATION).getDisplayStack();
        if (!icon.isEmpty()) {
            PonderIndex.registerComponentMatcher(COMPONENT, PonderComponentMatcher.exact(
                icon.getItem().getRegistryName(), icon.getItem().getHasSubtypes() ? icon.getMetadata() : -1,
                icon.getTagCompound(), icon.getTagCompound()));
        }
        // Registration only reads descriptors. Structures are compiled when a lesson is opened.
        for (int i = 0; i < CHAPTERS.size(); i++) {
            final int chapter = i;
            helper.addStoryBoard(COMPONENT, Ponder.asResource("mmce/empty"),
                (scene, util) -> OilProductionLineScenes.build(scene, util, chapter), TAG);
        }
        for (String machineId : OilProductionLineScenes.MACHINES) {
            MmceMachineDescriptor machine = findMachine(machineId);
            for (int i = 0; i < CHAPTERS.size(); i++) {
                final int chapter = i;
                helper.addStoryBoard(machine.getComponentId(), Ponder.asResource("mmce/empty"),
                    (scene, util) -> OilProductionLineScenes.build(scene, util, chapter), TAG);
            }
        }
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        if (!hasMachines()) {
            return;
        }
        ItemStack icon = findMachine(OilLineRecipeFacts.MACHINE_DISTILLATION).getDisplayStack();
        helper.registerTag(TAG).title("前期石油生产线")
            .description("锅炉供汽、勘探抽油、批次分馏、储油发电；含接线、操作与故障排查。")
            .item(icon).addToIndex().register();
        helper.addTagToComponent(COMPONENT, TAG);
    }

    static MmceMachineDescriptor findMachine(String path) {
        for (MmceMachineDescriptor machine : MmceMachineIndex.getInstance().getMachines()) {
            if (machine.getId().getPath().equals(path)) {
                return machine;
            }
        }
        return null;
    }

    private static boolean hasMachines() {
        for (String path : OilProductionLineScenes.MACHINES) {
            if (findMachine(path) == null) {
                return false;
            }
        }
        return true;
    }
}
