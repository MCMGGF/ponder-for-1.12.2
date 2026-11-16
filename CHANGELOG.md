# Changelog

## [0.3.7-beta] - 2026-11-17

本节记录 Cleanroom `0.3.x-beta` 路线首次同步到目标仓库的源码发布。以仓库
`main` 的 `c494bf7` 为历史父提交，新建 `cleanroom/0.3.x` 分支；`main` 的
Java 8 / Forge `1.3.0-alpha.1` 实现保留。两条路线分别编号，API、JSON 格式与
运行范围不视为兼容；本次不将版本号从 `1.3.0` 降级。

### 新增（相对目标仓库已有主线）

- 同步本地 Java 25 / Cleanroom 回移源码、资源和回归测试，保留原有作者、迁移说明及历史更新日志。
- 带入本地编辑器、蓝图与结构/图片资源、受限离线包交换、世界投影仪，以及独立的教学观看、尝试和练习界面；未知编辑步骤仍不能直接发布，功能范围限于本地单人环境。
- 带入六个原版玩家操作教学、三维选择轮廓、目标前景说明，以及既有 MMCE / FTB 集成。
- 带入整合包专用的六章前期石油线和隔离操作练习，包括四站结构、独立流体/能量线路、批量存储、组件安装、供汽条件、产量与故障说明；练习保留有限资源账本和可恢复混管故障，不写入真实背包或世界。

### 修复（包含已完成的 0.3.6-beta 变更）

- 统一颜色、矩阵、视口和世界投影的查询入口；安装 Actinium 时读取其模拟状态，修复 Core Profile / 固定管线模拟环境下演示、文字及控件空白的问题。保留 LWJGL 2/3 查询兼容，已选接口失败继续报告原始异常。
- 增加已核对的石油线脚本/配置成对基线；仅将内容配置大小上限从 32 MiB 提升到 64 MiB，普通文件仍限 8 MiB。保留旧基线、未知文件及错误配对拒绝机制。
- 内容失败同时显示错误教学并记录原因，按同一注册代际的连续相同错误去重，避免只留下不可见失败。

### 其他变更与验证边界

- CI 后续修复：在插件解析及 Unimined 项目依赖解析中，将 Forge Maven 限定为 Forge/MCP 组，避免查询无关 Minecraft、Fabric 和 Unimined 库时因该仓库超时而中断整个构建。新增真实 Gradle + 本地 HTTP 仓库回归，模拟 Forge 对无关依赖返回 503，在仓库已创建及延迟创建两种顺序下验证四项依赖各自正确解析。CI 保留源码测试和生产制品构建，并上传测试报告及路由诊断日志，不通过跳过测试掩盖下载失败。本次仅修正构建配置，版本继续为 `0.3.7-beta`。
- 修复前景渲染源码合同测试对换行格式的依赖：方法签名匹配前统一 LF/CRLF/CR，并增加三种换行格式的回归。保留原有渲染顺序断言，避免 Windows CI 检出 CRLF 时误报；不修改游戏渲染实现。
- 后续复验：将实际相关源码转换为 CRLF 后，旧测试复现同一方法查找失败，修复后该组测试通过；新增回归后的已核对快照全套为 1454 项启动、1452 项通过、2 项条件中止、0 项失败。生产 JAR 哈希保持不变。
- 本地版本从 `0.3.6-beta` 递增为 `0.3.7-beta`，用于本次源码发布及元数据/文档修正；没有在本次发布中新增上述既有玩法。
- 更新仓库地址、README 版本和实际生产构建命令；为 `cleanroom/**` 分支启用客户端构建、测试、服务端 shim 与制品上传。忽略本地依赖、运行日志和凭据文件。
- 保留 CleanroomMC 与上游 The Create Team 的 MIT 声明，将 LICENSE 和 NOTICE 随生产 JAR、源码 JAR 及服务端 shim 打包。
- 主客户端目标保持 Java 25 普通字节码 69.0，既有 Forge 专用服务端 shim 保持 Java 8；不升级或替换项目依赖栈。
- `0.3.6-beta` 已有隔离客户端的原版、单台 MMCE、六章石油线、错误提示和恢复重开证据；这些画面证据只对应原 JAR，不能代替 `0.3.7-beta` 的实际游戏验收。本次源码发布不自动更新正式游戏中的已部署 JAR。
- 发布复核时，正式实例的 `content.json` 已与 11 月 16 日已核对基线不同；针对该实例的基线验证会拒绝未知配置。本次保留拒绝机制，不将新配置自动加入白名单；已核对快照与当前正式实例的验证结果分别记录。
- 本次使用 JDK 25 / Gradle 9.3.1 完成生产 remap、源码 JAR 和服务端 shim 构建；在留存的已核对快照上运行 1453 项测试，1451 项通过、2 项因 Windows 符号链接条件中止、0 项失败。596 个主客户端类为 69.0、74 个 shim 类为 52.0，314 个源码/资源条目与 sources JAR 一致，三类交付 JAR 均检查到 LICENSE 与 NOTICE。

