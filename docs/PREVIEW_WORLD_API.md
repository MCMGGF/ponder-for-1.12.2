# Detached Preview World

`net.createmod.ponder.foundation.ui.DetachedPonderWorld` is available for both
scene TESRs and independently constructed GUI snapshots.

```java
DetachedPonderWorld world = new DetachedPonderWorld();
world.putBlock(pos, state, nullableNbt);
TileEntity tile = world.getTileEntity(pos);
EntityPlayer player = world.createPlayer();
world.clear();
```

For rendering a new frame, `setBlocks(Map<BlockPos, IBlockState>,
Map<BlockPos, NBTTagCompound>)` replaces the local display data while retaining
unchanged tile instances. This world has no real-world or network connection.
Normal `World.setBlockState` and ticking APIs cannot modify the display.

The preview/UI agent owns this class. The root snapshot implementation can use
a separate instance and create its own detached player without touching
`Minecraft.world` or `Minecraft.player`. `createPlayer()` constructs an independent
inventory and offline identity. It never copies or retains the current player.

`PonderUI.showcase(componentId, sceneIndex, parentScreen)` already exists.

## Scene And Camera Corrections

- `SHOW_SECTION` reveals the current block state, including dynamically placed
  MMCE blocks and replacements. Only `RESTORE_BLOCKS` restores the schematic;
  showing a destroyed block or an AIR replacement does not resurrect it.
- Fixed camera bounds include positions with non-AIR geometry at any point in
  the timeline. Empty selection cells, AIR-only writes and missing block IDs
  cannot enlarge the fit. Movement endpoints and sampled transformed corners
  of real blocks still contribute, including geometry shown later.
- The initial fit covers the full yaw envelope and the allowed pitch range
  `[-MAX_PITCH, MAX_PITCH]`, currently 75 degrees in either direction. It uses
  the maximum projected vertical extent in that range, not the default pitch
  or current animation frame.

## Layout And Paused Frames

- Debug screens use a shared rectangle layout for drawing and hit testing.
  Below 720 pixels wide or 420 pixels high (scaled GUI coordinates), Preview,
  Details and Timeline use separate tabs. Component and operation scrolling
  use the same complete-row rectangles as their click regions.
- Captions retain all wrapped lines. Their height is bounded by the space
  between the header and timeline, with a maximum of 156 scaled pixels.
  Arrows, mouse wheel and focused Page Up/Down navigate pages; interacting
  with a caption pauses playback so it can be read. A new caption resets the
  page, while resizing retains the current reading position within a page.
- Caption wheel input is consumed before the default GUI mouse dispatcher can
  interpret it as a held-button drag. Reading focus cancels both camera and
  timeline dragging. Changing debug tabs immediately drops the prior preview
  hit region, before the next draw.
- A screen-local frame cache keys on scene identity and exact render tick.
  Paused frames reuse block state, solid/translucent lists and applied preview
  world data. Translucent ordering is recomputed only when camera orientation
  changes. Seeking, partial-tick playback and scene changes invalidate the
  frame; closing/reloading clears all retained frame data.
- `DetachedPonderWorld` was not modified for this optimization. Its existing
  safety boundary remains unchanged. Active animation still rebuilds frames;
  these changes are not a claim that every large-scene rendering cost is gone.

## Checks Performed

- `scripts/test-preview-math.ps1`: 730,249 assertions passed, including positive
  and negative pitch sweeps, analytical yaw/pitch extrema, seven structure
  shapes, five viewport sizes and both showcase/debug layouts.
- `PonderSceneRuntimeTest`: 12 behavior tests passed via the JUnit launcher.
  They replay dynamic placement, hiding, revealing, replacement, restoration,
  destruction, AIR writes, empty selections and moving/rotating sections.
- Independent `javac` compilation of the changed scene runtime, camera math
  and runtime tests passed against cached Cleanroom MCP and existing Ponder
  classes. The root build must still perform complete Gradle compilation and
  remapping.
- The headless registry bootstrap reported missing built-in recipe assets
  (`.mcassetsroot`) on the standalone classpath. No assertion failed; this run
  does not validate client startup, recipe loading or rendered output.
- `scripts/test-preview-ui.ps1`: 14 JUnit tests passed for frame-cache
  invalidation/reuse, complete caption paging, debug rectangle geometry and
  wheel dispatch ordering.
  Layout cases include 320x240 through 1920x1080 scaled coordinates. The
  paused-cache test reuses an 8,192-entry fixture across 600 repeated frames.
- `PreviewCaptionInputTest`: three additional tests directly called screen
  input callbacks without initializing Minecraft or OpenGL. Caption clicks
  and Page Up/Down pause playback and cancel drags; changing tabs clears the
  old preview hit region immediately. All three passed.
- Independent `javac` compilation of `PonderDebugScreen`, `PonderUI`, the four
  new helper classes and their tests passed. These tests do not initialize
  a renderer and must not be presented as real-client visual acceptance.
- No target-instance files were changed by the preview/UI work.

## Remaining Runtime Gates

- Real-client rendered screenshots and interaction at every requested GUI scale.
- Native custom TESR compatibility, including renderers that read Minecraft
  globals directly instead of their tile's detached world.
- Actual font rendering, pagination controls, scrolling and resized debug
  tabs still need real-client interaction acceptance. Pure geometry and
  pagination tests cover logical bounds, not Minecraft's rendered pixels.
- Complex simultaneous local section rotations have event/midpoint/end bounds
  sampling. Static MMCE structure bounds are exact; arbitrary continuous
  multi-axis section animation extrema are not yet proven.
- Initial fit includes block geometry and validated finite MMCE controller
  model bounds. Those controller bounds do not guarantee coverage of every
  addon's custom TESR geometry; oversized models still need rendered checks.
