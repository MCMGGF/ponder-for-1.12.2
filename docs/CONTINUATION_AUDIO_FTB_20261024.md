# 音频取证、渲染隔离与 FTB 续作验收

## 后续安装状态

2026-10-24 23:14（Asia/Hong_Kong），用户明确要求换成候选包。本次已仅将原实例 Ponder JAR 从 0.2.1 换为本报告的 6148／0.2.2-beta 候选，安装后唯一启用版本及哈希独立核验通过。旧版备份、安装收据和回滚说明位于 `C:\CODEX\Ponder-MMCE-Integration\delivery\install-ponder-022-20261024-231444-981`。未改任务、配方、世界或其他模组，未装 QA 探针，未启动游戏。详情见 `docs\INSTALL_CANDIDATE_20261024.md`。

**此更新只改变安装状态，不改变剩余验收结论。以下报告在本次安装前形成，其中“原实例仍 0.2.1／未部署”保留为当时的历史状态；不可将它们解读成当前 mods 仍未更新，也不改写历史 JSON 收据。**

日期：2026-10-24，Asia/Hong_Kong。先完成离线修复；用户随后正常保存退出，复核已无游戏进程后，再执行少量定向隔离检查，没有控制桌面或覆盖原实例。原实例进程 PID 68420 只属于先前盘点记录，不能作为当前进程状态。MMCE 继续采用少量抽样，不补跑旧全量清单。

## 当前生产候选与验收范围

- 版本：`0.2.2-beta`，857,372 字节。
- SHA256：`61483C8BC9397843CC2ECDCD8295C220DA9DF6E1A52ED1622EDB69868EA6BA7F`。
- 完整离线构建：78 个套件、603 项测试，失败、错误、跳过均为零。
- 收据：`C:\CODEX\Ponder-MMCE-Integration\validation\verified-outline-20261024-113355-687\receipt.json`。
- 收据列出的 94 份归档文件及 330 项源码已由主代理独立复验；94 不包含收据自身。

6148 已取得下面默认 fixture 和最终八场及 FTB 抽样的有限像素通过证据，修复了此前四目标缺图的固定光照问题。4722 单课 FTB 的独立有限通过仍属于其原哈希，1288、EE、F7 等历史失败和运行结果也不跨哈希转记。原实例仍为 0.2.1，未写入；八个单时刻不等于完整时间线，其他分辨率与生命周期门槛没有用编译成功替代。

最终收尾记录：`validation\continuation-checkpoint-20261024-61483c8b.json`，SHA256 `53208C06C80DC03C2C0E8A0B11D01936B348B28D7B4228C09DC52ECA5BA0EA91`。6148 共两次原生运行、17 张保留 PNG（fixture 默认与五诊断共 6 张，八场及 FTB 共 11 张）；五张诊断图不算产品默认画面通过。两次均正常退出，收尾核对无游戏进程，94 份归档／330 项源码再次一致，原实例 0.2.1 JAR 哈希仍为 `7117170AEBCB48E5AC2B8D95BDC4FA00346451B8F7F6F651ADAB15953882EC06`。`allOriginalPlanAcceptanceComplete=false`、`installedOriginalInstance=false`；不宣称当前原世界与旧冻结副本等价，不追加启动或部署。

## 6148 默认四目标修复复核

4722 的单变量诊断已完成：仅关闭实际四目标上传的固定 lighting，即恢复带原贴图的玻璃、水、熔炉和石砖；只关 depth、diffuse 或 lightmap 不能等价恢复。代码证据是 BLOCK 顶点格式没有 NORMAL，却继承 GUI 固定光照。报告 `validation\native-split-fixture-review-20261024-4722001d.json`，SHA256 `8DF4629BCA806741CEFB0682C5EEC743AF73028C91E89A4BF96B0F700C61D33A`。这不是纹理图集损坏的结论。

生产新增 `PreviewBlockLighting`，只在 baked BLOCK／fluid 上传期间关闭固定 lighting，并在 finally 恢复原始 GL 与 Minecraft 缓存状态；保留正式深度、diffuse 贴图和 lightmap，不把未贴图诊断画面作为修复。7 项回归纳入当前 603 项测试。

6148 运行 `validation\p022-native-20261024a\validation-output\native-qa-20261024-033625-054-1693c9a6` 已正常保存退出，exit 0，无强制终止，probe 与 runner 通过。主代理查看任何诊断 override 之前保存的默认 PNG，确认四目标、底板、人物和前景内容可见，四目标保留原贴图；默认深度及两种纹理仍启用，只有 BLOCK 上传窄范围固定 lighting 关闭。五种诊断和 uploader observer 均恢复，诊断图片不计产品通过。

