# 进入世界故障证据复核

复核日期：2026-10-11，Asia/Hong_Kong。本文仅复核已经存在的日志、线程转储、
原实例 JAR 字节码及当前项目源码；未启动或操作游戏，未改动实例、模组、存档，
未运行 Gradle，也未增加替身测试。

后续补充：本报告正文和哈希表保留较早复核时的历史快照，不代表最新构建。
2026-10-11 随后获用户明确授权启动了无 Ponder 分支，新增三份进入世界后的线程
转储。其过程、再次物理 Esc 停止及验收限制另见
[首轮 P0 实机记录](C:/CODEX/Ponder-MMCE-Integration/docs/P0_NONE_RUNTIME_20261011.md)。
当前候选包及 212 项测试的最新状态以
[实施记录](C:/CODEX/Ponder-MMCE-Integration/docs/IMPLEMENTATION_STATUS.md) 为准。

## 结论

1. **已找到一个历史上明确的进存档失败原因**：2026-10-04 的
   SuperMartijn642 Core Lib 与 DragonCore 对 `SPacketCustomPayload` 的 Mixin
   冲突，继而造成网络类初始化失败。它有同次日志及崩溃报告互相印证。
2. **尚不能证明用户后来报告的卡住与该历史错误同源**，也不能确认当前候选包
   已修好该卡住。历史环境、原实例已安装包、隔离启动包、当前候选包不是同一组产物。
3. **旧隔离启动说明存在事实冲突，必须更正解释**：10 月 10 日的原始日志
   实际记录了集成服务器启动、客户端世界加载及一个无加载界面的渲染 tick。
   但没有可用画面和操作验收，不能据此宣布游戏可用。
4. 原实例旧 Ponder 包保留了全局 GUI 捕获路径；当前源码已经去掉调用并把兼容
   方法改为空操作。这是可验证的风险消除，**不是历史卡死根因已证实**。

## 证据范围

以下路径缩写仅用于表格：

- `P`：`C:\CODEX\Ponder-MMCE-Integration`
- `O`：`C:\Users\Seeds\Desktop\新建文件夹 (4)\新包测试\.minecraft`
- `S`：`C:\CODEX\Ponder-MMCE-Integration\validation\client-sandbox\validation-output\20261010-150642-485`

通过 `rg --files` 清点项目 `validation`、`verification`、`logs` 及原实例日志/
崩溃报告。当时找到的真实游戏线程转储为 `S` 下三份；没有发现顶层
`diagnostics` 目录。读取原实例 `latest.log`、`debug.log`，并在内存中解压检索
21 份 `2026-10-*.log.gz`（10 月 4 日 7 份、5 日 6 份、6 日 7 份、8 日 1 份）。
检查了相关 10 月崩溃报告及项目历史验证说明。没有把旧日志的文件名当作当前运行。

| 材料 | 能证明什么 | 不能证明什么 |
| --- | --- | --- |
| `O\logs\2026-10-04-2.log.gz` 与同日 19:29:43、19:30:11 崩溃报告 | 当时世界连接因网络类加载失败而终止 | 后来所有卡住都是同一原因 |
| `O\logs\latest.log`，文件修改时间 2026-10-09 19:56:30 | 该次客户端和服务器在登录后继续执行 | 用户所指失败尝试就是这次；画面和输入始终正常 |
| `S\stdout.log` | 旧候选包的注册、世界加载、渲染 tick 事件 | 当前候选包或实际操作体验通过 |
| `S\thread-1.txt` 至 `thread-3.txt` | 三个采样点的客户端在帧率等待 | 世界进入阶段不存在阻塞 |
| 当前源码及现存 JAR 的 `javap -p -c` 输出 | 指定文件实际包含的调用路径 | 某路径曾在用户失败现场运行 |

`O\logs\latest - 副本.log`、`debug - 副本.log` 属于 6 月旧副本，本轮只清点，
没有拿它们诊断 10 月问题。项目 `P\logs\latest.log` 的
`Test worker / Injected registry failure` 是 JUnit 故意注入的异常；
`validation\reconcile-test-*` 是合成日志，离线模型检查也不是游戏运行证据。

## 已证实的历史失败