## [0.3.6-beta] - 2026-11-16

- Query Actinium's emulated colors, matrices and viewport when installed, preserving native LWJGL 2/3 support without hiding failed emulation calls.
- Share the query backend across GUI state, matrix guards, world projection and static models to prevent blank previews and disappearing text.
- Accept the re-audited November 16 oil-line pack profile and log content failures once per registry generation while keeping the visible error lesson.

## [0.3.5-beta] - 2026-11-01 (Deployed-Pack Baseline Fix)

- Recognize the re-audited script/configuration pair from the actual installed pack so all six oil lessons and their operating screen pass the shared content gate.
- Retain the previous audited revision as a complete compatible profile; reject mixed revisions, unknown edits, missing files and unsafe paths.
- Add regression coverage for profile fallback, pair consistency and rechecking after file changes. Installation validation now invokes the production gate against the destination's actual files before replacement.

## [0.3.4-beta] - 2026-11-01 (Operating-Practice Candidate)

- Add an isolated five-station oil-line operating screen with virtual feedstock, components, steam gates, separate byproduct collection, generation, load and a recoverable mixed-pipe fault.
- Keep a deterministic bounded resource ledger, finite surveyed oil, visible pipe priming, whole-bucket versus partial-cell transfers, internal energy buffering and six session-only goals. Explicitly simplify temperature, transport speed and network scheduling.
- Add a book-icon entry only to the six existing oil lessons, preserve all playback and skin controls at 320-pixel logical width, and retain the existing Ctrl+J practice behavior for other lessons.
- Pause the source lesson on entry; resizing and station changes retain local exercise state, while closing releases it and returns to the same lesson. Do not rewrite the lesson timeline from exercise actions.
- Preserve the FTB return context only for explicitly owned direct child screens of the active viewer; reject foreign, stale and returning children. Apply the same ownership contract to existing isolated vanilla practice.
- Preserve existing machine, recipe, quest and scene identities. No real player inventory, world writes, rewards, machine interaction or new dependency is added. Native acceptance and deployment remain separate from source/build checks.

## [0.3.3-beta] - 2026-10-31 (Oil-Line Candidate)

- Add six connected petroleum lessons using this pack's existing boiler, surveyed oil pump, distillation tower and diesel generator. Preserve all existing machines, recipes, FTB tasks and lesson IDs.
- Show a full-line overview and focused operation chapters with detached player actions, shared-hatch targets, separate steam/oil/diesel/energy runs and storage snapshots.
- Teach actual component installation and exposed NORTH/DOWN faces, intermittent steam priming, manual oil/byproduct handling, whole-bucket limits and optional partial IC2 fluid cells.
- Distinguish 256 FE/t generation from the single-pump ideal average of 112 FE/t, transport priming, finite oil reservoirs and byproduct blockage.
- Register lazily and reject missing/changed pack dependencies instead of substituting fake blocks or recipes. This remains a scripted process visualization, not a live MMCE/Ender IO simulation or a four-machine static appearance renderer.
- Keep the new build and acceptance evidence separate; source/build success does not mean this candidate has been installed or visually accepted.

## [0.3.2-beta] - 2026-10-28 (Candidate In Progress)

