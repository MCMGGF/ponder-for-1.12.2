# Candidate Runtime Observation

Reviewed: 2026-10-11, Asia/Hong_Kong.

## Scope And Result

After the user's explicit resumption, the candidate branch launched with
Ponder `0.2.0-beta`, SHA-256
`B0791F4ECCB1F05DE45596DFEB2CBA1844A0999F813B15177C4CAABD766944F0`.
This was not the no-Ponder branch or the original instance.

Run directory:
`C:\CODEX\Ponder-MMCE-Integration\validation\p0-candidate-20261011a\validation-output\20261011-033010-490`.

The launcher ended its owned Java process, PID 15368, at the 20-minute runtime
budget. `result.json` records `reason=runtime-budget`, exit code -1. This is
not an observed game crash, a graceful exit, or successful acceptance.
The process and launcher session are terminal.

All 53 configured MMCE IDs match the published runtime index. Eleven machines
produced 14 successfully compiled structure variants, and three static
appearances compiled. The other 48 structure targets have no compile evidence.
No visible-frame or input acceptance has been obtained, and the historical
world-entry failure is not established as fixed.

## Timeline

Times are Hong Kong local time on October 11. Lines refer to this run's
`stdout.log`, not the original instance's `latest.log`.

| Time | Evidence | Observation |
| --- | --- | --- |
| 11:30:11 | `process.json` | Candidate launched with offline identity `PonderValidator` |
| 11:33:44 | lines 6024-6025 | 53 machines, zero index failures, 35 ms; generation 1 published with 196 scene entries / 6 tags in 89 ms |
| 11:33:47 | line 6047 | One 191 ms Ponder client tick before world loading |
| 11:34:25 | line 6086 | Integrated server starts |
| 11:34:37 | line 6325 | Client-world load observed |
| 11:34:38 | line 6383 | Render tick without a loading screen observed |
| 11:35:02-11:37:53 | lines 6438-6549 | Initial 10 machines / 12 structure variants and three appearances compiled |
| 11:49:47 | lines 6574-6575 | Fabricator subsystem L4 minimum/maximum compiled; final total 11 machines / 14 variants |
| 11:50:06 | lines 6593-6617 | 23 unavailable `create:*` block warnings from old debug schematics |
| 11:50:13 | `result.json` | Owned process stopped by the configured runtime budget |

Logged UI opening/closing events do not identify their operator. The automated
inputs attempted in this run did not succeed, so those events are not credited
to verified automation.

The missing-block warnings identify a separate built-in demo resource problem,
not an MMCE preview-compilation failure or proof of a world freeze. Unsupported
palette entries are read as air by `PonderSchematic`; the active UI test set
already has vanilla-only replacement resources. Follow-up source fixes and
builds must not inherit the runtime evidence for the hash above.

## Threads And Visual Limits

Three `jcmd Thread.print -l` dumps have matching `.identity.json` receipts
with verified PID, executable, argument file, process creation time, exit code
0, and dump SHA-256:

| Dump Filename | Actual Snapshot Time | Client Path |
| --- | --- | --- |
| `world-threads-20261011-033714-256-1.txt` | 11:37:16 | OpenGL world rendering |
| `world-threads-20261011-033804-676-2.txt` | 11:38:07 | RenderGlobal / EntityRenderer |
| `world-threads-20261011-033845-907-3.txt` | 11:38:49 | Lumenized / GregTech bloom FBO rendering |

Actual intervals are about 51 and 42 seconds, not 10 seconds. All three show
server-loop sleep, increasing CPU counters, and no reported Java deadlock,
`BLOCKED` thread or `waiting to lock`. These samples do not exclude
intermittent stalls or native graphics issues.

Computer Use capture failed with `FrameArrived timed out` and then
`window capture timed out`. Text-only state worked, but both F2 attempts were
rejected by the user-input guard. No candidate screenshot was obtained.
No new physical Escape stop occurred in this resumed run. The earlier stop
does not invalidate the user's subsequent resumption; input guards must still
be respected.

## Immutable Evidence

| File | SHA-256 |
| --- | --- |
| `stdout.log` (975,357 bytes) | `8B058C0467B2AE776CA8537DABB89E7ACEF3158DE7D167FE7C311A18B3C4E61D` |
| `result.json` | `FFB4B4C0E856E088C3A25D42089DF0F6F6C5A352D35F3E8973A06E26174C4642` |
| Early `mmce-reconciliation.json` | `F3924CE626FDD3E283ABC2E311C9EAAFB62B87329D78ED837FBBD432F1279AC3` |
| `mmce-reconciliation-final.json` | `C53B3AC279217F228B2E860935B72B14C825AFD7ED897DAAC274D2B265A9D4A9` |

The early report covers 907,638 log bytes before any previews opened and is
preserved unchanged. The final report matches the complete stdout hash; all
render/world-interaction statuses remain `not_verified`.

The two new/changed fixed-path ForgeStudio startup diagnostics were preserved
in `validation\probe-evidence-post-candidate-20261011-035401-242-5c6f1db9`.
Each is 600 bytes with SHA-256
`3235FD30F8178E3ACD1A1F40BEEF1391B0D1464B27592E253457012AA9A2ADC5`.
The completed receipt hash is
`34BC1B1BF705AA9D53D012F5892C95F48B221ED1BCC6B55EC5EEDF92C19EDCF7`.
This probe still rejects sandbox paths; original-instance-equivalent probe
behavior is untested.

## Post-Run Quest Preparation

Only after the candidate stopped, the existing audited fixture was merged
into that branch. Transaction:
`validation\transactions\p0-candidate-20261011a-20261011-035123-674-fb340076`.
Status is `committed`: one chapter `1158d37c`, 53 machine quests, 62 targets,
56 installed files. The fixture and original instance were not rewritten.

Independent installed-file audit:
`validation\audit-candidate-chapter-20261011-0351.json`, `passed=true`,
zero errors. All 185 SNBT files parse; 107 new UIDs avoid the 374 reserved
IDs; protected root, prior non-index files, chapter order and original-instance
quest files remain intact. This is not quest-button or reward-state acceptance.
The runtime above predates this chapter installation.

The candidate world copies are now used evidence, not pristine baseline
restores. Repeated cold comparisons require fresh restored copies; do not
overwrite these logs or reinterpret later changes as the tested configuration.

## Post-Run Build

The resource warnings led to four scoped content-class corrections: vanilla
schematic registration, matching script geometry, the Thermal schematic
namespace, and complete selections in the dynamic runtime example.
`BuiltInSchematicResourcesTest` adds 11 passing tests, including actual state
replay for replace/hide/restore. The unified build passes 223 tests in 37
suites with zero failures, errors or skips.

Build log: `validation\build-vanilla-scenes-20261011-040829-912.log`.
Artifact audit: `validation\artifact-audit-vanilla-scenes-20261011-0409.json`.
New client JAR SHA-256:
`21B9E032782BD19728BCE30086C01602C8BF63B79BE4580A1D5850E49C6B3C15`.

It was installed only into the stopped candidate, using the committed
transaction `validation-output\previous-ponder-20261011-041022-893-52009b98`.
That transaction archives the tested `B0791F...944F0` JAR. All 56 quest
installation outputs were rechecked unchanged after replacement.
The preceding four build artifacts and test results also remain under
`validation\build-before-vanilla-scenes-20261011-035746-882`.

The new JAR has not been launched. The 11:30-11:50 runtime does not validate
the corrected build or the newly installed quest chapter. The original
instance still has its unchanged `0.1.0-beta` JAR.
