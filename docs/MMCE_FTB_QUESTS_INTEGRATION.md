# MMCE 与 FTB Quests 客户端联动

## 当前接口

本联动针对测试实例实际安装的 **FTB Quests 1202.9.0.15 修复版** 和 **FTBLib 5.4.7.2 修复版**。已直接用 `javap` 核对：

- `GuiWrapper.getGui()` 包装 `GuiQuestTree`。
- `GuiQuestTree.getViewedQuest()` 与 `viewQuestPanel`。
- `PanelViewQuest.hidePanel`、`panelContent`。
- `QuestObjectBase.id` 是 32 位整数，配置中使用 1～8 位十六进制 UID，统一补足 8 位。
- `Quest.tasks`、`ItemTask.items`、`QuestObjectBase.icon`。
- FTB UI `Widget.getX/getY/posY/width/height/setHeight`。

没有依赖工作区中 Minecraft 26.1.2 版 FTB Quests 的代码。所有 FTB 类仅通过反射访问，FTB Quests 或 FTBLib 未安装时不注册桥接。

## 玩家使用

打开任务书，点击包含已注册 Ponder 演示物品的任务。任务详情底部出现 **查看演示**。正常关闭后返回原任务详情及滚动位置；若任务书在查看期间重新同步，则先解析当前有效任务，不重新打开已失效的旧对象。

同一任务匹配多个演示时，通过底部左右按钮选择。图标提示显示所选物品名称、当前序号、组件与场景 ID。自动匹配会保留物品元数据和 NBT，交给 Ponder 既有物品匹配器，不仅按物品名称比较。任务打开的查看器限定在所绑定的机器内，同一机器的最小、最大等场景仍可切换；其他机器从任务入口选择，普通思索浏览器仍保留同组机器导航。

已配置但缺失的演示保留灰色入口和具体原因，不会改开另一个场景。组件缺失或旧映射缺少稳定 ID 会立即显示；需要执行 storyboard 才能确认的缺失 ID、重复 ID 和编译错误在第一次点击后显示。错误不会禁用其他可用目标。重新打开任务页或成功重载 Ponder 后可以重新检查。

独立底栏需要 26 个 GUI 像素。短任务在屏幕允许时先扩高并重新居中详情面板，空间不足时才缩小滚动区；无法保留最小内容区时不强行绘制。关闭入口或离开时恢复原生尺寸，不逐帧累加高度。打开 FTB 上下文菜单时暂时收起底栏。若原生界面在绘制中重建子面板，该帧不叠加底栏，也不拦截尚未显示的按钮区域，下一帧重新预留。

该按钮：

- 不要求完成任务，不消耗任务物品。
- 不领取奖励，不调用任务进度接口。
- 不发送服务端命令，不要求 OP。
- 不写任务文件、世界存档或机器配置。
- 不在进入世界时构造演示场景；只有实际点击时才打开查看器。

## 自动与显式映射

默认启用自动匹配，读取当前打开任务的 `ItemTask.items`，其次读取任务图标。不是从奖励反推，也不会调用可能枚举所有物品过滤器的 `ItemTask.getValidItems()`。

可选文件：`config/ponder/ftbquests-links.json`。不存在时使用自动匹配，不会自行创建或重写文件。整合包样板在 `docs/examples/ftbquests-links.json`：

```json
{
  "version": 1,
  "enabled": true,
  "autoLinkItems": true,
  "quests": {},
  "tasks": {}
}
```

`quests` 按任务详情页的 quest UID 映射，`tasks` 按该任务内部子目标的 task UID 映射。每个值是数组，元素格式：

```json
{
  "component": "ponder_legacy:mmce/modularmachinery/quarry",
  "sceneId": "ponder_legacy:mmce/modularmachinery/quarry/minimum",
  "scene": 0
}
```

`component` 必须是 **当前 Ponder 注册表中的精确组件 ID**。`sceneId` 必须是执行 storyboard 后 `PonderScene.getSceneId()` 的真实值，通常由 `scene.title(...)` 设置；不能把原序号或共用的 schematic ID 当作场景 ID。MMCE 的最小与最大结构共用 `mmce/empty` schematic，但真实场景 ID 分别为 `ponder_legacy:mmce/<机器命名空间>/<机器路径>/minimum` 和 `/maximum`，非主绑定组件附加的 `/binding_N` 不属于真实场景 ID。

`scene` 是可省略的旧格式序号提示，为兼容已有清单工具而保留。存在 `sceneId` 时只按该 ID 唯一匹配，即使 `scene` 仍在范围内也不作为备用跳转。场景重排不会改变绑定；目标 ID 被移除或重复则拒绝打开。整合包作者应从当前注册信息确认机器组件 ID，不要假设所有机器都共用 `blockcontroller`。

旧 `version: 1` 配置继续解析，不自动改写任何文件：

- 本项目生成的规范 MMCE 组件 `ponder_legacy:mmce/<机器命名空间>/<机器路径>`，旧 `scene: 0/1` 分别按已核实的 `MmcePonderPlugin.machineScene` 规则转换为 `/minimum`、`/maximum`，并在点击时与实际编译出的 ID 再次匹配。组件以 `/binding_N` 结尾时无法区分别名和同名合法机器路径，因此一律要求显式 `sceneId`，不附加变体也不去除后缀猜测身份。显式 ID 原样匹配，两种命名情况都支持。
- 其他显式旧序号映射无法恢复原场景身份，显示“旧映射需要补充 sceneId”，不猜测当前第几个场景就是原目标。为它们补充真实 `sceneId` 即可恢复。
- 自动物品匹配不是固定场景绑定，未显式配置的组件仍可查看当前第一幕；该幕的实际 ID 也必须唯一。同一组件已有显式映射时不再添加自动第一幕，避免失效绑定被自动匹配掩盖。
- 既有 53 台 MMCE 测试任务无需重新生成、迁移 UID 或重新安装。`Generate-TestChapter.ps1` 源码已改为以后同时输出真实 `sceneId` 和旧序号提示；本轮未执行生成器，未改已有 fixture、manifest、安装事务或沙箱任务。

