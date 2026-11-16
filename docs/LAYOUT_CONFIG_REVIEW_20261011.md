# Layout And External Config Review

Updated: 2026-10-11 (Asia/Hong_Kong).

Latest verified build checkpoint: 2026-10-11 22:44:50 (Asia/Hong_Kong).
The outside-model explanation UI and combined browser/parser fixes passed
55 suites / 441 tests and the production artifact audit. The new JAR later
ran in candidate c at 23:05:49-23:10:58 on October 11, confirming startup
registration only. Window capture and its recovery timed out; world entry
and visible UI remain unverified. It has not been deployed to the original
instance. Candidate-b runtime remains pinned to `668335...6E8F`.
See `docs\P0_CANDIDATE_C_RUNTIME_20261011.md`.

This report records the offline layout/configuration review, candidate-b
preparation and bounded runtime outcome. It does not replace or rewrite the
historical rendering, FTB return or specialized runtime reports.

## Current Checkpoint

- Full `test build forgeServerShimJar` succeeded in 41 seconds:
  55 suites, 441 tests, zero failures, errors or skipped tests.
- New production-remapped client JAR:
  `C:\CODEX\Ponder-MMCE-Integration\build\libs\ponder_legacy-0.2.0-beta.jar`.
  Size: 745,541 bytes. SHA-256:
  `BCB1C2C4CA3177F24083CF28883A094FB75EC69A9E8E4FB06E0B5E489F3A7D03`.
- Artifact audit passed with 325 classes, all major 52, remapped commands
  and required resources present; no test/JUnit/dependency classes bundled.
- All 61 preserved build/test/audit files and the 286-entry source inventory
  were independently checked with zero mismatches. This is offline evidence,
  not a screenshot, input or world-entry pass.
- Candidate c contains `BCB1C2...A7D03` and its independently audited
  53-machine quest fixture. Its later startup indexed all 53 machines,
  without compiling previews or demonstrating a usable world/UI.
  Candidate b remains `668335...6E8F`; the original remains `39B364...346E`.

## Previous Checkpoint

- The preceding `668335...6E8F` checkpoint passed full
  `test build forgeServerShimJar`: 49 suites, 371 JUnit tests,
  zero failures, errors or skipped tests. This is not the test total or
  final build result for the newer source.
- The total is the preceding 347 tests plus 12 external-parser tests and
  12 UI tests. Reruns, loop iterations and independent audit cases are not
  additional JUnit tests.
- Runtime-tested production-remapped client JAR:
  `C:\CODEX\Ponder-MMCE-Integration\validation\p0-candidate-20261011b\mods\ponder_legacy-0.2.0-beta.jar`.
- SHA-256:
  `668335186DFE2EB344372494BC825AEF09D4FD58B6C39B7F831859C1A8C36E8F`.
- That JAR and existing test chapter fixture are installed in
  `C:\CODEX\Ponder-MMCE-Integration\validation\p0-candidate-20261011b`.
  The independent installed-chapter audit passed with zero errors.
- The branch ran on October 11 from 21:31:04 to 21:51:06 Hong Kong time.
  Its result is `runtime-budget`, `exitCode=-1`. World-load events,
  53 indexed machines, 4/62 compiled structural variants and two static
  appearances are logged; visible-frame, rendering, quest-navigation and
  input acceptance remain incomplete. These worlds have now been used.
- A new explicit human continuation after the second physical Escape stop
  has resumed isolated client/desktop testing. Another resumption is not
  currently required. Input protection remains in force; a new stop must
  be respected. The separate dedicated-server EULA remains unaccepted.

## External Definitions

The initial expansion/cache changes below describe the verified 371-test
checkpoint. The newer execution limits are recorded separately and included
in the final 441-test run.

The previous parser could expand a small acyclic reference graph into an
unbounded number of operation objects, and scene/tag registration parsed
the same files twice within one reload.

- Each file now shares one 16,384-operation expansion budget across its
  scenes, root-array entries, top-level calls and recursively expanded steps.
  Maximum operation nesting depth is 64.
- Existing reference-cycle detection remains active. The exact node/depth
  boundaries are accepted; the next node/level rejects the file.
