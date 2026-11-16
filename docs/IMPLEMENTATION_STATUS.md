# Implementation Status

Updated: 2026-10-25 (Asia/Hong_Kong)

## Current Checkpoint: Legacy Actor And Item Feedback

The screenshot-specific candidate is now 0.2.3-beta, 865,353 bytes, SHA256
`5A0929DE9BF67F02FF3848DF64D1EA0E7CF48851C982F3C7FC9F82A092FA45D9`.
The full 81-suite / 630-test build and independent artifact review passed.
Three legacy IDs now use real players, selection-bound chasing outlines and
native ground-model items, with explicit semantic steps and aligned movement.
The first dedicated nine-frame native run exited normally but FAILED its
inventory text-hash comparison. That failure remains unattributed and unchanged.
The second run of the same candidate passed: nine new images reviewed, twelve
full inventory NBT observations equal to the unchanged entry baseline, seven
nearby blocks unchanged, nine resource cleanups, normal world stop and exit 0.
The NBT evidence was independently decompressed and semantically compared.
This does not prove the first failure was key ordering. At 01:30 the JAR-only
candidate transaction was committed to original mods after stopped-game checks,
backup and candidate-bound native/image preflight. Only 0.2.3 is enabled; old
0.2.2 is in `delivery\install-ponder-023-20261025-013009-990`. No original
world, quest or configuration was written and no original game was launched.
Minor T32 orphan punctuation, full timeline, manual orbit/zoom, other scales
and the original repeated-entry/open-count gates remain unaccepted.
See `docs/ACTOR_FIX_REVIEW_20261025.md`. All checkpoints below remain historical
evidence for their respective candidate hashes, including installation status.

## Installation Update: Candidate Requested By User

At 23:14 Hong Kong time the user explicitly requested the candidate mod in the
original instance. The JAR-only transaction is now committed: original mods
contains one enabled `ponder_legacy-0.2.2-beta.jar`, SHA256
`61483C8BC9397843CC2ECDCD8295C220DA9DF6E1A52ED1622EDB69868EA6BA7F`.
The old 0.2.1 JAR is backed up under
`delivery\install-ponder-022-20261024-231444-981`. No configuration, quest,
recipe or world was written, no QA probe was installed and no game was launched.
This changes installation status only; remaining acceptance gaps are not passed.
See `docs/INSTALL_CANDIDATE_20261024.md` and the backup's `ROLLBACK.md`.
Older "not deployed" statements below describe the earlier validation stage.

## Current Checkpoint: Textured BLOCK Lighting Repair

The latest 0.2.2-beta candidate is 857,372 bytes, SHA256
`61483C8BC9397843CC2ECDCD8295C220DA9DF6E1A52ED1622EDB69868EA6BA7F`.
The full build passed 78 suites / 603 tests, with no failures, errors or skips.
All 94 archived files and 330 source entries were independently rehashed against
`validation\verified-outline-20261024-113355-687\receipt.json`.

The completed 4722 single-variable fixture established a narrow inherited
fixed-function lighting defect: BLOCK has no NORMAL element, yet the uploads
received GUI lighting. Lighting-only disable restored textured geometry; depth,
diffuse-only and lightmap-only changes did not provide the same result.
`PreviewBlockLighting` now disables lighting only during baked BLOCK/fluid upload
and restores both raw GL and Minecraft cached state in finally. Seven new tests
are included in 603; diffuse, lightmap and depth remain enabled normally.

The 6148 native fixture run `native-qa-20261024-033625-054-1693c9a6` saved normally
and exited with code 0. Root reviewed the default image captured before any
diagnostic override: glass, water, furnace and stonebrick are visible with their
original textures at the sampled tick. Review:
`validation\native-litfix-review-20261024-61483c8b.json`, SHA256
`B748B0EC5DA2A25FDAB5560074D1CE669FB8432AF33FC75800FFDF55E2DD13D6`.
This is a 1500x800 / GUI2 T+55 fixture check, not full visual acceptance.

The final 6148 run `native-qa-20261024-034042-711-9e0224d0` passed eight selected
vanilla/legacy scene ticks and one crafting-task FTB round trip, with a normal
world save, exit 0 and no forced termination. All eleven native PNGs were
independently reviewed. Native replay passed 8/8, steps 7/7 (1 N/A), skins 6/6
(2 N/A), paging 4/4 (4 N/A), GUI-target health 4/4 (4 N/A); all eight close-time
resource checks passed. FTB returned the same task/chapter, seven scroll panels,
347 task-progress entries and rewards unchanged; footer GL state was restored.
Review: `validation\native-vanilla-review-20261024-61483c8b.json`, SHA256
`85CD7CC5DD2CF7DE466A8C62C073C6F010D02A11322F3CB9E7E84CB878B30EDE`.

This covers only 1500x800 / GUI2 / Unicode, single selected ticks and one FTB
logical input path. Rails T+230 precedes minecart creation and does not validate
the minecart. A later rails point, new-world testing, planned three cold starts,
three reentries, twenty viewer open/closes, further scales and physical input
remain incomplete. The other 306 registered entries were not opened; no MMCE
was opened in this run and no full-MMCE run is required. Runner tool coverage
is separately 67 cases plus six option guards, not additional JUnit tests.
Historical failures remain unchanged and hash-bound. The original instance
remains 0.2.1 and has not been deployed over; acceptance is not yet complete.

## Earlier Checkpoint: Visible FTB Footer And Split Fixture Diagnosis

At this earlier checkpoint, the 0.2.2-beta candidate was 854,840 bytes, SHA256
`4722001D5A37D3E75EF412072A659360E3360AF64BB3E16291E9A49FF9C6EABF`.
The forced full build passed 77 suites / 596 tests, with no failures, errors
or skips. All 93 archived files and 328 source entries were independently
rehashed against `validation\verified-outline-20261024-111644-205\receipt.json`.

Native image review found the 1288 candidate's FTB footer invisible despite its
logical round trip passing. The production footer now uses a self-contained
screen-space foreground pass and restores inherited state. One actual crafting
task was retested on the 4722 candidate: the button is visibly present, the
real viewer opens and returns, seven scroll panels and 347 observed task entries
remain unchanged, and before/after GL snapshots match. The world saved and the
client exited normally. This is a single-task 1500x800 / GUI2 check, not physical
mouse input or all six tasks at every scale.

Evidence: `validation\native-ftb-review-20261024-4722001d.json` and
`docs\CONTINUATION_AUDIO_FTB_20261024.md`. The 1288 three-scene model screenshots
remain tied to their earlier hash. Its single-fixture depth/texture diagnostic
is recorded separately; combined texture/lighting removal restores geometry,
but is not a production fix or an established atlas root cause. A separate
single-variable 4722 fixture run was then in progress; its later result is
recorded in the current checkpoint above. No original-instance deployment
is allowed yet; the unrelated old full-MMCE backlog is not being repeated.

## Earlier Checkpoint: Audio Preflight, Render Isolation And FTB

The latest forced full rebuild passed 76 suites / 590 tests, zero failures,
errors or skips. The 847,715-byte 0.2.2-beta candidate has SHA256
`1288DCE034EB9F9188D9535C69F84F3ED2BE9DBC08CB0F3C57D8842D7339D3A7`.
All 92 archived files and 326 source inventory entries were independently
rehashed against `validation\verified-outline-20261024-104752-134\receipt.json`.
The production changes isolate TESR projection/model-view, viewport and scissor
state, and expose the existing FTB footer click logic to the optional native
round-trip observer without bypassing production pending-action checks.

The vanilla FTB chapter was merged only into the stopped private sandbox:
nine changed files, preserved existing chapters, 16 installer/audit cases passed.
The audio read-only health check (14), runner result contract (47) and actual
Gson null contract (6) are separate tool tests, not additional JUnit tests.
The previous EE crash has evidence of OpenAL initialization failure before
world entry; LoliASM clears the world before printing the resulting sound
exception. The underlying device/context cause is still unknown. See
`docs/CONTINUATION_AUDIO_FTB_20261024.md` for evidence and limits.

The user saved and exited the original instance. A fresh process check found
no Minecraft process before one directed three-scene + FTB private run began
at `validation\p022-native-20261024a\validation-output\native-qa-20261024-030325-172-0f35da15`.
Its results must be reviewed independently; no original-instance deployment
or complete visual acceptance is implied by this offline checkpoint.

## Earlier Checkpoint: Legacy Scene Migration And Bounded Sampling

The user requested review of old demonstrations, then explicitly reduced native
testing to random samples, especially for MMCE. No further complete MMCE run is
required. Finite built-in content was audited across 50 entries / 40 scene IDs;
33 legacy scene IDs were migrated without changing stable IDs or machine data.
Complex ordinary lessons no longer finish with an unexpected camera turn.
Thermal reference lessons now resolve real registered machine states or explicitly
label visible placeholders. The Debug GUI's three AIR anchors were replaced with
visible targets and a continuous true 3D outline. GUI target health is now checked
across all finite built-in entries, not just the six new vanilla lessons.

