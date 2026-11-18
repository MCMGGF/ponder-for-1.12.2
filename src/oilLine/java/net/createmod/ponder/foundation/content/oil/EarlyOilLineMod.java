package net.createmod.ponder.foundation.content.oil;

import java.io.File;

import net.createmod.ponder.Ponder;
import net.createmod.ponder.Reference;
import net.createmod.ponder.api.client.PonderLessonPractice;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.OilLineControlScreen;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = EarlyOilLineMod.MOD_ID, name = "Ponder Early Oil Line", version = Reference.VERSION,
    dependencies = "required-after:" + Reference.MOD_ID + "@[" + Reference.VERSION + "]",
    clientSideOnly = true, acceptableRemoteVersions = "*")
public final class EarlyOilLineMod {
    public static final String MOD_ID = "ponder_early_oil_line";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide().isClient()) {
            PonderIndex.addPlugin(new OilProductionLinePlugin());
            registerPractice();
        }
    }

    static void registerPractice() {
        PonderLessonPractice practice = new PonderLessonPractice("石油线操作推演",
            "教学配置已变化，操作台暂不可用", EarlyOilLineMod::createPracticeScreen);
        for (String chapter : OilProductionLinePlugin.CHAPTERS) {
            practice.register(Ponder.asResource(OilProductionLinePlugin.scenePath(chapter)));
        }
    }

    private static GuiScreen createPracticeScreen(GuiScreen parent) {
        File config = Loader.instance().getConfigDir();
        if (config == null || config.getParentFile() == null) {
            throw new IllegalStateException("Config directory is unavailable");
        }
        OilLinePackBaseline.verify(config.getParentFile().toPath());
        return new OilLineControlScreen(parent);
    }
}
