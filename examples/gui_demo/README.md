# 脚本化 GUI 演示

## 原版熔炉

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

## AE2S 铭刻器与输出侧面（可选）

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