- Give the world-projector reading screen its own readable logical canvas at native GUI scale 1, without changing the world camera, geometry or global GUI settings.
- Map foreground anchors, input and framebuffer clipping through the same canvas, including odd display sizes; restore temporary dimensions on nested callbacks and failures.
- Keep the ordinary projector HUD and configuration presentation unchanged.
- Preserve vanilla lessons, player operations, selection outlines and existing FTB/MMCE identities. No new machines, recipes, dependencies or multiplayer scope are added.
- Keep new build, QA, runtime and deployment evidence separate from 0.3.1. This candidate has not yet passed native acceptance or been installed.

## [0.3.1-beta] - 2026-10-27 (Local Delivery)

- Keep the completed singleplayer migration and stable lesson, FTB and MMCE identities.
- Rebalance soft-wrapped Chinese punctuation within the measured line width without losing text, spaces, formatting or Unicode code points.
- Add the missing English and Chinese keybinding names.
- Present projector configuration at a readable local scale when the game uses GUI scale 1; keep drawing and input in the same coordinate system without changing global GUI settings.
- Replace abbreviated projector commands with localized labels and retain full-label hover text.
- Stop bundling 31 legacy PNG assets with unresolved redistribution provenance. Keep Thermal snapshot IDs using dependency-owned textures and explicitly labeled draw-only schematic panels; use a Minecraft book icon instead of the legacy logo.
- The exact 660670D4 production artifact was installed locally on 2026-10-27 after its separate build and bounded native checks. The migration workspace retains its installation receipt, source recovery and remaining acceptance limits; those passes do not transfer to 0.3.2.

## [0.3.0-beta] - 2026-10-27 (G2 Local Delivery)

- Deliver the local editor, blueprints and assets, bounded offline exchange, world projectors and isolated watch/try/practice menus.
- Preserve vanilla player tutorials, selection-bound 3D outlines, foreground callouts and existing FTB/MMCE integration.
- Fix compact Unicode showcase control, footer and model-tab overlap.
- Verify 1,301 tests in 156 suites, six isolated client runs and candidate-bound screenshot sampling.
- Install the exact G2 production JAR with backups and independent post-install file verification. Runtime coverage is finite and does not establish physical-input, acoustic, multiplayer or arbitrary modern-content compatibility.

## [0.3.0-alpha.3] - 2026-10-25 (Candidate In Progress)

- Queue the local editor command until ClientTick END so GuiChat's Enter handling cannot immediately close the new editor.
- Bind pending editor requests to the requesting world, player and screen; clear stale requests and never return to a closed chat screen.
- Report workspace/editor initialization failures without leaving an owned partially initialized screen active.
- Preserve complete block-entity NBT snapshots when forking a lesson, including field removal; retain merge behavior for existing author documents.
- Toggle the vanilla repeater/lamp block pairs explicitly and repair the complex redstone matrix's repeater input direction and powered timeline.
- Wrap all local multiline UI text using one lossless code-point-aware measurement path and stop trimming already wrapped caption bodies; retain separate single-line label fitting.
- Preserve the failed compact Unicode visual evidence from the earlier Alpha3 candidate instead of treating its programmatic success as complete visual acceptance.
- Add real chat-command and repeated showcase lifecycle acceptance instead of treating direct screen construction as command coverage. Native results remain candidate-hash specific.
- Retain the singleplayer Cleanroom / Java 25 boundary and explicit unsupported-step diagnostics. This is not the final beta release.

## [0.3.0-alpha.2] - 2026-10-25 (Limited Candidate Acceptance)

- Add bounded local PNG assets, import review, thumbnails, image steps and explicit package image mappings.
- Share complete foreground captions, virtual menus and image rendering with the embedded editor; preserve input ownership and stable framing.
- Add loaded-chunk coordinate/structure hints and a non-pausing world-projector reading layer with deterministic preview particles.
- Fix small-screen virtual-menu readability and schema-only review of active extension handlers.
- Preserve 114 suites / 871 passing tests and candidate-bound authoring/projector evidence in the migration workspace; do not transfer that result to later builds.

## [0.2.2-beta] - 2026-10-24 (Candidate)