The latest forced full rebuild passed 75 suites / 580 tests, zero failures/errors/skips.
The 843,999-byte production JAR has SHA256
`EE9678E1C8C55E83BDCCE0B006E413B4F7EF822E1B27C360518A6815577CF2E6`.
All 91 archived files and 324 source inventory entries were independently rehashed.
Receipt: `validation\verified-outline-20261024-071005-314\receipt.json`.

Native testing now uses an actual-registry inventory with separate registration,
compilation, UI-open, capture, capability, not-selected and not-reached outcomes.
The preceding 276C migration candidate sampled 18 unique scenes with seed 20261024, one activity
point each, six vanilla lessons plus the repaired GUI, random old/complex scenes
and at most five MMCE scenes. Identity + seed, strata and all exclusions are saved.
Replays, semantic steps, loaded player skins and actual visible paging are checked
only where applicable; unavailable capabilities are not reported as passes.
It completed 18 captures and saved normally, but pixel review found panels covering
non-target redstone circuitry. The latest EE candidate therefore prefers the
cached current-model geometry envelope as a screen-space placement obstacle,
without changing framing, and fixes old lever controls/labels. Its native follow-up
is limited to three affected GUI/matrix/crafting scenes, zero MMCE scenes; the 18
earlier checks are not transferred to this new hash. Outcomes are recorded in
`docs\ALL_SCENES_REVIEW_20261024.md`, not inherited from earlier candidate hashes.

That final EE follow-up entered the copied world but captured zero PNGs. After
world disconnection it reached a SoundManager null-source-table crash screen;
the last inventory checkpoint was 64 compiled entries and no UI opened. A separate
MMCE background recipe warning also occurred; neither establishes Ponder causality.
After normal window-close tooling failed, only the identity-verified owned crash
process was terminated and the wrapper finished exit -1. This is not a normal QA
shutdown or visual pass. The independent report records actual forced cleanup,
overriding the wrapper's incomplete self-only false flag. No further launch is
scheduled. See `validation\native-directed-review-20261024-ee9678e1.json`.

The preceding A866 candidate compiled 314 actual entries and captured 871/942
points before losing owned-screen identity. It saved normally and exited 0, but
the overall result is false and the remaining 71 points are not being filled.
Its PNGs exposed the Debug AIR-target bug now fixed. Those results do not verify
the new candidate. The original world had independently changed before that run;
the old frozen 04:13 snapshot is not the current original-world state. The drift
observation is preserved without attribution or rollback.

The original instance remains untouched by this turn and still has 0.2.1-beta.
No all-machine, dedicated-server or multiplayer expansion is planned. Earlier
GPU fixture, resolution/scale, FTB roundtrip and deployment gates remain open.
See `docs\ALL_SCENES_REVIEW_20261024.md` for current evidence and limitations.

## Historical Checkpoint: Early 2026-10-24 Native Rendering

Version 0.2.2-beta implements transform-bound selection edges, faint occluded
edges, target-specific foreground text/image panels, multi-target avoidance,
small-screen paging and priority input routing. The six vanilla lessons now bind
captions and images to their actual objects. Existing MMCE, recipes and FTB scene
IDs are retained. External overlay target expansion is now budgeted before allocation.

Native client QA now runs without desktop automation. An isolated copy of the
current instance entered a freshly copied, normally saved user world and produced
real ScreenShotHelper PNGs. The first run exposed an empty crafting output slot;
that product defect is fixed, with valid crafting output visible in the next run.

The revised candidate passed a forced full offline rebuild: 71 suites / 550 tests,
no failures/errors/skips. All 320 source inventory entries were unchanged across
the build. Production JAR: 837,661 bytes, 365 major-52 classes; SHA256
`F7A4D7FF06CEDE67678BE0A693A275CB9C2D3E1125932202C4894F4EDD33F764`.
All 87 archived files and the source inventory were independently rehashed.
Evidence: `validation\verified-outline-20261024-044227-759\receipt.json`.

Three native runs of this F7 candidate exited normally with six actual default
screenshots each (plus one explicitly diagnostic PNG in the final run). The runs
passed 11 automated assertions, but the targeted
glass/water/furnace/stonebrick fixture still lacks clearly visible block models.
The diagnostics confirm nonempty CPU and actual-upload geometry, inactive external
hiders and no improvement from disabled culling; they do not establish correct
GPU pixels. Render acceptance remains open. The
earlier desktop-capture timeout no longer blocks native process/screenshot testing.
See `docs\NATIVE_CLIENT_QA_20261024.md` for evidence and acceptance boundaries.

Read-only checks now find 0.2.1-beta in the original instance, SHA256
`7117170AEBCB48E5AC2B8D95BDC4FA00346451B8F7F6F651ADAB15953882EC06`.
This turn did not install it or modify original mods/config/worlds. The 0.1.0
statements below belong to their dated historical checkpoints, not today's state.
See `docs\OUTLINE_CALLOUT_RELEASE_20261024.md` for changes and remaining gates.

## Historical Checkpoint: 2026-10-23 Vanilla Player Tutorials

The user narrowed the scope to vanilla tutorials, player demonstrations, UI/rendering,
world-entry diagnosis, FTB links and gated delivery. Existing MMCE integration is
retained; further machine/appearance development is out of scope. Dedicated-server
and multiplayer acceptance are not gates for this narrowed client deliverable.

Version 0.2.1-beta passed a forced full rebuild: 64 suites / 483 tests, zero
failures/errors/skips, with identical source inventories before and after the build.
The 791,996-byte production JAR has SHA256
`7117170AEBCB48E5AC2B8D95BDC4FA00346451B8F7F6F651ADAB15953882EC06`.
Artifact audit passed (343 classes, major 52, no bundled tests/dependencies).
Both vanilla and retained MMCE chapter installer tests passed, 25 each.

The new build has NOT run in Minecraft or been installed in the original instance.
Window-capture preflight failed twice, including the single allowed recovery.
The six-quest chapter is staged only; the original instance retains 0.1.0-beta.
World-entry root cause and visual/input acceptance remain open, not passed.
See `docs\VANILLA_PLAYER_RELEASE_20261023.md` and the immutable receipt at
`validation\verified-vanilla-20261023-174511-480\receipt.json`.

## Historical Checkpoint: 2026-10-11

Latest verified build checkpoint: 2026-10-11 22:44:50 (Asia/Hong_Kong).
The outside-model explanation UI and combined browser/parser changes passed
`test build forgeServerShimJar`: 55 suites, 441 tests, zero failures/errors/skips.
The production artifact is `BCB1C2...A7D03`. It subsequently ran in the fresh
candidate-c sandbox from 23:05:49 to 23:10:58 on October 11. Startup indexed
53/53 MMCE machines and published 196 scenes; no world-entry or visible-UI
acceptance was obtained. Both window-capture attempts timed out, so the
coordinator stopped only its verified test PID. The original instance was
not updated. See `docs\P0_CANDIDATE_C_RUNTIME_20261011.md`.
Candidate b's earlier runtime belongs only to `668335...6E8F`.

Goal: finish the Ponder 1.12.2 integration with the current MMCE machines,
usable previews, FTB Quests entry points, and verified runtime stability.

Build at that historical checkpoint:
`C:\CODEX\Ponder-MMCE-Integration\build\libs\ponder_legacy-0.2.0-beta.jar`,
745,541 bytes, SHA-256
`BCB1C2C4CA3177F24083CF28883A094FB75EC69A9E8E4FB06E0B5E489F3A7D03`.
Artifact audit passed: 325 classes, all major 52, production remapping and
required resources present, with no test/JUnit/dependency classes bundled.
The fixed explanation region is implemented and regression-tested; actual
Minecraft pixels, input, world entry and original-instance deployment
remain unverified for this build.

The preceding `668335...6E8F` build checkpoint passed
`test build forgeServerShimJar`, including production JAR remapping and
371 JUnit tests in 49 suites, with zero failures, errors or skipped tests.
That increment was 12 external-parser tests and 12 popup/text-layout tests,
added to the preceding 347. It does not verify the newer source work.
The 25 explicitly bound default Geo resource sets also passed independent
native-library baking. One test chapter containing
53 machine quests is installed and independently audited in `client-sandbox`
and, after its first run, `p0-candidate-20261011a`; it is not 53 chapters.