- Parsing a rejected file returns no partial scenes or tags. Other files
  retain separate budgets and can still register.
- The external plugin shares one parsed snapshot between scene and tag
  registration during a Ponder reload. Success and failure both clear it.
  The next reload rereads files; standalone registration without reload
  hooks does not create a permanent stale cache.
- Repeated references still receive independent invocation defaults.
- These limits address recursive operation expansion. They are not a claim
  that every possible malformed input or every storyboard runtime operation
  has a global time/memory budget.

`ExternalPonderScenesTest` contributes 12 tests exercising production parsing,
registration and reload lifecycle behavior, including rollback and independent
files. These are offline tests, not a world-loading diagnosis.

## Execution Budget Review

The new build adds 19 cases to `ExternalPonderScenesTest`, making 31 cases
in that suite. The existing 16,384-operation and depth-64 limits now account
for repeated `break_progress` events as well as parsed expansion nodes.
Repeated target visits share a 262,144-position budget per file, including
selection-based world operations, displayed baseplates and applicable NBT
work. Multiple scenes and nested sequences cannot reset that file budget.

Required positions are checked before compilation. Inclusive selection
volumes and baseplate extents use checked long arithmetic; block coordinates
must remain within -30,000,000 to 30,000,000. Invalid repetitions, absent
positions and oversized selections reject the file without partial output.
Other files keep separate budgets. These are bounded-input safeguards,
not a wall-clock scheduler or proof that every possible storyboard operation
has a universal runtime limit.

## Popup And Text Layout

The following fixes and counts belong to the 371-test checkpoint.

- Group popup height is bounded above the timeline/footer and large groups
  are paged. Shared geometry determines both drawn icons and hit regions.
- Click, wheel and keyboard input belongs to the popup while it is open,
  including dismissal. It cannot also operate the underlying caption,
  camera, timeline, model switch or native GUI button.
- Page changes immediately replace icon hit targets. Unused final-page
  slots cannot select stale items.
- `initGui()` invalidates old popup coordinates before another frame can
  receive input, while preserving the page position for the new layout.
  Index replacement, removed groups and closure clear the selector.
- Actor labels and group hover text are trimmed with the active font before
  positioning. Tests retain vanilla font measurement and cover formatted
  Chinese and variable-width glyphs.
- Independent review found the initial resize hit-region gap and a stale
  scene-navigation test fixture that kept the popup open. Both were corrected.
  Production `initGui()` resize and `synchronizeIndex()` last-page removal
  cases are now covered; boundary navigation remains tested with the popup
  closed, separately from popup input behavior.
- Nine `PreviewGroupInputTest` cases and three `PreviewTextLayoutTest` cases
  passed. Their headless geometry/callback evidence does not establish
  Minecraft screenshots, modded-font appearance or native input acceptance.

## Outside-Model Explanation UI

The user's latest requirement is to move explanatory text and hint UI out
of the model area, so blocks cannot cover text and text cannot obscure
block information. This is implemented and offline-tested in `BCB1C2...A7D03`.

- Normal showcase has a fixed right-side explanation region. Debug preview
  reserves a bottom region. Their dimensions are content-independent, so
  changing text or pages does not resize the model viewport.
- Active text, controls, actor labels, point-of-interest descriptions,
  notices and hover hints are routed outside the model. GUI snapshot images
  occupy accessible pages in the explanation region rather than cover blocks.
- Related parent/child GUI images share a uniform fit scale and relative
  positioning, with bounded handling of cyclic parent references.
- The 2D explanation pass isolates depth/native render state. Model scissor
  bounds and camera fitting use the actual model region; old caption insets
  no longer reduce or displace the new viewport again.
- Click, wheel and Page-key handling in the explanation region pauses
  playback and consumes the event. It cannot also pan/zoom the model or move
  the timeline. Playing again clears reading hover state.
- Resize and Debug-tab transitions immediately invalidate stale hits/chrome.
  High font metrics, narrow dimensions, long text and full available panel
  height are covered by pagination/layout regressions.

The final suite includes explanation content/layout, hover, GUI image,
camera and input regressions. A final read-only review found no blocking
issue in this scope. No Minecraft pixels or native input were tested for
this JAR; this remains a code/test result rather than visual acceptance.