默认 PNG SHA256：`6694BB60F947FF77564877248F573638E6364024439D330876BCC06CE6F32611`。复核报告 `validation\native-litfix-review-20261024-61483c8b.json`，SHA256 `B748B0EC5DA2A25FDAB5560074D1CE669FB8432AF33FC75800FFDF55E2DD13D6`。范围仅 1500×800、GUI2、该 fixture 的 `T+55` 默认帧，不代表全部时间线、全部世界或已经允许部署。

## 6148 八场及 FTB 最终定向抽样

运行：`validation\p022-native-20261024a\validation-output\native-qa-20261024-034042-711-9e0224d0`，生产为当前 6148，probe 为 `AABBAD5F59418D9F4B069707A68CB6B95CFB70F8BA8767519882549DAC4D6277`。实际进入既有世界副本，八场自动检查及一次工作台 FTB 往返通过，正常保存退出，exit 0、`forcedTermination=false`。实际注册／编译均为 314 个入口，仅打开八个不同场景；其余 306 个入口不计实机通过，本轮没有打开 MMCE。

六课取样点分别为 building T+84、crafting T+52、smelting T+54、logistics T+152、redstone T+56、rails T+230，另有旧 GUI T+174、红石矩阵 T+60。八张默认场景图及三张 FTB 图共 11 张由主代理与两位独立代理分工实看，有限画面通过。**rails 的 T+230 尚未生成人物放置的矿车，因此只能计铁轨／人物／信息框该帧通过，不能计矿车模型或后期运行通过。**

原生逻辑能力按适用场景统计：重播 8／8、步骤 7／7（1 项不适用）、皮肤切换 6／6（2 项不适用）、分页 4／4（4 项不适用）、GUI 目标健康 4／4（4 项不适用）；八场关闭后引用／资源清理检查通过。暂停、精确 seek 与倒退往返均通过。这些是原生逻辑调用检查，不是物理鼠标／键盘输入，也不证明完整时间线无问题。

FTB before／after 的“查看演示：工作台”按钮完整可见，中文正文无明显裁切或重叠；viewer T+20 中人物持工作台、底板、前景说明与引线可见。返回 quest `ac7b7795`、chapter `c9d9d27c`；七组 scroll 一致，只有 quest-map 为非零 `(1150,814)`，其余六组为零。347 项任务键、progress、complete 及已领奖集合无差异，本课 `6b532c01` 保持 0／false。footer 前景 depth test／write 关闭，alpha test 开启；before／after 全部 17 个已记录字段相同，viewer render-failure keys 为空。仍只抽查一课共享逻辑入口，不能推广成其他五课任务入口或所有非零滚动类型通过。

- FTB before SHA256：`1341EFD673731BAD09DE45E37DF6344562A7738E96AB72EF34E9B13331BE85B1`。
- FTB viewer SHA256：`2B300A723F4930BC2619C5E3868A8FF32017056195C23AD71E253E0BD53056E6`。
- FTB after SHA256：`8206CA5E6E98FD4374172506ACF49DA2275CE3C0FAE7721F4AD1FA04CA431863`。

独立复核：`validation\native-vanilla-review-20261024-61483c8b.json`，SHA256 `85CD7CC5DD2CF7DE466A8C62C073C6F010D02A11322F3CB9E7E84CB878B30EDE`。范围仅 1500×800、GUI2、强制 Unicode 字体下的八个单时间点和单课 FTB 往返，不是完整产品验收，不允许据此部署。

## 历史 4722 单课 FTB 原生复核

运行：`validation\p022-native-20261024a\validation-output\native-qa-20261024-032134-063-8475ba0c`。生产为 `4722001D5A37D3E75EF412072A659360E3360AF64BB3E16291E9A49FF9C6EABF`（854,840 字节、77 套件／596 项测试；收据 `verified-outline-20261024-111644-205`，93 份归档／328 项源码复验），probe 为 `D9F9A6EFC12D8B4D6E05F51ECCE559871FC8E34AADE6993F6D04784848466546`。隔离客户端实际进入世界副本、抽查工作台场景及一次 FTB 往返后正常保存退出，exit 0；probe、runner 和 FTB 自动结果通过，没有强制终止。