Three P0 comparison branches were prepared from the same frozen worlds, with
the then-current candidate installed only in the candidate branch. After the user
explicitly resumed testing, the no-Ponder branch was launched on October 11.
Its logs and three world-loaded thread samples show later client execution,
but visual/input acceptance remains incomplete. Physical Escape stopped that
run; the verified test process and launcher are terminal. The user then
explicitly resumed testing again, and the candidate branch launched at
11:30:11 Hong Kong time. It reached client-world load and published all
53 MMCE machine IDs; 14 structure variants and three static appearances
compiled. Its launcher ended the owned process at 11:50:13 with
`reason=runtime-budget`, not a game-crash or graceful-exit result.
The old-version branch remains unlaunched. The older `client-sandbox` and
original instance retain their previous JARs. These results do not establish
that the historical loading failure is fixed.

That run exposed old debug schematics using unavailable Create blocks.
The built-in entry points now use existing vanilla resources, matching
geometry, and an explicit Ponder resource namespace for Thermal Expansion.
The dynamic test's empty selections were also corrected. That
`21B9E0...B3C15` build was subsequently archived while fixing FTB footer and
return lifecycle defects. The subsequent `CA640B...F9E59` build is now archived
after the motion, rendering and navigation review. The preceding
`F484A9...94C1` candidate remains preserved in `p0-candidate-20261011a`.
The verified `668335...6E8F` build was installed in
`p0-candidate-20261011b`, which had been freshly restored from both frozen worlds. Its existing
53-machine chapter fixture has been installed and its independent installed-data
audit passed with zero errors. This branch ran from 21:31:04 to 21:51:06 on
October 11 and ended with `reason=runtime-budget`, `exitCode=-1`.
The final log records client-world load, 53 indexed machines, 4/62 compiled
structural variants and two static appearances. Visual/input acceptance is
still missing, and these used worlds are no longer fresh test inputs.
Each runtime section below is pinned to its own JAR; neither this result
nor the earlier `B0791F...944F0` result applies to the newer source changes.
Before branch b's run, a new explicit human message after the historical
second physical Escape stop resumed isolated client and desktop testing. Authorization
is no longer awaiting another human resumption; the input guard remains in
force, and dedicated-server EULA acceptance is still pending.

## Evidence Baseline

- Plan: `C:\CODEX\Ponder-MMCE-Plan-20261010\PLAN.md`
- Source work copy: `C:\CODEX\Ponder-MMCE-Integration`
- MMCE installed API: 2.3.2.
- FTB Quests installed API: 1202.9.0.15 for Minecraft 1.12.2.
- Machine configuration inventory: 53 distinct machine definitions, now
  reconciled with the candidate runtime index without missing or extra IDs.
  This is registration evidence, not acceptance of all 62 scene targets.
- Last inspected instance log: Cleanroom 0.6.13-alpha, Java 25.0.4.1.
- Original instance Ponder: 0.1.0-beta, SHA-256
  `39B36481BB1C6AB4683045D73C50D1CB2F33370618A46310F56CC2ABF358346E`.

## In Progress

- [x] Create a separate development copy under C:\CODEX.
- [x] Remove automatic deployment from the remap task.
- [x] Enable actual unit tests for new behavior.
- [x] Replace real-world sandbox writes with detached/explicitly supported adapters.
- [x] Move initial registry loading to FMLLoadComplete and publish a complete index.
- [x] Add optional lifecycle timing and unload cleanup.
- [x] Implement registered MMCE machine structures and controller mapping.
- [x] Implement quest-detail viewing without rewards or operator permission.
- [x] Integrate current-state section reveal and fixed camera envelope corrections.
- [x] Exclude native MMCE controller Tile creation from detached previews.
- [x] Build and run the focused automated test suite.
- [x] Merge the 53 machine quests into one isolated test chapter.
- [x] Audit installed chapter hashes, original files, UIDs and scene mappings.
- [x] Make command completion count entries without compiling scenes.
- [x] Add paged captions, compact Debug tabs and paused-frame reuse.
- [x] Isolate caption input from camera and timeline dragging.
- [x] Bake bounded, read-only MMCE static meshes without controller Tiles.
- [x] Integrate explicit Structure / Appearance selection and mesh-based framing.
- [x] Clear model caches on reload and client-world unload.
- [x] Audit the remapped artifact and synthetic installation/rollback behavior.
- [x] Add version-gated MMCE/ZenUtils reload notifications with guarded native reads.
- [x] Roll back MMCE-owned state when the combined Ponder registration fails.
- [x] Refresh open views by published generation and contain manual scene-load failures.
- [x] Resolve FTB links by actual scene ID and compile only the selected component.
- [x] Preserve task-linked scenes across reload without opening a different machine.
- [x] Freeze and verify read-only baselines for both original worlds.
- [x] Create the sandbox identity before optional candidate installation.
- [x] Add a guarded baseline restore tool and verify it with synthetic worlds.
- [x] Restore both real baseline worlds into three independent P0 branches.
- [x] Configure no-Ponder, original-Ponder and candidate variants with backups.
- [x] Complete the full copied-input comparison of the three P0 branches.
- [x] Verify the tightened top-level Ponder JAR location checks.
- [x] Preserve existing fixed-path ForgeStudio diagnostic output with verified inventories.
- [x] Analyze the first no-Ponder run and three verified world-loaded thread samples.
- [x] Launch the `B0791F...944F0` candidate and reconcile all 53 MMCE runtime IDs.
- [x] Capture three identity-verified candidate world-loaded thread samples.
- [x] Preserve the final candidate log and reconcile its 14 compiled variants.
- [x] Install and independently audit the existing 53-machine chapter in the stopped candidate.
- [x] Fix built-in demo resource and selection mismatches with 11 new regression tests.
- [x] Build, audit and install the resource-corrected candidate in the stopped test branch.
- [x] Fix short-quest footer reservation and guard deferred native rebuild frames.
- [x] Repair task returns across native refresh/sync and obsolete back references.
- [x] Build, audit and install the FTB-corrected candidate with 39 additional tests.
- [x] Prepare an isolated minimal Forge server; keep EULA false and do not launch.
- [x] Cover continuous section rotations, directional fades and actor travel in fixed bounds.
- [x] Share motion preprocessing for equivalent sections and test a 5000-block / 120-event scene.
- [x] Correct transparent/multilayer submission, vertex alpha and separated-neighbour faces.
- [x] Isolate native renderer state, extra matrix pushes and exceptional vertex uploads.
- [x] Repair boundary navigation and debug caption paging.
- [x] Build, audit and install the preview-reviewed candidate with 85 additional tests.
- [x] Bound external JSON expansion per file and share parsed definitions within one reload.
- [x] Page group selectors, isolate their input and fit long actor/group labels.
- [x] Invalidate resized popup hit regions and cover real index-refresh cleanup.
- [x] Build and audit the layout/config candidate with 24 additional tests.
- [x] Restore both frozen worlds into fresh candidate branch b and install the new JAR.
- [x] Install the existing 53-machine chapter fixture into branch b without regeneration.
- [x] Complete the independent installed-chapter audit for branch b.
- [x] Complete branch b's bounded run, final 53-machine reconciliation and three thread samples.
- [x] Preserve branch b's changed fixed-path probe diagnostics with matching hashes/timestamps.
- [x] Move explanation text and hint UI into reserved space outside the model viewport.
- [x] Isolate explanation input, preserve GUI image groups, and invalidate stale resize/tab hits.
- [x] Complete the combined 441-test build/audit for explanation UI, browser and parser changes.
- [ ] Verify camera, rendering, input routing and layout in the real client.
- [ ] Run the game on cloned worlds and verify startup/first frame/interaction.
- [ ] Verify representative scenes and reconcile all machine definitions.
- [ ] Verify multiplayer, optional dependency absence and rollback.
- [ ] Deploy a runtime-verified JAR to the original instance with backups and a single active mod version.

## Build And Review Notes

- Latest command:
  `scripts\gradle-jdk25.cmd --no-daemon --offline test build forgeServerShimJar`.
  `validation\build-explanation-ui-20261011-224138-449.log` records
  `BUILD SUCCESSFUL in 41s`: 13 actionable tasks, 6 executed and 7 up-to-date.
  Production compilation, JAR remapping, test compilation and tests executed;
  the unchanged server shim was up-to-date. All 441 tests in 55 suites passed,
  with zero failures, errors or skips. This is 70 more tests than the old
  371-test checkpoint, not a sum of focused reruns.
- New artifact audit:
  `validation\artifact-audit-explanation-ui-20261011-224308-722.json`.
  Production SHA-256 is `BCB1C2C4CA3177F24083CF28883A094FB75EC69A9E8E4FB06E0B5E489F3A7D03`,
  745,541 bytes, 325 classes, all major 52; no tests or dependency classes
  bundled. The audit validates the existing artifact, not game compatibility
  or source freshness by itself.