- Replace filled screen rectangles with real selection-bound 3D edges, exact section transforms and faint occluded edges.
- Merge filled cuboid selections, retain scattered block shapes, deduplicate lines and evaluate chase slots deterministically.
- Replace the fixed showcase sidebar with target-bound foreground callouts, leader lines, per-target text/image paging and small-screen fallback.
- Avoid all target outlines, actors and controls; keep callout input separate from model and timeline input.
- Bind and revise the six vanilla lesson captions and GUI images while retaining scene IDs and MMCE/FTB behavior.
- Add compatible Selection anchors and budget external overlay target expansion before allocation.
- Fix valid crafting previews hiding the output slot during the input phase; retain the furnace phase distinction and unavailable-recipe messaging.
- Migrate legacy Complex, Debug/UI and optional Thermal lessons to explicit visible targets, shared GUI parents, accurate reference text and ordinary fixed-camera behavior without changing scene IDs.
- Repair the Debug GUI's AIR anchors with visible furnace/reference blocks and a course-long 3D outline; verify all finite built-in GUI targets against actual runtime state.
- Match tutorial card counts to the actual filtered viewer and retain per-scene render failures even after global log deduplication.
- Prefer placing callouts around the complete currently visible block-model envelope without reframing; retain readable paging fallback in constrained windows. Bind legacy lever prompts to the lever itself and describe its actual redstone result.
- Replace the six-only native checklist with registry-driven, seeded stratified sampling; cap MMCE at five unique scenes and report not-selected/not-applicable cases without claiming full coverage.
- Isolate native tile-entity rendering projection/model-view stacks, viewport and scissor alongside existing client-array/state restoration.
- Share the existing FTB footer click path with an optional native crafting-task round-trip observer; keep actual drawn geometry, screen/quest identity and pending-action checks.
- Draw the FTB footer in an isolated screen-space foreground pass so inherited depth/clip/texture state cannot hide the visible entry button; restore all touched state after buttons and hints.
- Scope fixed-function lighting disable to baked BLOCK/fluid uploads, whose vertex format has no NORMAL element, and restore raw/cached lighting state in finally. Preserve depth, diffuse textures and lightmaps; add seven focused regressions.
- Add read-only audio readiness and world-ownership preflight checks to the private QA probe; stop without entering a world when the existing sound backend is incomplete.
- Full rebuild verified 603 JUnit tests in 78 suites. The current 61483C8B candidate passed its default textured four-model fixture frame and eight selected vanilla/legacy ticks plus a crafting-task FTB round trip; eleven native PNGs received limited independent visual review, with normal save/exit and observed state restoration. Rails T+230 predates minecart creation, so the minecart, new-world/repeated lifecycle, additional scales and physical-input gates remain incomplete and block original-instance installation. The earlier 4722001D footer pass and 1288 visual failures remain tied to their own hashes. Evidence is in docs/CONTINUATION_AUDIO_FTB_20261024.md and docs/ALL_SCENES_REVIEW_20261024.md.

## [0.2.1-beta] - 2026-10-23 (Candidate)

- Add six curated vanilla lessons with detached Minecraft player actions, loaded-skin fallback and step navigation.
- Render vanilla minecarts and include full actor paths and geometry in fixed camera bounds.
- Keep actors behind foreground translucent geometry; preserve external explanation layout.
- Fix client command completion compiling scenes, autoplay step-button state and tutorial group leakage.
- Add a separate six-quest FTB fixture with collision-safe staging and guarded rollback; retain MMCE integration.
- Verified 483 JUnit tests and 50 installer cases. Client visual/world-entry acceptance and original-instance deployment remain pending.

## [0.1.0-beta] - 2026-04-10

### Added
- 1.12.2 Cleanroom backport core framework
- in-game Ponder browser and showcase UI
- external JSON scene loading and bilingual documentation
- live GUI snapshot support and JEI / HEI integration work

### Changed
- repository documentation reorganized into language-specific directories
- example JSON scenes moved under `docs/examples/`

## [1.0.0] - 2023-09-15

### Added
- This is a default template changelog that follows the [KeepAChangelog Convention](https://keepachangelog.com/en/1.1.0/)