证据：
[压缩日志](<C:/Users/Seeds/Desktop/新建文件夹 (4)/新包测试/.minecraft/logs/2026-10-04-2.log.gz>)、
[崩溃报告](<C:/Users/Seeds/Desktop/新建文件夹 (4)/新包测试/.minecraft/crash-reports/crash-2026-10-04_19.29.43-client.txt:8>)。
下述压缩日志行号指解压后的文本行号。

| 时间 | 行号 | 直接观察 |
| --- | --- | --- |
| 2026-10-04 19:11:37 | 5040、5041 | `supermartijn642corelib.mixins.json:SPacketCustomPayloadMixin` 注入失败；优先级 900 的注入无法进入已被 DragonCore 优先级 1000 合并的方法 |
| 同次会话 | 5311 | `NoClassDefFoundError: net/minecraft/network/play/server/SPacketCustomPayload` |
| 19:29:25 | 5358 | 集成服务器开始启动 |
| 19:29:41 | 6390、6393 | `Netty Local Client IO #0` 因上述类缺失报 FATAL，服务器停止 |
| 19:29:43 | 崩溃报告第 8、29 行 | `EnumConnectionState` 初始化失败；cause 链指向上述 `SPacketCustomPayload` |
| 19:29:55 至 19:30:09 | 7284、7453 | 同一会话再次尝试，随后再次停止；另有 19:30:11 报告 |

崩溃栈明确经过 `Minecraft.launchIntegratedServer`、
`FMLClientHandler.tryLoadExistingWorld`、世界选择入口。受影响的界面是
`GuiWorkingOverride`，但界面名称不是根因；根因证据是其下方网络类初始化链。
该报告环境为 Cleanroom `0.2.4-alpha`、Java `21.0.1`、266 个已加载模组。
报告没有 Ponder 调用栈，Ponder 出现在模组列表不能证明它引发这次异常。

原实例 10 月 9 日日志和 10 月 10 日隔离日志都已经是 Cleanroom
`0.6.13-alpha`、Java `25.0.4.1`。因此不能把旧环境异常直接套到当前环境，
也不能声称仅更新 Ponder 就解决了那个依赖间的 Mixin 冲突。

## 隔离启动记录更正

原始证据：
[stdout.log](C:/CODEX/Ponder-MMCE-Integration/validation/client-sandbox/validation-output/20261010-150642-485/stdout.log:6109)。
当次候选包哈希为 `D450D0A9D26261B1D7CEB9110CC12F21BE61D86C3A2DA29A3ADEA87126A340E2`。

| 时间，2026-10-10 | 日志行 | 观察 |
| --- | --- | --- |
| 23:10:12 | 6045 至 6047 | MMCE 轻量索引 53 台、0 失败、21 ms；尚未编译预览；Ponder 196 条入口、6 标签、67 ms |
| 23:10:15 | 6067 | 一次 Ponder tick 耗时 174 ms；是一次超预算停顿，非持续卡死证明 |
| 23:16:04 | 6109 | 集成服务器启动 |
| 23:16:10 | 6167 | 准备主世界出生区域 |
| 23:16:13 | 6279 | FTB 加载 3 章、125 任务、178 子任务、68 奖励；不是后来安装测试章节后的验收 |
| 23:16:18 | 6323 | 测试玩家登录 |
| 23:16:19 | 6351 | Ponder 观察到客户端世界加载 |
| 23:16:22 | 6410 | Ponder 观察到一个 `world/player != null` 且 `currentScreen == null` 的渲染 tick |
| 23:16:27 | 6427 | 客户端继续记录 `GuiChatOF` 打开 |

[LAUNCHER_FINDINGS.md](C:/CODEX/Ponder-MMCE-Integration/validation/LAUNCHER_FINDINGS.md)
旧版曾写有“没有打开任何存档”，现已依据原始日志更正。同次
[operator-stop.json](C:/CODEX/Ponder-MMCE-Integration/validation/client-sandbox/validation-output/20261010-150642-485/operator-stop.json)
仍写有 `worldEntryAttempted:false`，与原始生命周期记录冲突。
`operator-stop.json`、`result.json` 作为原始运行记录保留不覆写，
以本节说明更正：**当次确实发生了世界进入流程，
但发起者/触发方式及可用画面没有得到核实。**