主代理和独立复核代理分别查看三张真实 FTB before／viewer／after PNG 并重算哈希：任务页底部的“查看演示：工作台”按钮现在完整、清晰可见，不盖住正文，中文没有裁切；viewer 中人物、手持工作台、外置前景说明与引线可见；退出后恢复原任务页。此图为场景 `T+20`，不覆盖后续合成容器画面。

- before：`2B1CFFF814B026A5DFAB2D7033FD547F7F9351DDCBDEE2D7D6DAF51CEB321E79`。
- viewer：`36E019251C8DC994DE70E04E2101F9B61AAA70435A2AD6CFBA6B9E2747D08459`。
- after：`16BDC0036DFC152BB010945F6933BF155766FBF876E0F5A4CD432A9BC21DD1CE`。

返回 quest `ac7b7795`、chapter `c9d9d27c` 未变；七组实际滚动位置一致，其中任务地图的非零位置为 `(1150,814)`，其他六组为零。347 项已同步任务的键、progress、complete 全部相同；本课手动任务 `6b532c01` 保持 `progress=0`、`complete=false`；已领奖集合未变。不能由其余六组零位保持推论所有类型的非零滚动均通过。

新增 footer 屏幕前景 pass 复用现有渲染状态与矩阵保护，不继承 FTB 的深度、裁剪或物品绘制状态。实际诊断记录显示 foreground 的 depth test／depth write 关闭；alpha test 开启；结束后 before／after 所有已记录字段完全相同，包含矩阵、viewport、scissor、纹理单元／绑定和原有深度／alpha 状态。viewer render-failure keys 为空。状态记录与成功画面支持本次窄修有效，不把某一个状态未经单变量验证地认定为唯一根因。

独立复核：`validation\native-ftb-review-20261024-4722001d.json`，SHA256 `D4CCB4783204E52078F249A9B5CDF03AF979AB374A0D430F730A302AD5F68654`。有限通过范围是 1500×800、GUI 2、强制 Unicode 字体下的工作台单课按钮可见、共享逻辑点击往返、人物与前景说明。不是物理鼠标／键盘输入通过，不是其他五课、其他窗口／缩放或全部产品视觉通过。

## 1288 候选本轮原生复核

11:03:29 至 11:06:57，隔离客户端 PID 30452 实际进入既有世界副本，三场 GUI／红石矩阵／工作台各截取一个活动时间点，并完成一次 FTB 工作台任务往返，随后正常保存退出，exit 0、未强制终止。音频后端连续两次健康，未复现前轮 OpenAL 上下文／空音源表故障。测试不修改原实例，不重跑 MMCE。

运行：`validation\p022-native-20261024a\validation-output\native-qa-20261024-030325-172-0f35da15`。生产哈希为下面的 1288DCE0；probe 哈希为 `441DBEE4B6709BEC65FAA23AF4E220F20EE262188D30196AB21BCD99A7763242`，绑定源码快照及实际安装身份已独立核验。

自动结果通过，但**不是整体视觉通过**：六张 PNG 均独立重算大小及 SHA256。三张模型图中，矩阵提示框在采样时不再挡住周边线路；GUI 三个实际目标与立体轮廓可见；工作台人物和前景说明正常。FTB 逻辑路径返回原任务／章节、七组滚动位置（任务地图为 1150、814，其他为零）、347 项已同步进度及奖励集合均未变。

人工查看 FTB before／after 发现缺陷：记录中的“查看演示：工作台”按钮在实际画面中不可见，尽管共享逻辑点击成功打开了 viewer。因此该 1288 运行的 FTB 像素门槛判失败，不部署。此后已完成 footer 窄修，并以 4722 重新取得上述单课证据；1288 历史失败保留，不追记为通过。

独立复核：`validation\native-directed-review-20261024-1288dce0.json`，SHA256 `5E319ABE895E4F969023244B0A141FFF59CECB71AC8E12E9757A4C10A22A2810`。三个场景之外的 311 个注册入口没有实机打开；三帧不代表完整时间线或其他窗口倍率。下面离线段落为此次运行之前的检查点。

## 1288 离线历史检查点

- 版本：`0.2.2-beta`，847,715 字节。
- SHA256：`1288DCE034EB9F9188D9535C69F84F3ED2BE9DBC08CB0F3C57D8842D7339D3A7`。
- 完整离线构建：76 个套件、590 项测试，失败、错误、跳过均为零。
- 收据：`C:\CODEX\Ponder-MMCE-Integration\validation\verified-outline-20261024-104752-134\receipt.json`。
- 收据列出的 92 份归档文件及 326 项源码已由主代理独立复验；92 不包含收据自身。构建过程中源码无变化。

