package net.createmod.ponder.foundation.ui;

import net.minecraft.client.gui.GuiScreen;

/** A local child screen whose direct owner is an existing Ponder viewer instance. */
public interface PonderOwnedChildScreen {

    GuiScreen getPonderParent();
}