三份转储时间依次是 **23:13:24、23:14:55、23:15:48**，均早于世界启动。
最后一份甚至仍早 16 秒。每份第 26 行起的客户端栈均为
`Thread.sleep -> Sync.sync -> Display.sync -> Minecraft.func_71411_J`，
状态 `TIMED_WAITING (sleeping)`；均没有 `Server thread`、Ponder 栈帧或死锁报告。
这些只是**进入世界前**的采样，不能排除其后出现阻塞，也不能确认显示器上的画面。

23:16:28 的 `operator-stop.json` 表明 PID 21396 被主动结束；
旧 `result.json` 的 `normal-exit`、退出码 `-1` 不能解释为正常自然退出，
也不能解释为新的游戏崩溃。

## 原实例最近日志

[latest.log](<C:/Users/Seeds/Desktop/新建文件夹 (4)/新包测试/.minecraft/logs/latest.log:6098>)
在本轮读取时对应 **2026-10-09**，并非 10 月 11 日的新尝试。

登录记录之后还有以下客户端事件：

- 19:45:53，第 6407 行：打开 `GuiChatOF`。
- 19:45:59，第 6422 行：打开 `GuiItemExport`。
- 19:46:10，第 6456 行：客户端聊天同步。
- 19:48:24，第 6495 行起：FTB 任务类型活动。
- 19:50:15，第 6504 行：客户端 EMC 导出完成，46,401 条配方。
- 19:56:26，第 6514 行：打开游戏菜单并保存暂停。
- 19:56:28 至 19:56:30，第 6579 行起：服务器停止、保存与清理。

它不只是“服务器说玩家登录了”，确有登录后的客户端进展；仍不足以断言
没有画面卡住、阶段性停顿或另一轮失败。在已检索的 21 份 10 月压缩日志中，
没有找到 `at net.createmod.ponder...` 的真实异常栈帧；这仅表示缺少正向归因，
不是排除 Ponder 的证明。部分归档不含完整启动或完整结束，不能按文件结尾猜根因。

## 其他错误的边界

| 证据 | 分类和限制 |
| --- | --- |
| 10 月 4 日 18:24:13 等崩溃，`mmcenetworks` preInit 缺少 `AnimationFactory` | 可确定为当次初始化依赖故障；不是后来的世界加载线程证据 |
| 10 月 5 日 02:40:04/05，CraftTweaker 缺少 `RecipeCraftingContext` | 同上，应按当时依赖版本组合处理 |
| 10 月 5 日 ZenUtils 的 ITweaker/Logger 未重定向 | 初始化 Mixin 故障，不等同于 Ponder 演示故障 |
| 10 月 6 日 16:38:34，`Invalid id 4096`，Galacticraft 注册流体光栅 | 注册阶段 ID 上限异常，发生在世界进入之前 |
| 10 月 9 日第 6359 行 MMCEComplement/DragonCore 文本格式覆盖被跳过 | WARN，不是 10 月 4 日致命的网络包 Mixin 冲突 |
| 10 月 9 日第 6381 行 OptiFine/Alfheim NPE 警告、6402 行地图目录缺失 | 后续客户端有进展，不能仅凭相邻错误认定全局卡死 |
| 10 月 9 日第 6426 行 ProjectE FATAL | 网络超时发生在 `ProjectE UUID Checker Server` 后台线程，不是 Minecraft `Server thread` |
| 10 月 9 日第 6464 行配方调查失效 | `forge_studio_recipe_probe` 检测文件/设置变化后主动终止调查；有明确错误日志，之后客户端继续执行 |
| 隔离运行 `20261010-143624-*` 的旧 MMCE JAR 补丁目标缺失 | Bansoukou 启动前阻断，详见既有 `BANSOUKOU_FINDINGS.md`；不是最新世界卡住已定因 |
| 隔离运行 `20261010-145728-393` 中文参数编码导致 CrashReport class data 缺失 | 当次验证启动器错误，已另有编码对照证据；不归因于 Ponder |

## 当前源码对照