- The four new JARs, all 55 XML reports, build log and artifact audit are
  frozen as 61 hash-verified files:
  `validation\build-explanation-ui-verified-20261011-224359-473\receipt.json`.
  All 61 preserved files were independently rehashed with zero mismatches.
  The separate 286-entry `source-inventory.json` is preserved with SHA-256
  `671A22D6AFF53CC212FF3AFD99BCBD96490B4980F29380E1D88BECEA31F14C02`;
  all 286 current source entries also matched. This is not a reproducible-build claim.
- The first full attempt had 433 tests in 54 suites and one failing browser
  index-refresh fixture. It assumed a single column at width 320 and seeded
  an out-of-range scroll value after responsive columns changed. The fixture
  now uses actual columns and enough entries; ID-anchor assertions were retained.
  Failed log/JAR/XML evidence remains in
  `validation\build-explanation-ui-failed-20261011-222928-889\receipt.json`.
  Subsequent hover/resize coverage is included in the final 441-test run.
  The earlier focused 86-test/11-suite result is preserved in
  `validation\explanation-ui-focused-20261011-222544-276\receipt.json`
  and is not added again to the final total.
- The new artifact has not replaced either tested candidate b or the original
  instance. Their independently rechecked hashes remain `668335...6E8F` and
  `39B364...346E` respectively. No game was launched during this build
  checkpoint; the later candidate-c startup is documented separately.
- Verified 371-test checkpoint command, not a result for the newer source:
  `scripts\gradle-jdk25.cmd --no-daemon --offline test build forgeServerShimJar`.
  Gradle reported `BUILD SUCCESSFUL`; all 371 JUnit tests in 49 suites executed
  successfully, with zero failures, errors or skipped tests.
  Log: `validation\build-layout-config-20261011-132007-434.log`.
  Summary: `validation\layout-config-test-summary-20261011-132107-200.json`.
  The complete rerun executed `compileTestJava` and `test`; production
  compilation/remapping and the unchanged shim were already up to date from
  the first attempt.
- JUnit evidence: `build\test-results\test\TEST-*.xml`; HTML report:
  `build\reports\tests\test\index.html`. Mathematical assertions within test
  methods are not additional JUnit test cases. Independent reruns of a subset
  of these tests are not added to the total. These live build paths can change;
  the checkpoint's preserved receipt/XML, not future live files, bind its result.
- Runtime-tested layout/config artifact:
  `validation\p0-candidate-20261011b\mods\ponder_legacy-0.2.0-beta.jar`, SHA-256
  `668335186DFE2EB344372494BC825AEF09D4FD58B6C39B7F831859C1A8C36E8F`.
  This layout/config build is installed only in candidate branch b.
  It ran from 21:31:04 to 21:51:06 and has bounded log/thread evidence, not
  visual acceptance. It is not installed into the original instance.
  Do not infer this hash from a later mutable `build\libs` output. The historical
  `B0791F...944F0` build launched at 11:30:11.
- Earlier layout/config artifact audit: 320 classes, all major 52; command overrides remapped to SRG;
  required resources and client bridges present; no test classes, JUnit or
  compile-only dependency classes bundled. Evidence:
  `validation\artifact-audit-layout-config-20261011-132107-226.json`.
  The older artifact audit remains preserved separately.
- Java 8 bytecode does not establish stock Forge-client support. The existing
  client package retains its Cleanroom metadata and JAVA_16 mixin setting.
- The separate server shim was up-to-date in the 371-test build. Its shared plugin API contains
  the new default lifecycle hooks; its no-op index does not invoke them or
  acquire client/MMCE dependencies. Shim SHA-256:
  `28CCA3699A2E81EA77DCB9AFC073532A4796E90EEFF5348B03AE7CA9C605AAF3`.
- The older `client-sandbox` still contains the earlier candidate with SHA-256
  `D450D0A9D26261B1D7CEB9110CC12F21BE61D86C3A2DA29A3ADEA87126A340E2`.
- `p0-candidate-20261011b` retains the tested `668335...6E8F` JAR.
  Its installation transaction is `committed` in
  `validation-output\previous-ponder-20261011-132228-294-584ea027`.
  The branch's original `39B364...346E` package is archived there and the
  transaction records `originalInstanceChanged=false`.
  Build and installed hashes matched at installation; the installed hash was
  rechecked after the run.
- `p0-candidate-20261011a` retains exactly one active Ponder JAR,
  `F484A9...94C1`. Its installation transaction is `committed` in
  `validation-output\previous-ponder-20261011-063043-930-00403fc1`.
  The unlaunched `CA640B...F9E59` package is archived there.
  `21B9E0...B3C15` remains in `previous-ponder-20261011-051615-250-0533284e`.
  The tested
  `B0791F...944F0` package remains in the earlier
  `previous-ponder-20261011-041022-893-52009b98` archive.
  The earlier original-package backup is retained in
  `previous-ponder-20261010-191233-562-7f7481d8`. These are isolated
  installations, not original-instance deployment or runtime acceptance.
- All 49 JUnit XML files and four JARs from that checkpoint are preserved with
  53 matching hashes in
  `validation\build-layout-config-verified-20261011-132145-661\receipt.json`.
  The initial 371-test attempt had four fixture failures: three expected the
  missing-tag placeholder in `ponder` instead of `ponder_legacy`, and one
  called real `initGui()` before initializing the test I18n locale.
  Fixture setup/expectations were corrected without removing the behavior
  assertions, then the entire suite passed. The failed log, XML and JARs
  remain preserved:
  `validation\build-layout-config-failed-20261011-131842-164\receipt.json`.
- The preceding full 347-test XML evidence and all four corresponding JARs
  are preserved with 50 matching hashes in
  `validation\build-preview-review-verified-20261011-063350-227\receipt.json`.
  Previous 262-test evidence is retained in
  `validation\build-before-motion-review-20261011-053049-614`.
  That version's post-install report verifies its JAR, archived predecessor,
  all 56 chapter output hashes and unchanged original-instance JAR:
  `validation\preview-review-postinstall-20261011-063133-237.json`.
- The preceding preview-review's FTB parser audit rechecked candidate a's
  existing chapter with zero errors: 185 SNBT files, 56 output hashes and
  107 new UIDs. That audit did not regenerate or reinstall a chapter:
  `validation\audit-candidate-preview-review-20261011-063132-892.json`.
- Before the FTB changes, four JARs and all 37 suites / 223-test XML results
  were preserved with matching hashes in
  `validation\build-before-ftb-return-20261011-044948-978`.
  That version's post-install check verifies its JAR, archived predecessor,
  all 56 existing chapter output hashes and unchanged original-instance JAR:
  `validation\ftb-return-postinstall-20261011-051702-534.json`.
- Before rebuilding, all four prior build JARs and the 212-test XML results
  were preserved and hash-checked in
  `validation\build-before-vanilla-scenes-20261011-035746-882`.
- The original instance JAR hash was rechecked after the build and remains
  `39B36481BB1C6AB4683045D73C50D1CB2F33370618A46310F56CC2ABF358346E`.
- The chapter installation transaction
  `validation\transactions\client-sandbox-20261010-153801-054-62c30129`
  is `committed`. It records 53 machine quests in chapter `1158d37c`, protected
  root `file.snbt`, and `originalInstanceChanged=false`.
- After the candidate stopped, the same fixture was merged into it with
  transaction `p0-candidate-20261011a-20261011-035123-674-fb340076`.
  Its independent `validation\audit-candidate-chapter-20261011-0351.json`
  passed with zero errors: 56 output hashes, 185 parsed SNBT files, 107 new
  UIDs without collision, 53 quest/task mappings and 62 targets per set.
  Original-instance quest files remain unchanged. The preceding candidate
  game run did not contain this chapter.
- A fresh read-only chapter audit passed:
  `validation\audit-installed-20261010-171145-607.json`.
  All 56 installed output hashes match; 185 SNBT files parse with the installed
  FTB parser; 107 new UIDs do not collide with 374 reserved IDs; all 53 quest
  mappings and 62 scene targets match. Original chapter order, root file and
  original-instance task files remain unchanged. The expanded audit's 27
  synthetic regression cases also passed:
  `validation\audit-regression-results-20261010-183219-444.json`.
- Main-source compilation passed with the installed Java 25 toolchain and
  Java 8 target bytecode.
- The offline template build initially lacked Blossom runtime jars; an online
  dependency resolution succeeded.
- Unit-test compilation exposed a missing test-only Gson dependency, now added.
- The full build exposed duplicate generated `mcmod.info` in `sourcesJar`;
  the sources archive now has an explicit duplicate strategy.
- The cached JUnit 5.7.1 / platform launcher 1.7.1 are explicitly pinned.
- `libs/api/CraftTweaker2-1.12-4.1.20.699.jar` is a compile-only copy of the
  installed API, not bundled in the release. SHA-256:
  `0C7D4B303322604C62BED0494F45EA0104C10FDBD4B54DB777228C05C134EA50`.
