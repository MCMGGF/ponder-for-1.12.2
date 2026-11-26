# 脚本化 GUI 演示

## 录制真实操作

| 所需模组 | 复制到实例 `config/ponder/ponder_demo/` 的场景 | 打开场景 |
| --- | --- | --- |
| Ponder Legacy | [sandbox_furnace.json](config/ponder/ponder_demo/sandbox_furnace.json) | `/ponderui ponder_demo:sandbox_furnace 0` |
| Ponder Legacy、AE2S 1.1.2 及其依赖 | [sandbox_ae2s_inscriber.json](optional/ae2s/config/ponder/ponder_demo/sandbox_ae2s_inscriber.json) | `/ponderui ponder_demo:sandbox_ae2s_inscriber 0` |
| Mekanism CE 10.0.5.221 | [粉碎机](optional/mekanism/config/ponder/ponder_demo/sandbox_mek_crusher.json) | `/ponderui ponder_demo:sandbox_mek_crusher 0` |
| Mekanism CE 10.0.5.221 | [富集仓](optional/mekanism/config/ponder/ponder_demo/sandbox_mek_enrichment.json) | `/ponderui ponder_demo:sandbox_mek_enrichment 0` |
| Thermal Expansion 5.5.7.1 | [红石炉](optional/thermalexpansion/config/ponder/ponder_demo/sandbox_thermal_furnace.json) | `/ponderui ponder_demo:sandbox_thermal_furnace 0` |
| Thermal Expansion 5.5.7.1 | [磨粉机](optional/thermalexpansion/config/ponder/ponder_demo/sandbox_thermal_pulverizer.json) | `/ponderui ponder_demo:sandbox_thermal_pulverizer 0` |

可以运行 `gradlew sandboxExampleZips`，将 `build/sandbox-examples` 中所需的 ZIP 解压到实例目录。启动后客户端会安排录制。游戏运行中改动本地场景时执行 `/ponder reload`。改动资源包时按 F3+T。录制完成后，在世界内执行表中的命令。Ponder 时间轴可暂停、回退和重播已录制的画面。

首次录制会在后台启动游戏客户端，加载整合包的模组并执行配方。录制进程的堆上限默认取主客户端的堆上限，最高 4096 MiB。可通过启动参数 `-Dponder.sandbox.maxHeapMiB=6144` 调整。相同场景和环境会复用实例 `ponder-gui-cache/recordings` 中的录制。启动时，若其他客户端正在使用该缓存，本次会跳过清理。空闲时优先清除较早的旧版本，录制缓存的清理目标为 512 MiB。当前场景所需的录制可以超过该值。

安装对应 JSON 后，也可从上述四台机器的物品进入思索。粉碎机将圆石粉碎为沙砾，富集仓将红石加工为压缩红石，红石炉将沙子烧成玻璃，磨粉机将一根骨头磨成六份骨粉。录制脚本检查机器产出，并把成品取回玩家背包。整合包改过配方时，可在相应 JSON 中调整输入和预期产物。热力机器原有的界面教程可从同一物品翻页进入，首次查看时录制侧栏的展开与收起。

熔炉示例从玩家背包取出铁矿石和煤，通过原版槽位放入熔炉，等待实际产出铁锭，然后取回背包。铭刻器示例通过左侧按钮开启自动输出，进入输出侧面，清空原选择并启用顶面。脚本返回主界面，关闭并重新打开铭刻器，核对服务端的侧面掩码和模板槽位，再放入硅。相邻的创造能源元件为加工供电，脚本等待产出压印硅并取回背包。此场景只需复制 JSON。

### 为其他机器编写录制脚本

复制熔炉场景，修改场景 ID、展示的方块以及 `show_gui_demo.demo`。录制定义包含 `source` 和 `sandbox` 两个对象。