| 路径 | 当前行为 | 与世界卡住的证据关系 |
| --- | --- | --- |
| [PonderBackportMod.java](C:/CODEX/Ponder-MMCE-Integration/src/main/java/net/createmod/ponder/PonderBackportMod.java:28) | preInit 仅注册插件；loadComplete 才 reload；serverStarting 注册命令 | 世界开始时没有调用场景编译/模型烘焙 |
| [PonderIndex.java](C:/CODEX/Ponder-MMCE-Integration/src/main/java/net/createmod/ponder/foundation/PonderIndex.java:63) | 同步构建新注册表，成功后发布；失败保留旧表；清理 ThreadLocal 和可选模型缓存 | 仍是同步注册，异常隔离不等于阻塞超时隔离 |
| [ClientProxy.java](C:/CODEX/Ponder-MMCE-Integration/src/main/java/net/createmod/ponder/client/ClientProxy.java:143) | END tick；HEI 重试受计数节流；无 player/world 时清队列返回；只有显式请求才开演示 | 没有自动世界进入预览；HEI 反射初次安装仍可能有耗时，174 ms 不能确定具体子步骤 |
| [ClientProxy.java](C:/CODEX/Ponder-MMCE-Integration/src/main/java/net/createmod/ponder/client/ClientProxy.java:200) | world load 只重置诊断状态；remote world unload 释放缓存；render tick 只记录条件满足 | 诊断日志没有像素检查或输入确认，不能充当验收 |
| [FtbQuestsCompat.java](C:/CODEX/Ponder-MMCE-Integration/src/main/java/net/createmod/ponder/compat/ftbquests/FtbQuestsCompat.java:82) | tick 仅处理显式 pendingTarget；非任务书界面快速返回；按钮才打开演示 | 不在普通世界 tick 扫描/编译全部 MMCE 演示 |
| [FtbQuestsCompat.java](C:/CODEX/Ponder-MMCE-Integration/src/main/java/net/createmod/ponder/compat/ftbquests/FtbQuestsCompat.java:378) | GUI 切换时同步检查文件时间/大小，变化才解析，配置上限 2 MiB | 仍有主线程文件访问；没有现场栈证明它造成用户卡住 |
| [MmceMachineIndex.java](C:/CODEX/Ponder-MMCE-Integration/src/main/java/net/createmod/ponder/foundation/content/mmce/MmceMachineIndex.java:185) | 轻量元数据注册；打开机器演示时才展开结构；有限额和协作式耗时检查 | 1.5 秒检查不是可抢占超时，不能终止卡在第三方调用内的线程；该风险属于显式打开预览路径 |
| [MmceStaticModelPreviews.java](C:/CODEX/Ponder-MMCE-Integration/src/main/java/net/createmod/ponder/foundation/content/mmce/MmceStaticModelPreviews.java:85) | 显式外观请求按需解析/烘焙并缓存；普通 world load 不烘焙 | 不把离线烘焙或类加载检查通过当作进世界验证 |
| [mixins.ponder.json](C:/CODEX/Ponder-MMCE-Integration/src/main/resources/mixins.ponder.json) | common、server、client Mixin 列表均为空 | 当前 Ponder 配置没有上述网络包注入；不代表其他模组之间已无冲突 |

### 原实例旧包中仍有的捕获路径

对原实例 `ponder_legacy-0.1.0-beta.jar` 做只读 `javap -p -c`：

- `ClientProxy.onClientTick` 在 player 判空前调用
  `SandboxTriggeredBlockGuiSnapshot.onClientTickAll`；每 tick 还尝试 HEI 安装。
- `ClientProxy.onGuiOpen` 转发到 `onGuiOpenAll`。
- 旧捕获类在 `captureInProgress`/`captureArmed` 时会把非 Ponder GUI 捕获，
  使用 `event.setGui(null)` 或 `displayGuiScreen(null)`，不按目标 GUI 类型严格限定。
- 旧 `render` 只有在 player/world 已存在时才调用 `beginCapture`；
  `prepareServerSandbox` 随后直接访问真实 `WorldServer`。不能仅因全局 hook 存在，
  就认定“全新启动首次进入世界、尚未看演示”必然进入捕获。

当前 [SandboxTriggeredBlockGuiSnapshot.java](C:/CODEX/Ponder-MMCE-Integration/src/main/java/net/createmod/ponder/foundation/ui/SandboxTriggeredBlockGuiSnapshot.java:116)
的两个旧兼容方法均为空，当前 ClientProxy 不再调用它们，预览仅使用隔离且明确
支持的适配器。**旧路径存在、当前源码去除路径这两件事已证实；失败现场是否处于
捕获状态并未证实。** 现存旧 JAR 的哈希也不能回填为每一次历史崩溃的 JAR 哈希。