## Browser Review

Browser grid columns now adapt to the usable narrow-screen width.
Descriptions and hints are bounded by the actual panel/footer geometry,
including long text. `PonderBrowserLayoutTest` has six passing cases;
index-refresh identity/scroll preservation is tested separately.
These are included in the 441 total, not additional to it.

## Build Evidence

Paths below are relative to `C:\CODEX\Ponder-MMCE-Integration`.

### Final 441-Test Build

| Evidence | Result |
| --- | --- |
| `validation\build-explanation-ui-20261011-224138-449.log` | `BUILD SUCCESSFUL in 41s`; 6 executed / 7 up-to-date tasks |
| `validation\artifact-audit-explanation-ui-20261011-224308-722.json` | Production-remapped client; 745,541 bytes; 325 major-52 classes; passed |
| `validation\build-explanation-ui-verified-20261011-224359-473\receipt.json` | 55 suites / 441 tests; zero failures/errors/skips; all 61 preserved files rehashed |
| `validation\build-explanation-ui-verified-20261011-224359-473\source-inventory.json` | 286 source entries; all current hashes/sizes match |
| `validation\build-explanation-ui-failed-20261011-222928-889\receipt.json` | Initial 433-test / 54-suite attempt with one fixture failure preserved |
| `validation\explanation-ui-focused-20261011-222544-276\receipt.json` | Earlier 86-test / 11-suite focused run preserved; not added to the final total |

The 61 preserved payloads are four JARs, 55 JUnit XML files, the build log
and the artifact audit. The source inventory is a separate evidence file,
with SHA-256
`671A22D6AFF53CC212FF3AFD99BCBD96490B4980F29380E1D88BECEA31F14C02`.
Source inventory verification is not a reproducible-build claim, and the
artifact auditor itself does not certify source freshness.

The first full attempt failed
`PonderBrowserIndexRefreshTest.closeReleasesBrowserMetadataAndReopenRetainsIdScrollAnchors`.
Its fixture assumed one column at width 320, while the responsive layout
used three, and its seeded scroll was out of range. It now uses actual
columns and enough entries without dropping the ID-anchor assertions.
The failed artifacts remain preserved. Hover/resize regressions were then
completed and the full final suite rerun, producing 441 passing tests.

The successful run executed production/test compilation, remapping and
tests; the unchanged server shim and other unchanged inputs were up-to-date.
No failed-build JAR was designated as the final candidate.

### Earlier 371-Test Build

The following receipts preserve the older `668335...6E8F` checkpoint.

| Evidence | Result |
| --- | --- |
| `validation\build-layout-config-20261011-132007-434.log` | Full rerun `BUILD SUCCESSFUL`; tests executed |
| `validation\layout-config-test-summary-20261011-132107-200.json` | 49 suites / 371 tests; 0 failures/errors/skips |
| `validation\artifact-audit-layout-config-20261011-132107-226.json` | Production client; 320 classes, all major 52; remapped commands; required resources/bridges present |
| `validation\build-layout-config-verified-20261011-132145-661\receipt.json` | Four JARs and 49 XML files preserved; all 53 backup hashes independently rechecked |
| `validation\build-layout-config-failed-20261011-131842-164\receipt.json` | First 371-test attempt and four failed fixtures preserved |

The first attempt failed three missing-tag assertions because the fixture
expected `ponder:not_registered` instead of the production
`ponder_legacy:not_registered`. A fourth fixture called the real `initGui()`
with an uninitialized `I18n.i18nLocale`. The missing-tag expectations now use
`Ponder.asResource`, and the client fixture initializes/restores its locale.
Behavior assertions were retained and the entire suite was rerun. The
initial failed log is
`validation\build-layout-config-20261011-131622-062.log`.

Production compilation, remapping and JAR tasks were up to date on the
successful rerun; `compileTestJava` and `test` executed. The complete task
chain succeeded, not a claim that each task rebuilt unchanged inputs.
The audited artifact excludes test/JUnit and compile-only dependency classes.
Major 52 does not establish stock Forge-client compatibility: current
Cleanroom metadata and the JAVA_16 mixin setting remain unchanged.