| 字段 | 填写方式 |
| --- | --- |
| `source` | `type` 填 `block_gui`，`block` 填机器的注册 ID。`meta` 默认 0，范围 0–15。可用 `tileNbt` 提供初始化方块实体的 SNBT 字符串。 |
| `source.capture` | 可选。用 `left`、`top`、`right`、`bottom` 分别扩展容器截图的四边，每边 0–512 个 GUI 逻辑像素，默认 0。裁剪范围须能完整放入实际游戏窗口。 |
| `sandbox.inventory` | 可选。对象的键是玩家物品栏编号，0–8 为快捷栏，9–35 为背包；开局选中 0。物品写成 `{"item":"minecraft:coal","count":1,"meta":0}`。`count` 默认 1，范围 1–127；`meta` 默认 0，范围 0–32767；可选 `nbt` 为 SNBT 字符串。 |
| `sandbox.neighbors` | 可选。最多放六个与机器面相邻的方块。每项填写 `block` 和相对位移 `x`、`y`、`z`，省略的轴为 0，恰有一轴取 1 或 −1。每面只放一次，可附 `meta`、`tileNbt`，例如在机器东侧放能源方块。 |
| `sandbox.actions` | 按 `tick` 升序排列 1–2048 个动作。录制从 0 开始，最迟到 23999 tick。`tick` 是最早执行时间，同 tick 按数组顺序逐个执行。等待期间机器继续运转，后续动作等条件满足才开始。外层 `show_gui_demo.duration` 要给加工和结果展示留出时间。 |

| 动作 `type` | 参数和效果 |
| --- | --- |
| `activate`、`close` | `activate` 用主手右键点击机器北面并等待界面打开。`close` 关闭当前界面。要验证设置持久化，可在两者之间修改设置，再重新打开检查。 |
| `click` | 用 `slot` 点当前容器槽位的中心，或同时指定 `x`、`y` 点控件。`button` 为 0 左键、1 右键、2 中键，默认 0。容器坐标从主面板左上角起算，普通屏幕从屏幕左上角起算。最小值分别为 `-capture.left`、`-capture.top`，最大值均为 4095，落点还须在屏幕内。 |
| `wait_screen` | `screen` 填实际 GUI 类的简单名或完整类名，区分大小写，例如 `GuiFurnace`。适合在按钮点击后等待子界面打开。 |
| `wait_slot` | `slot` 是当前容器编号（0–4095，且容器中须有该槽位）。`stack` 填物品对象，留空槽用 JSON 的 `null`。客户端和服务端必须在同一窗口观察到匹配的物品、数量、元数据和 NBT。例：熔炉界面的槽 3 对应玩家背包编号 9。 |
| `wait_nbt` | `nbt` 填非空 SNBT 复合标签，核对服务端目标机器的方块实体。只列所需字段，保持标签类型，例如铭刻器示例的 `"{outputSides:2}"` 匹配整数值。 |

三个 `wait_*` 动作均可指定 `timeout`，默认 100 tick，范围 1–2400。实际界面使用当前启用的资源包顺序、语言和 GUI 缩放；文件形式的服务器资源包也会加入录制。换机器时，先用 `activate` 和 `wait_screen` 验证入口，再加入一组点击及对应的槽位或 NBT 检查。录制失败可查看实例内 `ponder-gui-cache/last-error-<场景定义哈希>.log` 和 `logs/latest.log`，依失败动作编号核对容器槽位、物品、供能及等待时间。

## 手动关键帧示例：原版熔炉

1. 将 `config/ponder/ponder_demo/vanilla_furnace.json` 复制到游戏实例的同名路径。原版熔炉示例只需这个配置文件。
2. 进入单人世界执行 `/ponder reload`，再执行 `/ponderui ponder_demo:vanilla_furnace 0`。也可用 `/ponder compile ponder_demo:vanilla_furnace` 检查场景索引。
3. 等看到燃烧火焰和半满的烧炼箭头，点击暂停。观察数秒，输入槽中的铁矿石和箭头应保持原样。
4. 向前拖到输出槽出现铁锭，再向后拖到装煤阶段。回退时输出槽应清空，铁矿石和煤炭重新出现，烧炼箭头归零。
5. 点击 `R` 重播按钮。熔炉应从空槽开始，依次填入铁矿石和煤炭、显示燃烧和烧炼进度、产出并取走铁锭。

以下时间以场景开头为起点，按每秒 20 tick 换算。

| 时间 | 界面状态 |
| --- | --- |
| 约 3.5 秒 | 铁矿石已放入，煤炭尚未放入。 |
| 约 5 秒 | 输入槽有铁矿石，燃料槽有煤炭，输出槽为空。 |
| 约 8.5 秒 | 燃料已消耗，火焰点亮，烧炼箭头约半满。 |
| 约 12 秒 | 输入槽为空，输出槽显示一枚铁锭。 |
| 约 14 秒 | 指针取走铁锭，输出槽恢复为空。 |

该场景的 `show_gui_demo` 内联定义通过 `source.type: "block_gui"` 打开原版熔炉界面。在关键帧中，`slots` 使用原版熔炉容器槽位编号 0（输入）、1（燃料）和 2（输出）。`properties` 使用容器属性编号 0（剩余燃烧时间）、1（总燃烧时间）、2（已烧炼时间）和 3（所需烧炼时间）。场景 JSON 直接安排状态变化，所以每次重播都按相同时间展示。