- Camera/UI source changes are integrated; actual viewport, model and input
  acceptance is still pending. The new lifecycle tests initially exposed a
  missing offline `Bootstrap.register()` in their setup. Setup was corrected
  and the complete suite rerun successfully; this was not a game-start test.
- `-Dponder.diagnostics=true` logs reload duration, slow Ponder client ticks,
  client-world lifecycle and the first render tick without a loading screen.
  The render log is diagnostic evidence, not proof of usable gameplay.

## Earlier Implementation

- `/ponder dump` completion now reads registered entry counts, including item
  aliases and deduplication, without loading schematics or invoking storyboards.
- Captions have a bounded, paged reading area. Reading pauses playback and owns
  its wheel/key input. Compact Debug layouts use Preview / Details / Timeline
  tabs and clear obsolete hit regions. Paused frames retain evaluated state
  and classified block lists instead of replaying NBT every frame.
- MMCE appearances are explicit, lazy and static. Structure remains the default.
  Missing bindings or unsupported resources retain the ordinary structure with
  a specific status; no filename-based binding inference is used.
- Model baking uses fresh Gecko data objects and a private matrix stack. The
  control-block Tile guard remains intact. No native model pool, renderer,
  machine event, animation controller or global render queue is invoked.
- The first explicit successful appearance request fits the union of structure
  and actual mesh bounds. Later mode switches preserve framing and zoom.
  Resource invalidation returns to structure and requires another explicit
  request. Dynamic maximum variants do not resize the single static shell.
- Controller selection bounds no longer enlarge the pure structure envelope.
  This has an actual scene-runtime regression test using an oversized box.
- Rendering review caught and fixed an unpaired scissor attribute push and
  incomplete client-array restoration. LWJGL 2/3 vector-query names and the
  legacy 16-entry buffer requirement now have compatibility tests. This is
  source/API evidence, not real GL-context acceptance.
- Successful and failed mesh entries are invalidated on resource reload,
  explicit registry reload and world unload. Screen disposal releases its
  held preview and private rendering buffer.

## Reload And UI Continuation

- Automatic refresh is restricted to the locally audited MMCE 2.3.2,
  ZenUtils 1.28.7 and CraftTweaker combination on the physical client.
  Native Pre/Post signals queue one client-END-tick Ponder refresh. This
  observes a local reload; it is not a new multiplayer synchronization protocol.
- Pre cannot enter MMCE mutation while Ponder is copying native metadata,
  a structure, or default-model metadata. While reload is active, new reads
  are refused immediately; existing immutable snapshots remain usable.
  The full plugin reload never runs under this native-read monitor.
- Metadata is copied into staging data and published only after the guarded
  read succeeds. Lazy native access also checks a source epoch, preventing
  old machine objects from being reused after the next native reload.
  The index scan now checks the existing soft time budget.
- An early implementation used the native nonvolatile ScriptStatus field.
  Review reproduced a race with the next server-thread Pre. That design
  was replaced by the shared read guard and epoch queue before this build.
- Full Ponder reload uses one fixed plugin list, paired start/end hooks and
  reverse cleanup. MMCE checkpoints restore metadata, failure notes,
  snapshot LRU order, generation/source epoch and scene associations after
  registration failure. Serious errors propagate after the remaining
  cleanup is attempted; recursive reload is rejected through cleanup.
- Open browser/tag/viewer screens update once per published generation.
  Valid component/scene IDs, filters, scroll anchors, playback and framing
  survive. Changed bounds are fitted once without resetting manual rotation.
  Removed entries and bad scene compilation produce a safe empty/fallback view.
- Manual component switching now shares the same compile/bounds failure
  handling. Closing or rebuilding a viewer releases owned world, mesh,
  scene and click-state references. Return bookmarks contain only IDs and
  values. Actual FTB page-return input remains an unverified runtime gate.
- Reload and generation-refresh JUnit coverage is included in the old 371
  checkpoint and was rerun within the new 441 total,
  not counted again as independent test runs. Earlier review subtotals are
  historical and are not additional test cases in the new full suite.
  Source-isolation assertions are not a replacement for live event dispatch.

Contracts and independent evidence:
`docs\MMCE_REGISTRY_READ_GUARD.md`,
`docs\MMCE_SCRIPT_RELOAD_HANDOFF.md`,
`docs\MMCE_SCRIPT_RELOAD_CONCURRENCY_AUDIT.md`.

## Stable FTB Scene Links

- Explicit `sceneId` resolves `PonderScene.getSceneId()`, not schematic names
  or the scene's current list position. Missing or duplicate identities reject
  the link with a disabled button and a translated reason.
- Canonical legacy MMCE ordinals 0/1 retain compatibility through verified
  `/minimum` and `/maximum` IDs. Ambiguous `/binding_N` component paths require
  an explicit scene ID; no machine-path suffix is guessed or stripped.
- `QuestPonderUI` reuses the validated scene list on first open. The default
  route compiles only the clicked component, never all machines while drawing
  a quest page. Reload retains the selected scene ID within the linked machine;
  removal does not redirect to an unrelated machine. Close releases references.
- The legacy custom `SceneOpener` extension still receives the resolved list
  position and must use it synchronously within that generation. The default
  production route does not use this extension.
- The generator now emits explicit IDs, but was not rerun. Existing installed
  data remains the audited canonical legacy form: 53 quest mappings and 53
  task mappings, each containing 62 targets. The identity change did not
  regenerate them; the later stopped-candidate installation is recorded above.
- Merge planning, link auditing and installed auditing compare stable identity,
  so a correct explicit ID takes precedence over a stale ordinal. Wrong IDs
  are rejected even when their ordinal is otherwise correct.
- Both the link auditor and the real merge planner passed five synthetic
  identity cases each, including ID-only maximum scenes, obsolete ordinals,
  bad link IDs and bad manifest IDs. The planner only staged its outputs.
  Evidence: `validation\ftb-scene-link-tests-20261010-184732-032\report.json`.
- The chapter installer passed 22/22 synthetic regressions after the final
  identity-priority correction:
  `validation\installer-test-results-20261010184216.json`.

Contract: `docs\MMCE_FTB_QUESTS_INTEGRATION.md`. These source and offline test
results do not establish real-client navigation, layout or reward preservation.

## FTB Footer And Return Corrections

- Native short task details can be only 60 GUI pixels high. Reserving 26
  pixels inside them previously hid the action even on a large screen.
  The panel now grows and recenters within available screen bounds, retains
  the 20-pixel minimum content area, and restores its native dimensions when
  the integration leaves or is disabled. Repeated draws do not add height.
- Pre/Post draw snapshots detect FTBLib child replacement or geometry changes.
  A rebuild frame does not draw a footer over already-rendered native content;
  an undrawn or stale footer cannot intercept the next task click.
- Return handling resolves the authoritative `ClientQuestFile` and tree before
  wrapper initialization. It restores only a current, visible task/chapter,
  respects unrelated active selections, rejects same-file UID replacement,
  and falls back without opening invalid old data.
- Native same-file refresh migrates the original chapter but not its task.
  That inherited selection is distinguished from choosing another chapter.
  Scroll restoration stops if the authoritative tree changes, including when
  another listener cancels opening its replacement.
- A tree created while Ponder is open can capture that viewer as its previous
  screen. Only this exact stale viewer/parent reference is repaired; unrelated
  back destinations remain untouched. The installed FTBLib has no public
  setter, so the verified private `GuiBase.prevScreen` field is used.
  Unsupported reflection now disables the optional bridge rather than
  propagating an initialization-time runtime exception.
- Added 12 footer lifecycle tests, 25 return lifecycle tests and two layout
  tests, for 39 additional JUnit cases. They exercise production reflection
  and event handlers against version-shaped fixtures, not a live FTB client.
  Fake progress/reward maps staying unchanged is not real-player acceptance.
- Initial validation exposed abstract-GuiScreen fixture compilation and
  Forge's missing test-JVM event transformation. Those fixtures were fixed.
  Two further native-lifecycle cases reproduced failures before their source
  correction: `validation\test-ftb-return-regressions-20261011-051334-777.log`.
  The final full build then passed all 262 tests; earlier failed build logs
  remain preserved and are not described as successful runs.
- No task UID, installed chapter data, machine definition, recipe or world was
  changed by these source fixes. Actual repeated navigation and reward-state
  acceptance remain pending.

## Preview Rendering Corrections

Continuous bounds now include ordered multi-axis section motion, directional
show/hide offsets and visible actor travel. Equivalent section timelines share
preprocessing. The 5000-block / 120-rotation-event regression completed in
0.143 seconds for the whole JUnit case on this machine. This is an offline
preprocessing check, not a GPU frame-rate measurement.

