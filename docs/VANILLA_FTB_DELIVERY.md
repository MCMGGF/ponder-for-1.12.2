# Vanilla Tutorial Chapter Delivery

## Scope

The independent chapter `c9d9d27c` contains six optional, manual checkmark tasks.
Viewing a Ponder scene does not complete a task, consume items, or grant a reward.
No MMCE machine, recipe, appearance, chapter, or existing quest UID is generated or
replaced by the vanilla fixture. Existing MMCE links remain untouched.

| Lesson | Component | Stable scene | Quest UID | Task UID |
| --- | --- | --- | --- | --- |
| Building | `minecraft:stone` | `ponder_legacy:vanilla/building` | `f045c78a` | `298c3b5b` |
| Crafting | `minecraft:crafting_table` | `ponder_legacy:vanilla/crafting` | `ac7b7795` | `6b532c01` |
| Smelting | `minecraft:furnace` | `ponder_legacy:vanilla/smelting` | `ef7f3e7a` | `7f89ad8b` |
| Logistics | `minecraft:hopper` | `ponder_legacy:vanilla/logistics` | `164f0669` | `97957b5e` |
| Redstone | `minecraft:redstone` | `ponder_legacy:vanilla/redstone` | `90d60ef5` | `1b9d78da` |
| Rails | `minecraft:rail` | `ponder_legacy:vanilla/rails` | `f8c6c4a6` | `3237ff9f` |

Scene links intentionally omit ordinal scene indices. The existing client bridge
resolves stable scene IDs and preserves the task, chapter and scroll position.
It does not call quest completion or reward APIs. GUI return and world-state
isolation still require real-client acceptance, independently of offline tests.

## Build And Stage

All commands run from `C:\CODEX\Ponder-MMCE-Integration` in PowerShell 7.

```powershell
& .\validation\tools\Generate-VanillaChapter.ps1
& .\validation\tools\Test-InstallTestChapter.ps1 -ChapterKind vanilla
& .\validation\tools\Test-InstallTestChapter.ps1 -ChapterKind mmce
& .\validation\tools\Prepare-VanillaChapter.ps1 -SourceInstance '<original-instance>' -SandboxName vanilla-stage-<unique-name>
```

The generator writes only `validation\vanilla-tutorial`. IDs are deterministic;
an existing conflicting UID causes rejection, never silent ID reassignment. The
preparer reads the source instance, copies only FTB configuration and the optional
Ponder link map into a fresh CODEX directory, hashes both sides, then prepares a
transaction without applying it. It never copies or writes world progress.

The merge uses the installed FTB 1.12 SNBT parser, not regex replacement. It
preserves existing chapter order, root `file.snbt`, reward tables, unknown link
metadata, `enabled`, `autoLinkItems`, and all existing quest/task links. A disabled
existing link map stays disabled and is reported, not silently enabled.

## Isolated Installation And Rollback

```powershell
& .\validation\tools\Install-TestChapter.ps1 -SandboxName '<validation-sandbox>' -ChapterKind vanilla -StageOnly
& .\validation\tools\Install-TestChapter.ps1 -SandboxName '<validation-sandbox>' -ChapterKind vanilla
& .\validation\tools\Restore-TestChapterTransaction.ps1 -Transaction '<returned-transaction-json>'
```

Both installation and rollback require a stopped, identity-verified sandbox
directly under `validation`; they cannot target the original instance. Repeated
installation is a byte-preserving no-op. The transaction records source hashes,
staged hashes, pre-change backups, chapter order, policy and each operation state.
Unexpected writes, symlinks, hard links, normalized UID collisions, conflicting
content, concurrent edits and unfinished transactions are rejected. Apply errors
automatically restore changed files; explicit rollback refuses to overwrite any
subsequent user edit. Retain an incomplete rollback for inspection, never retry by
deleting its evidence.

The production JAR and original-instance installation remain gated by the root
task's runtime acceptance. A staging plan is not a deployed chapter and is not
evidence that a scene rendered correctly. Before approved original deployment,
re-snapshot its current configuration, confirm game shutdown, and revalidate every
source hash; do not transplant an old staged index over newer user quests.

