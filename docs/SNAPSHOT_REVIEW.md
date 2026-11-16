# Snapshot Review

Review date: 2026-10-10. Root-owned snapshot classes were inspected, not edited.

## P1: Native GUI initialization writes live container state

`EmbeddedReflectiveGuiSnapshot.java:237` and
`EmbeddedReflectiveTabGuiSnapshot.java:208` call
`gui.setWorldAndResolution(mc, ...)`.

Local Cleanroom MCP bytecode proves `GuiContainer.initGui()` executes
`Minecraft.player.openContainer = inventorySlots` (putfield at bytecode offset
15). Passing a detached player inventory to the constructor does not isolate
this singleton write. Never initialize a native GuiContainer through the normal
screen lifecycle for snapshots. A verified draw-only adapter must initialize
its own display fields and approved elements without calling that method.
Saving/restoring the live field would still violate the read-only requirement.

`PreviewIsolationSourceTest.nativeSnapshotsNeverInitializeTheRealPlayersContainer`
guards this known call path and intentionally fails until corrected.

## P2: Native snapshots crop instead of resizing

Both reflective snapshot renderers pass target `width/height` to scissor setup
but never scale native GUI coordinates to that target. Ponder's placement logic
fits panels to smaller viewports, so native content is cropped while highlight
rectangles are scaled. `EmbeddedGuiFurnaceSnapshot` ignores target width/height
and clips using native xSize/ySize, so it can overflow the intended panel.

Use a scoped transform from native logical size to the target screen rectangle.
Keep scissor in absolute screen pixels and restore prior scissor state. Restore
depth, lighting, blend and texture state in `finally`, including renderer failure.

## P2: Class-name tile path has no corresponding block in detached world

`EmbeddedReflectiveGuiSnapshot.createTile` class-name branch, plus
`EmbeddedReflectiveTabGuiSnapshot.createTile`, construct a tile directly and set
its world/position but never populate that world. Reads of its own block state
return AIR; metadata-dependent texture/GUI state can consequently be wrong.

For known Thermal machines, resolve the actual registered machine state and use
`world.putBlock` then `world.getTileEntity`. Verify the returned tile class.
Do not guess metadata from alphabetical ordering.

## Other Findings

- `PonderDebugScreen.onGuiClosed()` correctly drops its detached world and NBT
  cache. Shared snapshot registry caches are reset on world unload, not on every
  Ponder screen close. Their counts are bounded.
- Native `GuiContainer.drawScreen` still reads the singleton player's carried
  stack. Draw-only snapshots should not use this full method without an audited
  adapter. Do not invoke native `onGuiClosed`, which calls the container close
  callback with the real player.
- FTB returns the same parent screen object. Installed FTBLib's
  `GuiWrapper.initGui` calls `GuiBase.initGui`, which refreshes and aligns widgets.
  Returning to the same object therefore does not alone prove quest scroll
  retention. The quest adapter must capture/restore scroll state or demonstrate
  that its concrete panel state survives that refresh.
- No Thermal/CoFH-named JAR was found in the requested instance inventory. This
  check does not exclude renamed JARs and does not establish native Thermal
  runtime behavior.