The unchanged server shim is preserved with SHA-256
`28CCA3699A2E81EA77DCB9AFC073532A4796E90EEFF5348B03AE7CA9C605AAF3`.

## Fresh Candidate Preparation

- Restore receipt:
  `validation\world-restores\p0-candidate-20261011b-20261011-131903-459-789630bd\receipt.json`.
  Both frozen worlds were restored: 164 files / 16,461,843 bytes.
  The receipt records original worlds and baseline unchanged.
- JAR installation:
  `validation\p0-candidate-20261011b\validation-output\previous-ponder-20261011-132228-294-584ea027\transaction.json`.
  Status is `committed`; the previous `39B364...346E` original package is
  archived outside active mods. Source and installed JAR hashes matched at
  installation; candidate b's installed hash was rechecked after its run.
- Chapter installation:
  `validation\transactions\p0-candidate-20261011b-20261011-132246-851-2ef3c6fc\transaction.json`.
  Status is `committed`; the existing fixture was reused, not regenerated.
  It contains one chapter, 53 machine quests and 62 scene targets.
- Independent chapter audit:
  `validation\audit-candidate-b-layout-config-20261011-132437-693.json`.
  The installed FTB Quests 1202.9.0.15 parser read 185 SNBT files; all
  56 installed output hashes, 107 new UIDs, 53 quest/task mappings and
  62 targets per mapping set passed with zero errors. Inputs were read-only.
- Candidate a retains `F484A9...94C1`, its prior transactions and its older
  runtime evidence. No history was overwritten and no earlier world-load
  result is transferred to candidate b.
- Installation records `originalInstanceChanged=false`. The original
  instance has not received the new candidate JAR.

## Candidate B Runtime

The verified `668335...6E8F` run is preserved at
`validation\p0-candidate-20261011b\validation-output\20261011-133104-009`.
PID 19748 ended at 2026-10-11T13:51:06.5290324Z because of the 20-minute
budget. Exit code -1 does not turn this into either a normal exit or a
reported game crash.

The final log and `mmce-reconciliation-final.json` show a complete published
53-machine index with no failures, client-world load, and four MINIMUM
structure compilations: blockcutter, windgenerator, steamboiler and orewasher.
Blockcutter and orewasher also compiled static appearances. The remaining
58 structural targets lack compilation evidence. All visual/interaction
statuses remain unverified.

Three identity-verified thread dumps contain no reported Java deadlocks
or `BLOCKED` threads, while sampling different client item-render paths
and server-loop sleep. This does not exclude native or intermittent issues.
Window capture failed and the input guard was respected; logged GUI changes
are not coordinator-generated click/key acceptance.

Post-run fixed-probe output changes were independently preserved:
`validation\probe-evidence-post-candidate-b-20261011-135200-audit\receipt.json`.
The source grew to 602 files / 313 directories / 290,047,935 bytes.
Only the new startup session and changed `startup-latest.json` were copied,
1,200 bytes in total, with matching content/timestamps and stable source
inventories. The original Ponder remains `39B364...346E`.

Full timeline, hashes, caveats and probe evidence are in
`docs\P0_CANDIDATE_B_RUNTIME_20261011.md`. That historical report is unchanged.
No part of this run verifies the newer `BCB1C2...A7D03` explanation UI.

## Remaining Gates

The new build and artifact audit are complete; keep them separate from
the 371-test build and candidate-b run. After candidate c's startup-only
run and capture failures, the new JAR still requires
visible world entry, first usable frame, interaction,
camera and rail/transparency rendering, large-group paging, font behavior,
outside-model explanation layout, repeated quest returns and reload behavior.
Preserve failures as well as passes; candidate b is no longer an unused world
copy, and a repeat cold comparison requires a fresh baseline restoration.
The planned three cold starts and three world reentries, multiplayer,
optional-dependency and original-instance-equivalent checks remain open.
The historical world-entry failure is not declared repaired.

The isolated minimal server still has `eula=false` and has not launched.
Client/desktop continuation does not constitute EULA acceptance. The
candidate-c client was stopped by the coordinator after capture failed;
no dedicated server was launched. Restore usable capture before repeating
game validation, and do not substitute startup logs for UI acceptance.
