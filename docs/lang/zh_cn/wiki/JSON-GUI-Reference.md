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

使用 Forge 注册的 GUI 时，在 `source.guiId` 填写客户端 GUI 的数字 ID。`source.mod` 默认使用方块 ID 的命名空间。GUI 处理器需要使用传入的隔离世界和玩家返回 `GuiContainer`。演示的槽位和属性更新只作用于隔离容器，指针按压由时间轴绘制。

可选的 `source.capture` 可把同一界面在主 GUI 矩形之外绘制的侧栏纳入演示。`left`、`top`、`right`、`bottom` 分别接受 0 到 512 的整数，单位为 GUI 像素，各边默认为 0。例如 `"capture": {"left": 24, "right": 80}` 会向左扩展 24 像素，向右扩展 80 像素。指针坐标仍以主 GUI 左上角为原点。使用上述边距时，`"pointer": {"x": -12, "y": 20}` 指向左侧扩展区域。

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

原生方块 GUI 演示可用 `screens` 为同一沙盒方块及其方块实体定义附加界面。界面可用 `guiId` 和 `mod` 指定 Forge GUI，或用 `factory` 填写已注册的预览工厂资源 ID。`source` 也支持这些选项。附加界面省略界面来源或 `capture` 时，沿用主界面的对应设置。在关键帧中填写 `screen` 可从该 tick 起切换，填写 `"screen": "main"` 返回主界面，省略 `screen` 则沿用当前界面。

各界面分别保留脚本设置的槽位、属性与指针状态。指针只在一次停留于同一界面的期间插值移动。可选的 AE2S 示例通过独立预览适配器展示铭刻器的输出侧面界面。[AE2S 铭刻器示例](../../../../examples/gui_demo/optional/ae2s/config/ponder/ponder_demo/ae2s_inscriber_subgui.json)

### 录制真实方块操作

给 `block_gui` 定义添加与 `source` 同级的 `sandbox`。录制器在独立实例建立测试世界，使用机台和玩家的原生逻辑执行操作。时间轴读取已录制的画面。`source` 接受 `block`、`meta`、`tileNbt` 和 `capture`。左右工具栏或顶边按钮需要相应的录制边距。

| 字段 | 写法 |
| --- | --- |
| `inventory` | 可选。以玩家背包编号为键的初始物品表。0–8 为快捷栏，9–35 为主背包。物品格式为 `{"item":"minecraft:coal","count":1}`，可添加 `meta` 和 SNBT 字符串 `nbt`。 |
| `neighbors` | 可选。每项具有 `block`，可带 `meta`、`tileNbt` 和偏移 `x`、`y`、`z`。偏移默认为 0，三轴中恰有一轴为 ±1。每个相邻面最多一个方块。 |
| `actions` | 按执行顺序排列的 1–2048 个操作。每项需写 `tick` 和 `type`，同一 tick 按数组顺序执行。tick 表示最早执行时刻，以叠层开始为 0。 |

| 操作 | 参数与效果 |
| --- | --- |
| `activate` | 玩家右键激活目标方块。 |
| `click` | 写 `slot` 或一组 `x`、`y`。槽位编号来自当前容器；界面坐标以当前 GUI 左上角为原点。可写 `button` 指定左键 0、右键 1 或中键 2，默认左键。 |
| `wait_screen` | `screen` 指定实际 GUI 的简单类名或完整类名。 |
| `wait_slot` | `slot` 指定当前容器的槽位，`stack` 指定预期物品堆，`null` 表示空槽。 |
| `wait_nbt` | `nbt` 为非空 SNBT 复合标签，检查目标方块实体在服务端保存的对应字段。 |
| `close` | 玩家关闭当前界面，后续可再次激活同一个方块。 |

等待操作的 `timeout` 范围为 1–2400 tick，默认为 100。给实际加工和网络响应留足时间。可在关闭、重新打开 GUI 后检查 `wait_nbt`，也可在取出产物后同时检查背包槽和输出槽。带完整步骤的示例见[熔炉与 AE2S 铭刻器](../../../../examples/gui_demo/README.md)。

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