## 手动关键帧示例：AE2S 铭刻器与输出侧面（可选适配器）

1. 在安装了 Applied Energistics 2 Supergiant 1.1.2 和 Ponder Legacy 的客户端中，将可选的 Ponder AE2S Preview 适配器 JAR 放进 `mods`。从源码构建适配器时，在仓库根目录执行下面的 PowerShell 命令，替换 Ponder 开发版 JAR 路径。从本地指定 AE2S JAR 时，在命令末尾追加 `"-Pae2sJar=<AE2S 1.1.2 JAR 路径>"`。

   ```powershell
   .\gradlew.bat -p examples/gui_demo/optional/ae2s assemble "-PponderDevJar=<Ponder 开发版 JAR 路径>"
   ```

   构建产物位于 `examples/gui_demo/optional/ae2s/build/libs`。
2. 将 [ae2s_inscriber_subgui.json](optional/ae2s/config/ponder/ponder_demo/ae2s_inscriber_subgui.json) 复制到游戏实例的 `config/ponder/ponder_demo`。
3. 进入单人世界，执行 `/ponder reload`，再执行 `/ponderui ponder_demo:ae2s_inscriber_subgui 0`。
4. 主界面装入硅压印模板和硅。叠层出现后第 110 tick 切换到 AE2S 的输出侧面界面，第 225 tick 返回主界面，模板与硅仍在原槽位。第 270 tick 显示压印硅，第 330 tick 取走压印硅。可以在两次切换前后暂停或拖动时间轴，核对对应的界面和槽位。

`source.factory` 和 `screens.output_sides.factory` 引用适配器注册的界面工厂。`source.tileNbt` 开启自动输出，使铭刻器主界面显示输出侧面按钮。子界面的 `capture.top` 包含超出界面顶边的返回按钮。

## 资源包熔炼工作台

示例适用于安装了含 GUI 演示功能的 Ponder Legacy 的 Minecraft 1.12.2 客户端。场景用原版熔炉、铁矿石、煤炭和铁锭，界面背景随资源包提供。

1. 将示例的 `config` 和 `resourcepacks` 合并到游戏实例同名目录。场景文件位于 `config/ponder/ponder_demo/smelting_station.json`。
2. 在游戏的「资源包」菜单启用 **Ponder GUI Demo**。
3. 进入单人世界，执行 `/ponder reload` 或在启动客户端前完成安装。
4. 执行 `/ponderui ponder_demo:smelting_station 0` 打开 **Scripted Smelting Station**。也可用 `/ponder compile ponder_demo:smelting_station` 检查场景索引。

界面出现后约 2 秒内会填入两份铁矿石和一份煤炭。随后指针按下 **Start**，进度条依次更新，输出槽先显示一锭，再显示两锭。约 6.5 秒时指针切到 **Results** 标签，最后指向输出槽。约 8 秒时场景关闭叠层。点击播放按钮暂停或继续，拖动时间轴跳到任意进度，点击 **R** 从头重播。

场景的 `show_gui_demo` 步骤通过 `ponder_demo:smelting_station` 引用资源包中的 `assets/ponder_demo/ponder/gui_demos/smelting_station.json`。该文件定义面板尺寸、背景、控件及相对时间轴。每个关键帧可更新指定控件的状态，或指定指针坐标、目标控件和按压状态。场景后续的 `hide_gui_demo` 通过同一个 `id` 关闭演示。

## 自定义界面

- 用 `width` 和 `height` 设置逻辑尺寸。`background` 引用资源包的 PNG，可以省略。
- 在 `widgets` 中定义控件的 `id`、类型、位置和初始状态。类型包括 `slot`、`button`、`tab`、`progress`、`status` 和 `highlight`。槽位用注册 ID、`count` 和 `meta` 指定物品，`item: null` 清空槽位。
- 在 `keyframes` 中用相对 `tick` 指定时间，`updates` 按控件 ID 更新状态，只修改本帧提供的字段。`pointer` 可用 `target` 指向控件，也可用 `x` 和 `y` 定位。`pointer: null` 隐藏指针。
- 每个场景都可以用 `show_gui_demo` 引用同一个资源 ID，也可以直接把定义对象写在 `demo` 字段。修改资源包后刷新游戏资源，再重开场景或执行 `/ponder reload`。
