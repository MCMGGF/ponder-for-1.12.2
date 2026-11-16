# 三处旧演示反馈修复

日期：2026-10-25，Asia/Hong_Kong。范围为用户三张截图对应的 `debug_birbs`、`debug_tracking`、`debug_items`，不新增 MMCE 机器，不改任务与配方。

## 已确认原因

- `debug_birbs`、`debug_tracking` 仍调用旧 `createBirb`，走彩色棱柱占位路径，未使用已有真实人物模型。旧 POI 文案也没有对应的真实人物转向。
- tracking 的细长红框是场景明确指定的空间 AABB，不是三维描边引擎退回二维。它不贴合真实方块，且中途缺少解释。
- ITEM 同时画彩色薄块和二维 GUI 物品图标，还有辅助朝向线；旧示例只移动 20 tick，而文字持续更长，并指向静止起点。
- 仅有 actor、没有可见方块的场景被三维渲染入口跳过。

## 本轮修改

三场稳定 ID 保留。前两改为真实人物行走、持物、潜行、挥手与显式转向；tracking 以同 slot 的实际方块选区，在金块、钻石块、离散两块之间切换。抽象 AABB API 及其语义测试保留，不将所有外部 BIRB API 强行解释为人物。

ITEM 改用原版 `RenderItem` 的 `GROUND` 模型，按独立 actor 的位置、旋转、浮动和透明度绘制，去掉二维图标、薄块和方向线的重复表示。物品路线明确持续 72 tick，分步文字区分移动中和到达终点。原版纸片型物品仍使用原版模型，并不虚构成实体立方体。

新增兼容的 `OverlayInstructions.chaseOutline` 和 `WorldInstructions.moveEntity`；前者保存不可变方块选区，后者只记录已知预览物品的时间线，不调用真实实体工厂、交互或网络。原来 `createItemEntity` 的 20 tick 初始 motion 语义不变。

## 离线产物

- 候选 `0.2.3-beta`：865,353 字节。
- SHA256：`5A0929DE9BF67F02FF3848DF64D1EA0E7CF48851C982F3C7FC9F82A092FA45D9`。
- 81 套件、630 项测试，零失败、错误或跳过；归档 `validation\verified-actors-20261025-005833-670`。
- 独立复核 97 份归档文件及 335 项源码；384 个生产 class 均 major 52，无 QA、JUnit 或测试类混入。实际 mixin 仍要求 `JAVA_16`，不声称普通 Forge／Java 8 兼容。
- 独立报告：`validation\independent-actor-build-review-20261025-5a0929de.json`。

## 首次定向实机检查：历史失败

使用已有隔离副本及当前候选，游戏 PID 72976 已正常保存、退出，exit 0。没有桌面控制、强制终止或原实例写入。

运行：`validation\p022-native-20261024a\validation-output\native-qa-20261024-170349-400-ed94f305`。

三场各三点：人物 32／60／128、追随 28／59／102、物品 30／56／100。九张原生 PNG 全部采集并独立实看，人物、方块选区、真实物品与前景说明在这些时刻有限通过；暂停、回退与关闭清理亦有本轮记录。T+59 是最多 6 tick 的追随中间态，不是旧细长框。

**首次整轮为失败，当时禁止安装**：真实测试玩家背包的原始 NBT 文本哈希前后不同。七个附近方块相同；原观察器只保留哈希，无法据此判断真实字段变化或序列化顺序。不得据理论直接翻转结果。其后增加完整 NBT 深拷贝、语义比较、逐槽差异以及 entry／首个预览前／每次关闭／stop 的只读快照，并进行候选不变的定向复测。首次失败结果保留不改。

独立图审 `validation\native-actor-visual-review-20261025-5a0929de.json` 明确 `visualSamplesPassed=true`、`runtimePassed=false`、`cannotInstall=true`。非阻断排版残留：人物 T+32 的句号单独换行；未将全部字体排版计为通过。

## 第二次定向实机检查：有限通过

运行 `validation\p022-native-20261024a\validation-output\native-qa-20261024-172046-202-afa309a0`，生产候选与首次相同，探针更新为 `8B7C1474B3B6896282B9F9131F1A464A03B285B49E3775FDC02B46CD3B489ED0`。PID 72688 正常保存退出、exit 0、无强制终止；实载候选哈希与归档一致。

- 三场指定九点全部采集；第二轮九张新图全部重新独立实看，没有挪用首轮图审。
- 12 个观察点的完整 Inventory NBT 深拷贝均与原始入口、前一次相等，31 个槽位、所有标签、类型与列表顺序均保留；原始文本亦全部相同，槽位差异为空，七个附近方块不变。
- 独立使用 Minecraft `CompressedStreamTools` 读取 12 份压缩 NBT，再做完整相等与 typed JSON 逐字段核对；24 个快照／详情文件均重新核对长度和哈希。
- 九次关闭均清理预览资源。抽样控制路径包括暂停、正反向定位、重播、步骤及适用人物的皮肤切换；不等于真实物理鼠标的完整验收。
- 新图审：`validation\native-actor-final-review-20261025-5a0929de.json`，SHA256 `3E32C07FC022BD1198F2DFBB50381E747A0C532DC70BBB08ABEF10E3D9CE02BB`。
- 独立库存核验：`validation\inventory-review-20261025-native-afa309a0\review.json`，SHA256 `C18AC0B2D2F95A55D1EF4B4FA8409A6F51877BA3AF09475DB9AF7C3B160DDDC2`。

**此次没有复现背包变化，但不证明首次仅有键序差异。** 首次没有完整快照，原因仍未归因，失败记录不改写。新比较器另有真实 NBT／ItemStack 的 33 项离线检查，任何已观察中间语义变化仍永久判失败，不重置基线，不筛掉模组标签。检查范围是序列化库存列表，不包括所有运行时字段或每个瞬间。

## 未验收与原实例

01:30 通过本轮定向门槛后，仅 JAR 安装事务已提交。原实例启用 `mods\ponder_legacy-0.2.3-beta.jar`；旧 `0.2.2-beta`／`61483C8B...68EA6BA7F` 备份在 `delivery\install-ponder-023-20261025-013009-990`，其中有完整收据和回滚说明。预检曾明确拒绝首轮失败证据；实际安装绑定第二轮通过结果与新图审。没有安装 Ponder QA 探针，没有写任务配置、进度、配方、机器、世界或启动器。没有在原实例启动新版游戏。

独立安装审查 `validation\independent-install-review-20261025-5a0929de.json` 确认：启用的 288 个 JAR／ZIP 中只有一个 Ponder，Ponder QA 标记为零；287 个其他模组在收据前、后与当前强制包含隐藏文件的枚举中，路径、长度、SHA256 和修改时间完全一致。旧包的备份与移出副本均与 `61483C8B...68EA6BA7F` 相同，原启用路径不存在。最终 Minecraft 进程为零，没有强制结束其他 Java 程序。

本轮没有覆盖手动旋转、其他窗口倍率、完整时间线、原计划的 3 次冷启动／3 次重进／20 次开关或新建世界；不扩专用服务器、联机或全量 MMCE。旧验收结果各自保留原版本哈希，不转移到新候选。