## 尚缺的现场证据与验收门槛

后续执行计划必须把以下项目保留为未完成，本复核没有要求用户再提供一份日志：

1. 对同一次实际卡住建立明确关联：运行目录、PID、时间、使用的世界副本、
   Ponder/依赖 JAR 哈希，以及匹配该次的日志。不能混用三个不同候选包。
2. 世界启动之后、卡住期间的多次客户端与服务器线程栈及耗时/锁等待证据。
   较早 `S` 中三份转储时间窗口不符合这一条件。后续无 Ponder 分支的三份
   转储位于世界加载后，但未与已确认的画面冻结对应，仍不能代替卡住现场。
3. 在隔离副本上验证首帧、移动、ESC、物品栏、Ponder 打开关闭、FTB 返回及再次
   进入世界；同时确认原存档和任务进度没有被预览改写。
4. 若要证明归因，执行只改变待验证变量的副本对照，记录相同环境下是否复现。
   不通过无依据移除其他模组或改动原存档来“修复”。

当次复核时的构建/112 项单测、离线模型与类加载检查，以及日志中的登录成功，都不替代
以上门槛。**本报告不宣告世界进入故障已修复。**

## 校验摘要

以下为较早复核时读取到的 SHA-256；原始材料未覆写，不将旧候选包哈希当作最新版。

| 文件 | SHA-256 |
| --- | --- |
| `O\mods\ponder_legacy-0.1.0-beta.jar` | `39B36481BB1C6AB4683045D73C50D1CB2F33370618A46310F56CC2ABF358346E` |
| `P\build\libs\ponder_legacy-0.2.0-beta.jar` | `EDDD685E280A3716B793DD9F2EE5090F4FE8A5C4146921D221E590C2A9324D26` |
| `O\logs\latest.log` | `EDE4D5A8CBF1AADFC6FB5A8D873137D390E25E0A87EFDE8E93C99E2169F8CFE0` |
| `O\logs\2026-10-04-2.log.gz` | `17EF79BF8F2B2BA63996798EBE9A44B899C3A09968087AE63E774656FD512792` |
| `O\crash-reports\crash-2026-10-04_19.29.43-client.txt` | `82CDD1FD03E665C370D6538507F131F05CBB1F83D751D6B723312224ED68754C` |
| `S\stdout.log` | `28DFA71CD8743690F325F81EFE7054049D26C312BCF6CFF8D2CBA7C1C2E86239` |
| `S\thread-1.txt` | `6B045FEECDA0959FFAEA96B92739F9B60E1BB2C15B373E2390406B5FBB3113C9` |
| `S\thread-2.txt` | `35D1A505B1D90A103C851CACE1615BAAD6741BB52002914471C8B3C4E8D8B443` |
| `S\thread-3.txt` | `109717E3AED2E3DCF0E299C2A8DE676BCD82AC5F029889B535495AD8A0AECB8E` |

当前源码锚点（均位于 `P\src\main\java\net\createmod\ponder`）：

| 文件 | SHA-256 |
| --- | --- |
| `PonderBackportMod.java` | `BC9FE5300858036C69A123EF8FCE8EEC13FD23FC4804F980EA298ECE34931B5E` |
| `Ponder.java` | `D502D1C25B3095B89CCAEC028AD6D66B2F35546458577B0429EE7CD1ED35E233` |
| `foundation\PonderIndex.java` | `15EAA2CF65C718B94B7851BFC4F77D1BB168242AB432096B6982D411F94F77F2` |
| `client\ClientProxy.java` | `0D5CE5AA1C3F3F28534D5FCC1B0214DDA533FE531EFBB3D65B4144843162F837` |
| `compat\ftbquests\FtbQuestsCompat.java` | `99DCA5E6E50BB8127F0F9B6D3E9CAF3273B521231ADB67A6C2250E6A6A05912B` |
| `foundation\ui\SandboxTriggeredBlockGuiSnapshot.java` | `4F04E8B2BE4C31470F2EB058166A371CE51DDFC871A39142AC0AE01A3E1D18FE` |