The post-install auditor now supports `-ChapterKind vanilla` (the default remains
`mmce`). Run it against the new committed sandbox transaction, not the historical
MMCE receipt. It checks the nine installed outputs, all thirteen deterministic
UIDs, six manual checkmark quests and both six-link sets against the existing
lesson manifest, together with preserved old chapters, root configuration and
link policy/metadata. `Test-VanillaInstalledChapterAudit.ps1` provides isolated
negative regressions. These checks do not claim task-button or return-navigation
acceptance; those require the actual FTB GUI and Ponder bridge in a native client.

## Isolated Installed Audit, 2026-10-24

- Sandbox `p022-native-20261024a` now has the six-lesson chapter installed by
  committed transaction `p022-native-20261024a-20261024-023844-212-a1b5f58b`.
  This is a CODEX test copy, not original-instance deployment.
- Post-install audit passed: nine outputs, thirteen UIDs, six quests, six tasks,
  six stable scene targets per mapping set and all 358 installed SNBT files.
  The seven previous chapters retain their order; 350 prior files remain byte
  identical, with only the chapter index merged. All 351 original input files
  and the protected `file.snbt` matched the installation snapshot.
- Baseline audit: `validation/audit-vanilla-baseline-20261024-023949-846.json`,
  SHA256 `4071FF3F1A4C8AC55EE9FD706A9A3E805E2EC82D1AE45A1789314066738410B8`.
- Sixteen synthetic offline audit regressions passed, including wrong chapter
  mode, missing/changed output, protected/prior file edits, duplicate task UIDs,
  rewards, automatic completion, stable/ordinal links, duplicate JSON keys,
  stage-only receipts and manifest hash mismatches. The real sandbox, original
  configuration and committed transaction were hash-verified unchanged.
  Report: `validation/installer-audit-vanilla-test-results-20261024-023949-846.json`,
  SHA256 `7D252E8B68739198E9E59A257A5AB9E3896DB7BB42387057A2E1F39E9C3FC0D8`.
- No Minecraft process was started by the audit. Runtime rendering, footer
  clicks, return navigation and task-progress isolation remain unverified by
  these reports, independently of the structural pass.

## Offline Verification, 2026-10-23

- Vanilla installer: 25 cases passed, report
  `validation\installer-vanilla-test-results-20261023092623.json`.
- Existing MMCE installer default: the same 25 cases passed, report
  `validation\installer-mmce-test-results-20261023093023.json`.
- These include exact-byte explicit rollback, refusal after a subsequent user
  edit, stage-only operation, idempotence, duplicate/normalized/numeric UID
  rejection, preserved policies/unknown metadata, links/hardlinks rejection,
  stopped-process enforcement and automatic rollback of early/late write errors.
- The first vanilla run reached its guard probe but failed because that test
  helper only accepted the old `install-test-*` directory prefix. Its allowlist
  now also accepts the two explicit fixture test prefixes; both full suites were
  rerun successfully. This was a harness failure, not a game acceptance result.
- Current-original config snapshot: 351 files, independently rechecked with zero
  changed hashes. Stage-only plan: nine additions/merges, zero applied changes,
  seven existing chapters retained in order and `c9d9d27c` appended. Receipt:
  `validation\transactions\vanilla-stage-20261023-20261023-092726-340-f03c350e\transaction.json`.
- Only short-lived offline Java probes ran. No Minecraft instance was launched,
  no world or player-progress file was written, and no original-instance
  deployment was performed by these tools.

## Original-Instance Baseline, 2026-10-23

The installed Ponder was still `ponder_legacy-0.1.0-beta.jar` (530,540 bytes), SHA256
`39B36481BB1C6AB4683045D73C50D1CB2F33370618A46310F56CC2ABF358346E`.
FTB Quests is `1202.9.0.15` and FTBLib is `5.4.7.2`, both locally patched jars.
No source instance files were changed by this work.

At read time, `latest.log` was 27,436,430 bytes, modified at 17:14:49 local time.
It records 28 Ponder scene entries at 11:39:12, integrated-server start at
11:41:30, the in-game pause menu and chunk saving at 16:30:21, player logout and
server shutdown at 16:30:22, then dimension cleanup at 16:30:23. These are old-JAR
world-session evidence, not proof that the new build passed runtime acceptance.
Repeated recipe-search warnings originate in
`cn.seeds.airship.OpeningBotanicalBatch$Route.cost:56` and
`OpeningLiquidMana`, through MMCE; the sampled stack contains no Ponder frame.
That observation does not establish a root cause for the historical entry hang.