以上是 1288 运行前的离线检查点，当时尚无该包的原生截图或进入世界证据；后续数据已另列于“1288 候选本轮原生复核”。这不是当前 6148 的版本、测试数量或验收状态。F7、276C、EE 的历史结果没有转记到 1288。

## 音频故障的已知原因与边界

只读取证确认，EE 定向复核那次客户端崩溃的直接异常是声音更新链中 `Library.getSources()` 返回 null，不是 Ponder 预览渲染栈。更早的 OpenAL 上下文初始化失败发生在模组加载阶段，早于 QA 初始化、进入世界和打开任何演示。

此前关于“先退出世界、后声音异常，可能还有独立的退出原因”的推论已修正：已安装 LoliASM 的崩溃恢复会先清理世界，再打印客户端异常。该日志顺序不能证明另有一个世界断开根因。具体音频设备、驱动或兼容层为何未能建立 OpenAL 上下文，现有证据仍不能确定。

证据及时间线：`C:\CODEX\Ponder-MMCE-Integration\validation\audio-preflight-audit-20261024\README.md`，相关输入和字节码证据哈希见同目录 `receipt.json`、`report-receipt.json`。没有禁用音频、修改 MMCE 配方或向发布模组加入未经证实的声音兼容补丁。

测试探针增加只读音频健康前置检查：有限等待初始化，检查声音管理器、实际库和 source map；空 map 合法，null 不合法，不以单条 `Sound engine started` 日志判健康。失败应保存前置条件失败结果，不进入世界。探针接入在离线检查点时尚处准备阶段；后来 1288 与 4722 的上述运行均通过实际前置检查并进入世界，未复现该次音频故障。未复现不等于已经查明并修复设备／驱动／兼容层根因。

## TESR 状态隔离修复

确认旧 `renderTileEntityPreview` 只保护 ModelView，第三方 TESR 可泄漏 Projection、Scissor 和 Viewport，影响后续渲染。新增 `PreviewNativeRenderGuard`，复用现有 GL 状态和客户端数组恢复，并保护投影／模型视图矩阵及裁剪／视口；正常、异常和嵌套路径均恢复。没有误用 2D overlay，也没有关闭正式 3D 深度测试。

新增 6 项回归已包含在历史 590／596 项及当前 603 项中：正常污染、运行时异常、LinkageError、ModelView 捕获失败、矩阵恢复失败、嵌套恢复。它们验证离线恢复契约，不代替真实第三方 TESR 画面验收。

这项修复不能当作四目标缺图根因。历史 F7 fixture 的玻璃、水、熔炉和石砖均已提交非空顶点及有效 alpha，实际上传时 shader 为 0、数组缓冲绑定为 0，关闭剔除也未改善；这些目标的矩阵处于裁剪范围内。后续只围绕同一 fixture 定向诊断，不重测全 MMCE。

## 历史 1288 单 fixture 诊断与 4722 单变量追加检查

1288 运行 `validation\p022-native-20261024a\validation-output\native-qa-20261024-031418-798-b73495a0` 已正常退出、自动诊断通过，并保存同一相机、同一暂停时刻 `T+55` 的三图。四个实际目标上传均被观察到，两种诊断 override 均应用和恢复，原生产 uploader 已恢复，缓冲内容没有修改。

主代理查看三图后的结论：默认图仍缺玻璃、水、熔炉和石砖的模型；仅对四目标关闭深度测试没有恢复模型；同时关闭四目标的 diffuse 纹理、lightmap 纹理和 lighting 后，可看到未贴图的四目标几何。它只说明此组合阶段对表现有影响，未隔离是哪一个变量，不能宣称“纹理根因已查明”，更不能发布关闭贴图、光照或深度的补丁。诊断自动通过不等于默认模型视觉通过。

独立复核：`validation\native-fixture-review-20261024-1288dce0.json`，SHA256 `725489C6D42ADCD6FFBDDEA734CB6A762716A7367F7506CE093E98C75746C4F5`；记录仍为 `rootCauseEstablished=false`、`defaultVisualAcceptance=false`、`deploymentAllowed=false`。

4722 的三个单变量追加诊断位于 `validation\p022-native-20261024a\validation-output\native-qa-20261024-032638-693-46f7d262`，已正常保存退出并完成独立图片复核，结果和 SHA 见上面的 6148 修复依据。它建立的是固定 lighting 窄问题证据，4722 默认画面本身仍不通过；不改写 1288 的 `rootCauseEstablished=false` 历史记录，也不把诊断自动通过转为产品通过。