Transparent models sort far-to-near; faded and multi-layer blocks use the
appropriate passes and actual vertex alpha. Separated sections expose their
formerly shared faces. Native TESR calls restore driver and cached GL state,
client arrays, and extra model-view pushes. A renderer that destroys lower
caller matrix history is reported, not claimed recoverable. Authored rail
state handling and MMCE controller Tile isolation remain intact.

Boundary navigation no longer restarts the same scene. Debug captions now
use the shared paging and input ownership rules. Eighty-five additional
tests passed in the preceding 347-test run. The first full attempt had one
test-resource namespace mistake; its failed log/XML are retained separately.
The corrected test loads the real bundled section scene and checks its
moving corners against the fixed envelope.

Detailed scope, regression logs and verification limits:
`docs\PREVIEW_RENDER_REVIEW_20261011.md`.

## Layout And Config Review

The following implementation/test evidence describes the earlier
371-test `668335...6E8F` checkpoint. Its counts are retained as historical
evidence; the subsequent combined build has 441 tests.

- External JSON files now share a 16,384-operation expansion budget across
  every scene and invocation in that file, with a maximum nesting depth of
  64. Reference cycles remain rejected. An invalid/over-budget file contributes
  no partial scenes or tags; unrelated files remain independent.
- The external plugin uses one parsed definition snapshot for scene and tag
  registration within a reload. Successful and failed reloads both release
  it, the next reload rereads files, and standalone registration does not
  retain a permanent cache.
- Group popups use bounded, shared draw/hit geometry with reachable pages.
  Their clicks, wheel events and keys own input while open. Page changes
  replace hit targets immediately, resize initialization invalidates old
  coordinates, and index replacement/closure clears the selector.
- Actor labels and group hints are trimmed with the active font before
  positioning. Chinese glyphs, formatting and variable-width metrics are
  covered; actual resource-pack/font-mod rendering remains a client gate.
- The latest full run includes 12 parser tests, nine production-popup
  callback/geometry tests and three text-layout tests. Existing navigation
  tests now keep the popup closed when testing scene-boundary no-ops.
  Independent review verified the resize and index-removal paths after
  their final corrections.
- Fresh branch b restored two frozen worlds, 164 files / 16,461,843 bytes.
  Its completion receipt records unchanged original worlds and baseline:
  `validation\world-restores\p0-candidate-20261011b-20261011-131903-459-789630bd\receipt.json`.
- Its chapter transaction is `committed`, reusing the existing fixture:
  `validation\transactions\p0-candidate-20261011b-20261011-132246-851-2ef3c6fc\transaction.json`.
  The installed FTB parser independently audited 185 SNBT files, all 56 output
  hashes, 107 new UIDs, 53 quest/task mappings and 62 targets per mapping set
  with zero errors:
  `validation\audit-candidate-b-layout-config-20261011-132437-693.json`.
  This is read-only file/data evidence, not quest-navigation acceptance.
- Branch b subsequently launched and ended at its runtime budget. Its final
  evidence is summarized below and in `docs\P0_CANDIDATE_B_RUNTIME_20261011.md`.
  Human continuation does not establish runtime success or accept the server EULA.

Full scope, preserved failure evidence and current acceptance boundaries:
`docs\LAYOUT_CONFIG_REVIEW_20261011.md`.

## Outside-Model Explanation UI

The latest user requirement is that explanation text and hint UI remain
outside the model: blocks must not obscure the text, and text must not
cover the model's block information. This is now implemented in the
`BCB1C2...A7D03` build, with a content-independent right-side explanation
region for normal showcase and a bottom region for Debug preview.

- Active text, control hints, actor labels, point-of-interest descriptions,
  notices and hover help are collected outside the model instead of drawing
  over blocks. GUI snapshot images have reachable pages in the same region.
- Parent/child GUI image groups share a uniform scale and retain their
  relative positions; cyclic parent references are handled without recursion.
- Explanation drawing is a depth-independent 2D pass with native render
  state isolation. Model clipping and camera fit use the actual reserved
  viewport; obsolete caption insets are removed.
- Explanation click, wheel and Page-key input owns its region and pauses
  playback rather than reaching the camera/timeline. Resuming playback
  releases hover text; resize and Debug-tab changes clear stale hits/chrome.
  Large-font and full-height-pane pagination are covered.
- Responsive browser columns, long descriptions and bounded hover hints
  are corrected. Parser validation now charges repeated execution and
  selection targets in addition to expansion nodes, with a per-file
  262,144-target budget and overflow-safe coordinate/volume checks.
  These bounds are not a global wall-clock limit for every possible storyboard.

The combined suite, final read-only review and artifact audit found no
blocking issue within this change's tested scope. Those are source/offline
results, not a physical-client visual pass. Detailed evidence is in
`docs\LAYOUT_CONFIG_REVIEW_20261011.md`.
No game was launched or original-instance package replaced for this build.
The previous screenshot-capture failures remain an open visual-validation
limitation, not a passing result or a new physical-Escape stop.

## Physical Server Preparation

- A minimal Forge 14.23.5.2860 / Java 8 server input set is prepared at
  `validation\forge-shim-server-20261011-045224422Z`.
  The input manifest records 24 hash-verified JARs and three configurations;
  all 27 payload files were independently rechecked.
- The only mod is the unchanged server shim. Configuration binds
  `127.0.0.1:25580`, retains online authentication, disables Query/RCON/snooper
  and uses a new disposable world name. Port availability must be rechecked
  before any future launch.
- `eula=false` remains intact. The user was asked for EULA confirmation;
  no response or acceptance has been recorded, and no server was launched.
  Manifest SHA-256:
  `49FE0170BABD45F158EB7F58830703FCB3976D2C6DED17EDB6DE7C35D2166A77`.
- During the earlier server-preparation continuation, process inspection
  found no Minecraft process.
  A read-only launcher screenshot attempt failed while minimized; the
  prescribed activation/reselection retry failed with `FrameArrived timed out`.
  No further client launch, input bypass or visual-acceptance claim followed.
  A later separate Computer Use operation reported another physical Escape
  stop. A newer explicit human continuation now resumes isolated client and
  desktop testing; this does not change the server's pending EULA boundary.

## Frozen World Baselines

- Both original worlds were captured without changing their sources: 164 files,
  16,461,843 uncompressed bytes. ZIP entry hashes, complete archive hashes and
  before/after source inventories agree.
- Current baseline:
  `validation\world-baselines\worlds-20261010-182341-688-f9727f82`.
  Its `manifest.json` SHA-256 is
  `B41BDEBB16D94EDC412BB779D9775F027378A4BB563762DFE148AA72CBE31604`.
- The capture tool's 27/27 synthetic checks passed:
  `validation\world-baseline-tests\20261010-182236-695-33650dc5\report.json`.
- This is stopped-instance file-level evidence, not an atomic VSS snapshot,
  NBT validation, or a successful world-entry test.
- `Restore-WorldBaseline.ps1` now supports restoration into a freshly prepared
  sandbox without saves or prior output. It checks pinned input hashes, path
  isolation, every ZIP entry, restored files and late process state, and
  publishes a non-overwriting completion receipt.
- Its 66/66 synthetic regressions passed:
  `validation\world-restore-tests\20261010190057-6befa4\report.json`.
  Review found and corrected staging/input overlap and final verification
  order. The tests exercise those cases and receipt failure after publication.
- Both real baseline worlds were subsequently restored into
  `p0-none-20261011a`, `p0-old-20261011a` and `p0-candidate-20261011a`.
  Each independent successful receipt records 164 files, 66 internal
  directories and 16,461,843 bytes. Original worlds and the frozen capture
  manifest were not rewritten. The manifest's historical
  `restoredToTestBranch=false` is superseded for restoration status by the
  later receipts, not by editing that manifest.

Capture/restore contracts and archive hashes: `docs\WORLD_BASELINES.md`.

## Prepared P0 Comparison

- Three fresh branches were copied from the original instance, not from the
  previously launched `client-sandbox`. Each Prepare operation copied 28,736
  files / 1,102,150,771 bytes before baseline restoration and variant setup.
- The branches contain no Ponder, the original `39B364...346E` JAR, and the
  then-current `B0791F...944F0` candidate respectively. No-Ponder and candidate
  old-package backups are preserved outside active mods paths.
- All three uniformly quarantine one obsolete MMCE 2.1.5 patch whose target
  is absent and retain four applicable patches. This addresses a known
  isolated-launch prerequisite; it is not proof of the original freeze's cause.
- Configuration receipt:
  `validation\p0-comparison-20261011a\configuration.json`.
  This records `configured-awaiting-comparison-audit`, `comparisonVerified=false`
  and `gameLaunched=false` at preparation time and remains unchanged.
- The later full comparison passed with zero failures:
  `validation\p0-comparison-20261011a\report-20261010-192627-298-14b5c259.json`,
  SHA-256 `7C49AA14531A3C5FF206C639EADA1F2A991C738D6FDD4C410CE2648BF407845C`.
  It checked 28,736 selected source files, 30,147 normalized copied entries
  per branch, 299 non-Ponder files under mods, and 492 restored world-file
  records. All three normalized copied-inventory hashes match.
