# GUI Reference

Supported GUI-related operations:

- `gui_texture`
- `gui_snapshot`
- `block_gui`
- `machine_gui`
- `gui_outline_text`
- `gui_interaction`
- `interactionDefinitions`
- `show_gui_demo`
- `hide_gui_demo`

## `gui_texture`

Draws a texture region as a GUI overlay. Use it for stable static screens and custom panels.

Common fields:

- `texture`: resource location, with `textures/` and `.png` added automatically when omitted
- `regionWidth`, `regionHeight`: source region size
- `displayWidth`, `displayHeight`: rendered size
- `id`: overlay id for later highlight operations
- `pointAt`, `placeNearTarget`, `independentY`, `offsetX`, `offsetY`

## `gui_snapshot`

Draws a registered snapshot by id. Built-in examples include Minecraft furnace and Thermal Expansion machine snapshots.

```json
{
  "type": "gui_snapshot",
  "id": "furnace_gui",
  "snapshot": "ponder:gui_snapshot/minecraft_furnace_live",
  "duration": 80,
  "pointAt": [1.5, 1.0, 1.5],
  "placeNearTarget": true
}
```

## `block_gui` / `machine_gui`

Opens a real block GUI through a hidden sandbox block, captures the resulting screen, and renders it inside Ponder.
`machine_gui` is an alias for `block_gui`.

```json
{
  "type": "block_gui",
  "id": "machine_gui",
  "blockGui": "thermalexpansion:machine",
  "meta": 0,
  "nbt": { "Energy": 12000 },
  "guiWidth": 198,
  "guiHeight": 166,
  "duration": 100,
  "pointAt": [1.5, 1.0, 1.5],
  "placeNearTarget": true
}
```

Required fields:

- `blockGui`: block id used to open the GUI
- `guiWidth`, `guiHeight`: logical GUI size

Optional fields:

- `meta`: block metadata, defaults to `0`
- `nbt`: tile entity NBT merged into the sandbox tile before opening the GUI
- `id`: overlay id for `gui_outline_text`
- `pointAt`, `placeNearTarget`, `independentY`, `offsetX`, `offsetY`

## `show_gui_demo` / `hide_gui_demo`

Use `show_gui_demo` with an overlay `id`, `duration` in ticks, and a `demo` definition. The definition can be inline or referenced by resource ID from `assets/<namespace>/ponder/gui_demos/<path>.json`. Use `hide_gui_demo` with the same overlay `id` to close it early.

Set `source.type` to `block_gui` to render a block's real container screen. `source.block` is the block registry ID. Optional `source.meta` defaults to zero, and optional `source.tileNbt` supplies the initial tile entity SNBT.

The keyframe `tick` is relative to the start of the overlay. `slots` maps container slot indices to item stacks with an `item` registry ID, optional `count`, `meta` and item `nbt`. Use `null` to clear a slot. `properties` maps container property IDs to integer values. Both sets of IDs are defined by the machine's container. A keyframe retains fields it omits. `pointer` can target a `slot` or use GUI-local `x` and `y`, with optional `pressed`. Use `pointer: null` to hide it.

The scene timeline controls when each change appears. Pause holds the displayed state and moving backward restores the state for the selected tick. The pointer illustrates scripted actions. The example below loads iron ore into the input slot of a vanilla furnace. See [the full example](../../../../examples/gui_demo/config/ponder/ponder_demo/vanilla_furnace.json) for fuel, progress and output.

```json
{
  "type": "show_gui_demo",
  "id": "furnace",
  "duration": 80,
  "demo": {
    "source": { "type": "block_gui", "block": "minecraft:furnace" },
    "keyframes": [
      { "tick": 20, "slots": { "0": { "item": "minecraft:iron_ore" } }, "pointer": { "slot": 0 } }
    ]
  }
}
```

For a GUI registered with Forge, set `source.guiId` to the numeric client GUI ID. `source.mod` defaults to the block ID's namespace. The GUI handler must return a `GuiContainer` using the supplied detached world and player. Slot and property updates apply to that container. The timeline draws pointer presses.

Use optional `source.capture` to include side panels drawn by the same screen outside its main GUI rectangle. Each of `left`, `top`, `right`, and `bottom` accepts an integer from 0 to 512 in GUI pixels and defaults to 0. For example, `"capture": {"left": 24, "right": 80}` adds 24 pixels to the left and 80 to the right. Pointer coordinates remain relative to the main GUI's upper-left corner. With those margins, `"pointer": {"x": -12, "y": 20}` points into the left margin.

For a native block GUI demo, use `screens` to name additional views of the same sandbox block and tile. A view can set `guiId` and `mod` for a Forge GUI, or `factory` for a registered preview factory identified by a resource ID. These options also apply to `source`. Additional views inherit the main view's GUI resolver and capture bounds when their definitions omit the corresponding options. Set `screen` in a keyframe to switch views on that tick, or use `"screen": "main"` to return to the original view. A keyframe without `screen` continues on the active view.

Each view retains its own scripted slots, properties, and pointer state. Pointer movement is interpolated within each visit to a view. The optional AE2S example uses a separate preview adapter for the Inscriber's Output Sides screen. [AE2S Inscriber example](../../../../examples/gui_demo/optional/ae2s/config/ponder/ponder_demo/ae2s_inscriber_subgui.json)

## `gui_outline_text`

Highlights a rectangle inside a parent GUI overlay and attaches text.

```json
{
  "type": "gui_outline_text",
  "gui": "machine_gui",
  "guiX": 54,
  "guiY": 24,
  "guiWidth": 22,
  "guiHeight": 22,
  "duration": 60,
  "text": "Input slot"
}
```

## `gui_interaction`

Runs reusable GUI step sequences from `interactionDefinitions`, or executes inline `steps`.
