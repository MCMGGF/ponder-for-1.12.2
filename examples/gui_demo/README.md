# 脚本化熔炼工作台

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