- Beyond the variant and generated files, the recorded differences are only
  the uniform obsolete-patch quarantine and the exact Prepare transformations
  of research-progress-reset paths, multishot output and window options.
  This is file-level evidence, not an atomic snapshot or runtime-equivalence
  proof. The initial audit source is retained as `Audit-Comparison.initial.ps1`;
  review tightened top-level Ponder locations after this full run.
- The follow-up uses the updated audit's actual AST predicates. Its 10/10
  in-memory cases passed, including nested/duplicate/wrong-hash JARs and a
  jointly altered old-JAR/configuration path. The historical report and the
  live original/none/old/candidate locations passed; current Ponder counts
  are 1/0/1/1, with the pinned paths and hashes.
  Evidence:
  `validation\p0-comparison-20261011a\ponder-path-supplement-20261010-193321-085-e6582099.json`,
  SHA-256 `93D60293AF325FF9D96CA6D0F7B2E6A34F7E7B2A2F42393B666433992CEDB09F`.
  The supplement preserves and hashes the original report, initial and current
  audit sources, and unchanged configuration. It did not repeat all file I/O
  or establish that every other input remained unchanged between checks.
  These are PowerShell verification cases, not additional JUnit tests.
- At preparation and during the initial runs, no generated machine chapter
  was installed in these three branches. The candidate received it only
  after the 11:50:13 stop; its transaction and independent audit are recorded
  above. Do not rewrite the earlier comparison as testing that chapter.
- Read-only dependency inspection found no declared hard Ponder dependency
  among the other installed top-level JARs. Embedded JARs were not recursively
  inspected, and this is not physical optional-dependency acceptance.
- Describe-only launcher checks passed for all three branches without starting
  Java. They use the same 100 classpath entries, Java 25 executable, offline
  identity, 12,288 MiB heap, 1500 x 800 window and 20-minute/64-MiB budgets.
  The launcher's `Worlds` description reads the immutable Prepare-time list
  (empty for `-SkipWorlds`); the later restore receipts, not that field,
  establish the two restored worlds.
- The copied ForgeStudio probe and research bridge share their configurations,
  but the probe's embedded original-instance path disables its survey in all
  three sandboxes. Baseline mode does not disable the probe in the original.
  There it can capture CT/load-complete stages and, after world/player become
  available and 40 client END ticks, world-ready data. Complete environment
  fingerprint verification occurs later; missing old pins cannot be treated
  as an early bypass of that sampling.
- Those synchronous stage captures have row/byte limits, not a wall-clock
  budget. The old research fingerprint is rejected before registry changes,
  and initialization/check failures are caught instead of escaping to Forge.
  This establishes neither negligible runtime cost nor the freeze's cause.
  Do not selectively bypass checks or patch other mods to make the comparison
  pass. Original-instance-equivalent probe behavior remains a separate gate.
  Its existing fixed diagnostic output has now been preserved. Recheck the
  source for new or changed output before any later authorized game launch.

- Probe diagnostic preservation completed without modifying its source:
  `validation\probe-evidence-20261010-194104-127-50e61e0c\receipt.json`,
  SHA-256 `3E5372569722670058A253C3B982211C853B304D07C9438C9828A41E69CCB0BF`.
  It records 599 files, 313 subdirectories and 290,046,132 bytes. Source
  before/after and destination inventories matched exact paths, sizes,
  SHA-256 and file timestamps. No Java or game was launched. This is
  file-level preservation, not an atomic snapshot or a persistent lock.

Probe behavior was verified against the installed bytecode and matching
local source in
`C:\CODEX\Modpack-Forge-Studio\workcopies\twelve-device-rebaseline-20260930\src\forge_studio\probe`
(`RecipeProbe.java`, `PhaseSelection.java`, `ReadOnlyStages.java`,
`FileEvidence.java`). No probe code or configuration was changed.

## First P0 Runtime Observation

The user explicitly resumed isolated testing after the earlier stop.
`p0-none-20261011a` launched on October 11 at 10:59:09 Hong Kong time,
without a Ponder JAR. Its log records integrated-server startup at 11:04:01,
player login at 11:04:12 and subsequent chat, quest GUI and game-menu activity.
Three process-verified snapshots at 11:05:59, 11:06:14 and 11:06:28 show
different client rendering paths and server-loop sleep, with increasing CPU
counters and no Java deadlock report. They do not prove usable visible frames
or exclude intermittent/native graphics problems.

Computer Use screenshots failed, F2 input was rejected by the user-input
guard, and physical Escape stopped automation again. The verified test
process was terminated and its launcher completed with `operator-stop`.
No other P0 branch had launched at the end of that run, no candidate
acceptance was recorded, and
no original-instance deployment occurred. Preserve the now-used no-Ponder
branch; use a fresh restored branch for any repeat cold comparison.

Evidence and exact limitations:
`docs\P0_NONE_RUNTIME_20261011.md`.

## Earlier Candidate Runtime Observation

After the next explicit user resumption, `p0-candidate-20261011a` launched
with the then-current `B0791F...944F0` candidate, not the no-Ponder variant.
Its log records the 53-machine index with zero failures in 35 ms, registry
publication in 89 ms, integrated-server startup at 11:34:25, client-world
load at 11:34:37, and a render tick without a loading screen at 11:34:38.

The initial immutable `mmce-reconciliation.json` covers the registration-time
log snapshot before any preview compilation. All 53 configuration IDs match
the published runtime index with no component/dynamic mismatch or evidence
issue. The final log shows successful compilation of 11 machines / 14
structure variants and three explicitly requested static appearances. No
Ponder preview-compilation failure has been found in this run.

Three process-verified thread dumps at 11:37:16, 11:38:07 and 11:38:49 show
different client drawing paths and server-loop sleep, without a Java deadlock
report. Their actual intervals are about 51 and 42 seconds. This is neither
a continuous performance profile nor visible-frame or input acceptance.

Computer Use screenshots failed and F2 input was rejected by the user-input
guard. No candidate screenshot has been obtained. The actor causing the
logged UI changes has not been verified. A later Computer Use operation
reported a new physical Escape stop. The earlier resumption does not
authorize desktop control after that stop. A subsequent explicit human
continuation has now resumed isolated client/desktop testing. This changes
the authorization state, not the missing visual/input evidence for that
historical run.

Run directory:
`validation\p0-candidate-20261011a\validation-output\20261011-033010-490`.
The candidate had only original quest data during this run. Its test chapter
was installed after shutdown, with independent file/data audit but no game
launch under the new quest configuration.

The final reconciliation preserves the initial report and matches all
975,357 bytes of the closed stdout log. Remaining 48 structure targets
have no compilation evidence. The last seconds also exposed 23 missing
`create:*` block warnings in old debug schematics; this is a separate demo
resource compatibility issue, not an MMCE failure or a proven freeze cause.
Exact timeline, final hashes, probe preservation and post-run quest evidence:
`docs\P0_CANDIDATE_RUNTIME_20261011.md`.

## Candidate B Runtime Observation

`p0-candidate-20261011b` ran the `668335...6E8F` artifact with its installed
53-machine quest chapter on October 11, 21:31:04-21:51:06 Hong Kong time.
PID 19748 is terminal. `result.json` records `runtime-budget` and exit code
-1, not a normal exit or evidence of a game crash.

The closed stdout log records client-world load at 21:35:31 and a render
tick without a loading screen at 21:35:33. All 53 machine IDs match the
configuration, with zero index failures. Four of 62 structural targets
compiled: blockcutter, windgenerator, steamboiler and orewasher, all MINIMUM.
Blockcutter and orewasher also compiled static appearances. The other
58 targets have no compilation evidence; every render/interaction status
remains unverified. FTB loaded 4 chapters and 178 quests, but no
quest-button/return acceptance is inferred.

Three identity-verified world-loaded thread samples at 21:38:11, 21:39:56
and 21:45:54 show changing client item-rendering paths and server sleep.
There are no Java deadlock reports or `BLOCKED` threads in those samples.
Native graphics/input problems are not ruled out. Window captures failed,
the input guard was respected, and the logged GUI actions were not issued
as coordinator clicks or key presses.

Run directory:
`validation\p0-candidate-20261011b\validation-output\20261011-133104-009`.
The final reconciliation matches the closed 965,937-byte stdout SHA-256
`829C64748E32318E5EF48E065A933E3F847948AED6F25AE9A630932F5D3387B9`.
The post-run probe inventory grew from 601 to 602 files; one startup
session file was new and `startup-latest.json` changed. Both were preserved
with matching hashes/timestamps and unchanged source-before/after inventories:
`validation\probe-evidence-post-candidate-b-20261011-135200-audit\receipt.json`.
The diagnostic is a path-rejected probe initialization, not a game crash.