读取顺序为 quest 显式映射、每个 task 的显式映射、未显式映射组件的物品匹配、任务图标；相同组件与稳定场景 ID 会去重，旧序号提示不影响稳定 ID 的身份。关闭 `autoLinkItems` 后仅保留显式配置。`enabled: false` 关闭全部入口。

配置在切换 GUI 时检查修改时间及文件长度，变化才重新解析，不在每帧读磁盘。JSON 出错保留上一份有效配置并记录警告，不影响任务书和世界进入。文件大小上限 2 MiB。只接受当前 1.12 的十六进制 UID，不接受新版 64 位 UID。Ponder 注册表成功重载后，已有任务链接会按注册代次重新解析。

历史使用「任务奖励执行 `/ponderui`」的做法不属于本桥接，也不能可靠调用纯客户端查看器；当前实现完全不依赖它。旧任务内容没有被自动改写。

## 生命周期钩子

仅需在客户端代理 `init()` 调用：

```java
net.createmod.ponder.compat.ftbquests.FtbQuestsCompat.register();
```

默认入口在下一次客户端 tick 的 END 阶段延迟打开，先核对玩家、原界面、原任务对象、配置与注册代次。若等待期间代次变化，只取消旧点击，刷新后的按钮可以再次点击，不会被旧失败永久禁用。仅在点击时编译目标组件，读取真实 ID 并确认唯一匹配，然后把同一次编译得到的列表交给 `QuestPonderUI`；首次查看器初始化不再执行第二次 storyboard。

任务查看器初始化与后续索引刷新均严格按真实 ID 唯一匹配，不使用普通浏览器的序号回退。目标机器移除时清空预览而不显示另一台机器；目标场景删除、重复或编译失败时停止播放并提示原因。保留同机器内玩家主动切换的场景 ID，重排后继续显示该场景。普通查看器的默认选择策略没有改变；仅新增受保护的 `selectCompiledScene` 钩子和 `PonderUI` 构造器可见性以支持这个专用子类。

原 `register(SceneOpener)` 扩展保留：调用者收到当前已核验 ID 对应的整数位置，必须在该调用中使用当前注册代次，不能将整数单独排入跨重载的延迟队列。默认集成不走这个自定义扩展。不要从服务端代理、公共静态初始化器或世界加载处理器调用。

### 返回与原生任务同步

返回通过低优先级 `GuiOpenEvent.setGui` 处理，在旧界面关闭和新包装器初始化之前检查 `ClientQuestFile.INSTANCE.questTreeGui`。不嵌套调用 `openGui` 或 `displayGuiScreen`，不发送同步请求。

- 当前文件、玩家队伍数据或权威任务树不可用时关闭入口，不退回旧包装器。
- 全量同步按稳定 UID 查找新任务，同时检查任务及所属章节的可见性；编辑模式沿用 FTB 原生规则。
- 同一文件中的 UID 删除重用不被当成原任务；有效的新任务或其他章节选择优先，不套用旧滚动位置。
- 同文件 `refreshGui()` 会自动迁移原章节但清空详情。这种原生迁移允许继续恢复原任务，与选择其他章节分开处理。
- 仅短期恢复初始化及延迟重建后的滚动；用户输入、目标失效、文件或权威树变更立即取消。
- 新树可能把正在查看的 Ponder 保存为上一页。只修补本次查看器或旧包装器的准确引用，保留无关返回目标，防止后退键重开已关闭的演示。使用已核对的 FTBLib 私有 `GuiBase.prevScreen` 字段，因为该版没有公开 setter；反射不可用时只禁用可选桥接。

## 验收状态

- `QuestSceneLinksTest`：无符号 UID、配置类型、稳定 ID、旧格式 MMCE 转换、去重、保留失效显式目标、抑制同组件自动回退、关闭开关。
- `QuestSceneResolverTest`：按真实 `scene.title` 产生的 ID 选择，不按共用 schematic；重排、删除、重复 ID、未知旧序号、绑定别名和动态变体移除时不会串场。
- `QuestPonderUITest`：实际执行 `initGui`、`updateScreen`、场景切换与关闭重开路径；首次使用已验证快照，不二次编译，重排按 ID 保留，代次变化、组件移除、场景缺失及重复时安全停止。
- 增加 `QuestFooterLayoutTest`：多种 GUI 分辨率、不重叠的底栏、过小界面保护、鼠标边界。
- `FtbQuestFooterLifecycleTest` 的 12 项与 `FtbQuestReturnLifecycleTest` 的 25 项覆盖真实桥接方法和事件处理，使用按安装版 API 建模的测试夹具；另新增 2 项布局用例。本轮共新增 39 项，包含在全项目 262 项 JUnit 中，不是实际游戏画面、输入、网络或奖励验收。
- 联动测试纳入根项目统一构建；全项目执行数量及结果以 `IMPLEMENTATION_STATUS.md` 和当次 JUnit XML 为准，不把新增未执行的用例记为已通过。
- 隔离副本内的 1 个测试章节、53 个机器任务、107 个新增 UID 和 62 个场景目标已通过独立安装完整性审核。未修改正式实例任务文件，也未重复生成测试章节。
- 自动映射仅查注册元信息，不编译或运行场景。
- 仍需在完整实例实测：任意机器任务打开演示、退出回到同一任务、多演示切换、编辑模式、滚动、GUI 缩放、无 FTB 模组启动。单元测试通过不等同于实机验收。
