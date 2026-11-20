# GUI 与交互参考

当前已支持的 GUI 相关操作：

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

把贴图区域绘制为 GUI 叠层。适合稳定的静态界面和自定义面板。

常用字段：

- `texture`：资源路径，省略 `textures/` 和 `.png` 时会自动补全
- `regionWidth`、`regionHeight`：源区域尺寸
- `displayWidth`、`displayHeight`：显示尺寸
- `id`：叠层 id，供后续高亮引用
- `pointAt`、`placeNearTarget`、`independentY`、`offsetX`、`offsetY`

## `gui_snapshot`

按 id 绘制已注册的 GUI 快照。内置示例包含原版熔炉和 Thermal Expansion 机器快照。

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

通过隐藏沙盒方块打开真实方块 GUI，捕获打开后的界面，并渲染到 Ponder 场景里。
`machine_gui` 是 `block_gui` 的别名。

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

必填字段：

- `blockGui`：用于打开 GUI 的方块 id
- `guiWidth`、`guiHeight`：GUI 逻辑尺寸

可选字段：

- `meta`：方块 metadata，默认 `0`
- `nbt`：打开 GUI 前合并到沙盒 TileEntity 的 NBT
- `id`：叠层 id，供 `gui_outline_text` 引用
- `pointAt`、`placeNearTarget`、`independentY`、`offsetX`、`offsetY`

## `show_gui_demo` / `hide_gui_demo`

在 `show_gui_demo` 中设置叠层 `id`、以 tick 计的 `duration` 和 `demo` 定义。`demo` 可以内联，也可以通过资源 ID 引用 `assets/<namespace>/ponder/gui_demos/<path>.json`。用相同的叠层 `id` 调用 `hide_gui_demo` 可提前关闭界面。

将 `source.type` 设为 `block_gui` 可渲染方块的真实容器界面。`source.block` 为方块注册 ID。`source.meta` 可省略，默认为 0。`source.tileNbt` 可选，用 SNBT 提供方块实体初始数据。

关键帧的 `tick` 相对于叠层开始时间。`slots` 以容器槽位编号为键，值是包含 `item` 注册 ID 的物品栈，也可指定 `count`、`meta` 和物品 `nbt`。用 `null` 清空槽位。`properties` 以容器属性编号为键，值为整数。槽位和属性编号由各机器容器定义。关键帧省略的字段沿用已有状态。`pointer` 可通过 `slot` 指向槽位，或使用 GUI 内的 `x`、`y` 坐标定位，也可指定 `pressed`。用 `pointer: null` 隐藏指针。

场景时间轴决定每次变化何时显示。暂停时保持当前状态，回退时恢复到所选 tick 的状态。指针演示脚本安排的操作。下面的例子向原版熔炉输入槽放入铁矿石，燃料、进度和出料见[完整示例](../../../../examples/gui_demo/config/ponder/ponder_demo/vanilla_furnace.json)。

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

## `gui_outline_text`

在父 GUI 叠层内部高亮一个矩形区域，并绑定说明文字。

```json
{
  "type": "gui_outline_text",
  "gui": "machine_gui",
  "guiX": 54,
  "guiY": 24,
  "guiWidth": 22,
  "guiHeight": 22,
  "duration": 60,
  "text": "输入槽"
}
```

## `gui_interaction`

执行 `interactionDefinitions` 中定义的复用 GUI 步骤序列，也可以直接执行内联 `steps`。
