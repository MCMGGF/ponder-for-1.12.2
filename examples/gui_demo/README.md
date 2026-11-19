# 脚本化熔炼工作台

示例适用于安装了含 GUI 演示功能的 Ponder Legacy 的 Minecraft 1.12.2 客户端。场景用原版熔炉、铁矿石、煤炭和铁锭，界面背景随资源包提供。

1. 将示例的 `config` 和 `resourcepacks` 合并到游戏实例同名目录。场景文件位于 `config/ponder/ponder_demo/smelting_station.json`。
2. 在游戏的「资源包」菜单启用 **Ponder GUI Demo**。
3. 进入单人世界，执行 `/ponder reload` 或在启动客户端前完成安装。
4. 执行 `/ponderui ponder_demo:smelting_station 0` 打开 **Scripted Smelting Station**。也可用 `/ponder compile ponder_demo:smelting_station` 检查场景索引。

界面出现后约 2 秒内会填入两份铁矿石和一份煤炭。随后指针按下 **Start**，进度条依次更新，输出槽先显示一锭，再显示两锭。约 6.5 秒时指针切到 **Results** 标签，最后指向输出槽。约 8 秒时场景关闭叠层。播放按钮可重新演示这一流程。

场景的 `show_gui_demo` 步骤通过 `ponder_demo:smelting_station` 引用资源包中的 `assets/ponder_demo/ponder/gui_demos/smelting_station.json`。该文件定义面板尺寸、背景、控件及相对时间轴。每个关键帧可更新指定控件的状态，或指定指针坐标、目标控件和按压状态。场景后续的 `hide_gui_demo` 通过同一个 `id` 关闭演示。