## FTB 隔离合并与验收边界

原版六课章节 `c9d9d27c` 已通过事务合并到 `C:\CODEX\Ponder-MMCE-Integration\validation\p022-native-20261024a`，不是原实例。事务：`C:\CODEX\Ponder-MMCE-Integration\validation\transactions\p022-native-20261024a-20261024-023844-212-a1b5f58b\transaction.json`。

安装后结构审核通过：9 个输出、13 个固定 UID、6 个手动勾选任务及两组各 6 个稳定场景链接；保留旧章节顺序、原文件和链接策略，不因观看完成任务或发奖励。16 项离线安装审核回归通过，包括输出变更、UID 冲突、自动完成、奖励、链接错误和阶段收据等拒绝路径。

证据：`C:\CODEX\Ponder-MMCE-Integration\validation\audit-vanilla-baseline-20261024-023949-846.json`、`C:\CODEX\Ponder-MMCE-Integration\validation\installer-audit-vanilla-test-results-20261024-023949-846.json`；说明见 `C:\CODEX\Ponder-MMCE-Integration\docs\VANILLA_FTB_DELIVERY.md`。

FTB 返回生命周期与公共逻辑点击回归包含在历史 590 项中；随后新增 6 项前景渲染作用域回归，包含于历史 596 项和当前 603 项。该段最初离线检查时尚未实测任务往返；现已分别由上述 4722 及 6148 工作台单课运行验证各自实际按钮可见、公共逻辑点击、返回状态和本轮进度不变。其他课任务入口与其他窗口条件未验收，准备或编译没有被当作通过。

## 工具验证独立统计

以下离线工具检查不并入当前生产 603 项，也不是 Minecraft 运行记录：

| 工具检查 | 结果 | 证据 |
| --- | --- | --- |
| Native 音频健康检查 | 14 项通过 | `C:\CODEX\Ponder-MMCE-Integration\validation\native-qa\test-output\audio-health-20261024a\test.log` |
| Runner 当前结果读取契约 | 67 项通过，另有 6 项选项守卫通过 | `C:\CODEX\Ponder-MMCE-Integration\validation\native-qa\test-results\runner-result-contract-20261024-031208-337-adf254f2\report.json` |
| Runner 历史结果读取契约 | 当时 47 项通过，已由上项扩展 | `C:\CODEX\Ponder-MMCE-Integration\validation\native-qa\test-results\runner-result-contract-20261024-025414-880-6ef7993f\report.json` |
| 实际 Gson 2.14.0 空值序列化 | 6 项通过 | `C:\CODEX\Ponder-MMCE-Integration\validation\native-qa\test-results\gson-null-contract-20261024-025320-914-3cea7282\receipt.json` |
| FTB 安装后审核工具 | 16 项通过 | 上述 `installer-audit-vanilla-test-results-20261024-023949-846.json` |

Gson 会省略 null 元数据字段。Runner 已改为安全读取缺失属性，允许保留音频前置失败记录，同时不能将失败提升为成功；成功的指定场景结果仍必须有匹配抽样身份。最新 67 项加 6 项选项守卫报告 SHA256 为 `AFC582C22BABACA97958D2FF04915A634F36CFC3D0AD00542AB6FC935C43F836`。较早 45 项及暴露问题的中间 47 项记录保留，不覆盖为通过；`RUNNER_CONTRACT_20261024.md` 中的 47 项是当时历史检查点，不是当前最终统计。

## 后续未完成

6148 默认四目标原贴图、八场单时刻及 FTB 工作台单课往返已有限通过，不再将固定 lighting 缺陷写成当前根因未知，也不重复把这些检查写成全部未执行。仍待后期包含矿车的铁轨时间点、新建世界，以及原计划按最终候选完成的 3 次冷启动、3 次退出重进、20 次演示开关和多分辨率／GUI 缩放／物理输入。八场本次关闭清理已通过，不等于长时间或反复开关资源稳定性全部通过。更广范围真实世界／背包／任务进度无变化仍需相应证据，不从本次有限方块、347 项进度及单课界面观察推论全部情况；不以全量 MMCE 为门槛。

关键验收未通过前不覆盖原实例。最终交付仍须在关闭游戏后重新备份并校验当时 JAR 和任务配置，合并安装、只启用一个 Ponder 版本，保留回滚记录。
