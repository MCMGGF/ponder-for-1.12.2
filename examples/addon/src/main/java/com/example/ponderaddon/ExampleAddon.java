package com.example.ponderaddon;

import net.createmod.ponder.foundation.PonderIndex;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = ExampleAddon.MOD_ID, name = "Ponder Example Addon", version = "1.0.0",
    clientSideOnly = true, acceptableRemoteVersions = "*",
    dependencies = "required-after:ponder_legacy@[0.3.7-beta]")
public final class ExampleAddon {
    public static final String MOD_ID = "ponder_example";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        PonderIndex.addPlugin(new ExamplePonderPlugin());
    }
}