Exact timeline, file hashes, limits and post-run JAR checks:
`docs\P0_CANDIDATE_B_RUNTIME_20261011.md`.
The original instance still has `39B364...346E`. This run does not test
the outside-model explanation UI or other subsequent source changes.

## Built-In Demo Resource Corrections

- `DebugScenes` and `TestScenes` now register the existing vanilla-only
  `ui_test` schematics. Old `debug` resources and general missing-block
  warnings remain intact for compatibility and useful diagnostics.
- The UI resource numbers were not a one-to-one match for the old scripts.
  Debug and ordinary UI entry points now use the actual geometry required
  by the off-screen, particle, controls and section examples.
- Thermal Expansion's helper uses the `thermalexpansion` namespace for
  scene identity. Its schematic is now explicitly `ponder_legacy:ui_test/runtime_test`,
  avoiding a lookup for a nonexistent Thermal-owned resource.
- The dynamic runtime test previously revealed an empty pillar and a
  mostly-empty platform. It now reveals two real four-block sections and
  restores all eight original iron blocks, including an intermediate
  redstone replacement. Captions and anchors match the selections.
- Eleven new JUnit tests read real compressed NBT palettes, resolve vanilla
  block states, compile actual registered stories, preserve explicit scene
  IDs, check authored coordinates, and replay replacement/hide/restore
  events through `PonderSceneRuntime`. All passed in the 223-test suite.
- No NBT resource, MMCE definition, recipe or general loader fallback was
  changed. A second read-only source/geometry review found no new blocker.
  These fixes address demonstrated demo content defects, not a proven
  cause of the user's historical world-entry failure.

## Independent Offline Evidence

- Model contract and native bake results:
  `docs\MMCE_STATIC_MODEL_INTEGRATION.md`,
  `verification\mmce-static-model\native-bake-results.txt`.
  Ten geometry reference cases and 25/25 real declared bindings passed.
  Combined vertex arrays: 7,862,016 bytes; largest: fwssmk2, 47,304 vertices.
  The electric arc furnace's signed cube dimensions are retained and tested.
- Optional-dependency/class-loading isolation: 10/10 checks passed:
  `validation\mmce-class-loading-20261011-023412-787\report.json`.
  Common classes initialize with MMCE, Gecko, client and LWJGL classes denied.
  Client cache invalidation reads no model resources and triggers no bake.
  This does not execute a physical dedicated server or Forge side gates.
- Artifact auditor regression coverage: 30/30 passed against the preceding
  `B0791F...944F0` build, not the later `668335...6E8F` artifact:
  `validation\artifact-audit-regression-20261010-183412-835.json`.
  Covers production, dev, sources, shim, renamed dev with copied metadata,
  missing resources, stable-scene bridge classes/translations and bundled
  dependencies.
- Synthetic sandbox replacement coverage includes different same-named old
  files, injected archive/commit failures, verified rollback, incomplete
  rollback with preserved backups, and retry rejection. All 11 cases passed
  with the preceding `B0791F...944F0` candidate in a serial run:
  `validation\mod-install-tests-20261010184603.json`.
  The preceding parallel rerun,
  `validation\mod-install-tests-20261010183412.json` was not all-green: one
  injection case was rejected earlier by the fail-closed Java-process guard
  while other Java tests ran. Its original hashes stayed unchanged and the
  injection did not execute. The serial run exercised all four injection
  scenarios successfully; the process guard was not weakened.
  These tests use disposable synthetic directories, not the actual
  `client-sandbox`. The original two injection tests initially did not trigger
  because of PowerShell script-scope resolution; closure-captured state now
  records each injected exception and verifies the post-rollback hashes.
- Sandbox preparation now saves its identity and log configuration before
  optional candidate installation. Previously `-PonderJar` called the installer
  before the required manifest existed. Installation failure still propagates
  without a final preparation-success summary and retains diagnostic files.
  Four synthetic integration tests passed, covering preparation with/without
  a real candidate, rejected candidate and pre-existing destination:
  `validation\prepare-client-sandbox-tests\20261010184901-45f97f5c\report.json`.
  These are minimal nonplayable fixtures, not prepared or launched game clients.

## Next Acceptance Steps

1. Preserve both candidate branches, installed chapter transactions and offline
   reports. Do not regenerate the existing fixture or reinstall the chapter
   in the current `client-sandbox` unnecessarily.
2. Preserve the completed `BCB1C2...A7D03` UI implementation, 441-test result
   and artifact audit without transferring the older candidate-b runtime
   conclusion to it. Candidate c has since completed a startup-only run;
   its screenshot capture and one recovery failed. Visual/input and
   world-entry checks remain open. Restore usable window capture before
   repeating an expensive game launch; do not issue blind game inputs.
   Branch b's chapter audit, bounded run and post-run probe preservation
   are complete within their recorded scopes. For a later authorized P0 run,
   retain the same Java, heap, resolution, offline identity and budget. Do not reuse
   previously played world copies or use dev, sources or server-shim artifacts
   as clients. The probe's existing fixed diagnostic output is preserved;
   recheck the source for updates before launching.
   The no-Ponder, candidate-a and candidate-b worlds have now been used;
   preserve all three and use further fresh restorations for repeat cold
   comparisons. The old-version branch also remains unlaunched. Identify the selected variant
   explicitly before each launch.
   The offline identity is not proof of original-player progress, and the
   path-disabled probe is not original-instance-equivalent runtime behavior.
3. First compare startup and world entry before opening previews. On a freeze,
   collect several thread dumps from that run's verified PID after world-load
   begins, together with logs and visible state. Earlier menu-time stacks
   cannot diagnose a later world-entry freeze. Reconcile actual machine IDs.
   The October 11 candidate has now supplied per-machine rows and passed
   registration reconciliation; its remaining scene and visual coverage
   must not be inferred from that result.
4. Candidate a retains its audited chapter; candidate b's chapter installation
   is committed and its independent installed-data audit has passed.
   The initial visual comparison is still incomplete.
   Complete the planned three cold
   starts and three world reentries using the final integration configuration,
   verifying visible frames, movement, inventory and input. Then inspect the
   representative machines, all runtime IDs, rails, transparency, fonts and
   camera controls. Do not declare the historical freeze fixed without evidence.
5. Verify repeated quest-to-preview-to-quest navigation in the real client,
   preserving the task page, scroll position, progress and rewards. The
   installed test data alone does not establish this behavior.
6. Complete physical optional-dependency, multiplayer, original-instance-
   equivalent probe behavior and game rollback gates before release.
   Synthetic file rollback does not establish saved-world compatibility.
   Original-instance deployment still requires backup and explicit verification
   of the actual user configuration; a sandbox-only pass does not prove it.

There have been two physical Escape stops. The first ended the no-Ponder
run; a later resumption authorized the `B0791F...944F0` candidate test.
The second was reported by Computer Use after that test. A new explicit
human continuation after the second stop now authorizes isolated
client/desktop testing; this is not authorization inferred from an automatic
continuation. Screenshot timeout and input-guard failures do not establish
game failure or visual acceptance. Do not bypass the guard; any new stop
must be respected.

Known implementation limits: no MMCE animation, bloom, OBJ, model-selection
events or partial-alpha textures; 28 machine definitions have no default Geo
binding found in the inspected scripts. Runtime registration is authoritative.
The unbound animated Dyson resource is not automatically attached.
Local native reloads in the audited version combination now request automatic
refresh; `/ponder reload` remains available for explicit refresh. A malformed
native sequence that never reaches its matching Post leaves uncached native
reads refused for the session rather than guessing that mutation has ended.
This does not block the client waiting for scripts; native reload recovery and
unsupported versions require separate runtime validation. Long captions, rail states,
transparent blocks, custom fonts and large-scene performance still need actual
client acceptance despite the offline fixes.

Historical world-entry evidence was also rechecked:
`docs\WORLD_ENTRY_EVIDENCE_REVIEW.md`. An October 4 Mixin conflict is confirmed
for that older failure, not for every later reported freeze. The October 10
isolated log does contain world-load and render-tick events; older claims that
no world-entry flow occurred have been corrected in
`validation\LAUNCHER_FINDINGS.md`. Those events still do not establish a usable
picture or successful interaction, and belong to the older sandbox JAR.

## Safety Boundaries

- No original machine JSON, recipe, quest progress, or world is modified.
- A server login log line is not proof of a usable client.
- Existing diagnostic switches in other mods are not changed speculatively.
- The goal is not complete. Isolated client/desktop testing has been explicitly
  resumed, while dedicated-server EULA acceptance remains pending.
  Missing visual/input evidence and remaining runtime gates are not satisfied
  by offline checks, authorization, installation or registration logs.
