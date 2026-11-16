# Candidate B Runtime Evidence

Updated: 2026-10-11 (Asia/Hong_Kong), after the bounded runtime ended.

## Scope And Outcome

This report is exclusively about the candidate-b client JAR with SHA-256:

`668335186DFE2EB344372494BC825AEF09D4FD58B6C39B7F831859C1A8C36E8F`

Installed artifact:
`C:\CODEX\Ponder-MMCE-Integration\validation\p0-candidate-20261011b\mods\ponder_legacy-0.2.0-beta.jar`.

The logs establish an integrated-server login, a client world-load event,
a render tick outside a loading screen, Ponder browser/UI openings, four
compiled MMCE structural previews, and two compiled static appearances.
The three post-world-load thread samples contain no reported Java deadlock
or `BLOCKED` thread. They do not establish visible frames, usable input,
correct layout/rendering, or the absence of every possible native/render stall.

The run stopped at its configured 20-minute limit. It was not a normal user
exit, and the recorded exit code is not evidence of a game crash.
The historical original-instance world-entry problem is not declared fixed.

Browser layout and external-definition execution-budget changes were being
developed separately during this run. This report does not test any later
JAR produced from those changes. Earlier candidate-a or B0791F runtime
evidence is also not transferred to this build.

## Identity And Lifecycle

Run directory:
`C:\CODEX\Ponder-MMCE-Integration\validation\p0-candidate-20261011b\validation-output\20261011-133104-009`.

| Field | Recorded value |
| --- | --- |
| PID | 19748 |
| Player identity | PonderValidator |
| Start UTC | 2026-10-11T13:31:04.5813930Z |
| Finish UTC | 2026-10-11T13:51:06.5290324Z |
| Local interval | 2026-10-11 21:31:04 to 21:51:06, Asia/Hong_Kong |
| Recorded duration | 1201.9476394 seconds |
| Stop reason | `runtime-budget` |
| Exit code | `-1` |
| Argument encoding | `gb2312` |
| PID after completion | Absent when checked by the post-run audit |

`process.json` and `result.json` record the lifecycle above. The coordinator
reported that the launcher session terminated with shell exit code 0; that
shell status and the recorded game exit code are separate values.
Before collecting each thread sample, the coordinator checked PID, exact
Java executable, argument-file path, and creation time against this launch.
Each dump itself names PID 19748.

Only this isolated client was launched. The dedicated-server EULA remained
unaccepted; no dedicated-server result is claimed.

## Runtime Milestones

The following line numbers refer to this run's final `stdout.log`.

| Local time | Lines | Evidence |
| --- | --- | --- |
| 21:34:32 | 6102 | MMCE lightweight index: 53 machines, 0 failures, 51 ms; no previews compiled during indexing |
| 21:34:32 | 6103 | Ponder registry generation 1: 196 scene entries, 6 listed tags, 114 ms |
| 21:34:35 | 6124 | One Ponder client tick warning: 170 ms |
| 21:35:27 | 6336-6337 | Candidate-b FTB Quests data loaded: 4 chapters, 178 quests, 231 tasks, 68 rewards, 481 objects |
| 21:35:31 | 6379, 6406 | Player login and observed client world load |
| 21:35:33 | 6462 | Observed client render tick without a loading screen |
| 21:35:40 | 6492, 6494 | Browser command and `PonderIndexScreen` opening |
| 21:35:43 | 6501, 6503 | `PonderUI` opening and blockcutter minimum preview compilation |
| 21:36:13 | 6509 | Blockcutter static appearance compiled |
| 21:36:21-21:36:27 | 6512-6527 | Further index/UI transitions |
| 21:50:27 | 6530 | Windgenerator minimum preview compiled |
| 21:50:32 | 6539 | Steamboiler minimum preview compiled |
| 21:50:33 | 6548 | Orewasher minimum preview compiled, machine ID `modularmachinery:orewasher` |
| 21:50:40 | 6549 | Orewasher static appearance compiled |

The actor behind the GUI transitions is not identifiable from these log
messages. The coordinator did not issue clicks or key presses for them.
They are not recorded as automated input acceptance.

## MMCE Coverage

The coordinator generated `mmce-reconciliation-final.json` once after the
run. This documentation pass only read it and did not rerun reconciliation.
It records 53 configured unique machine IDs, all 53 indexed, no duplicates,
zero reported index failures, a complete diagnostic batch, and a published
Ponder registry.

The report contains 62 structural preview variants:
4 have compilation evidence and 58 have no compilation evidence.
All rendering and world-interaction statuses remain `not_verified`.

| Machine | Variant | Positions | Materials | Compile time |
| --- | --- | --- | --- | --- |
| `modularmachinery:blockcutter` | MINIMUM | 32 | 5 | 9 ms |
| `modularmachinery:windgenerator` | MINIMUM | 3 | 3 | 0 ms, as logged |
| `modularmachinery:steamboiler` | MINIMUM | 27 | 5 | 1 ms |
| `modularmachinery:orewasher` | MINIMUM | 48 | 5 | 1 ms |

Static appearances are separate from those 62 structural variants:

| Machine | Bones | Cubes | Vertices | Compile time |
| --- | --- | --- | --- | --- |
| `modularmachinery:blockcutter` | 1 | 674 | 16,176 | 161 ms |
| `modularmachinery:orewasher` | 2 | 932 | 22,368 | 341 ms |

These durations measure the logged compilation work, not FPS, total scene
latency, visual quality, or successful completion of an entire demonstration.
FTB data loading is not proof that a quest button opened the right scene
and returned correctly.

## Thread Samples

All three files are in the run directory, produced by the coordinator with
`jcmd Thread.print -l`. The actual snapshot time in the dump header is shown;
the timestamp in its filename is the earlier command-dispatch timestamp.

| File | Snapshot time, local | Client sample |
| --- | --- | --- |
| `threads-worldloaded-20261011-133808-291.txt` | 21:38:11 | RUNNABLE, `GL11.nglLightfv`, browser item lighting |
| `threads-worldloaded-20261011-133953-460.txt` | 21:39:56 | RUNNABLE, `GL11C.glDrawArrays`, browser item drawing |
| `threads-worldloaded-20261011-134552-039.txt` | 21:45:54 | RUNNABLE, `GL11C.glTexParameteri`, browser item texture setup |

Each contains 96 Java thread states: 13 RUNNABLE, 31 TIMED_WAITING, and
52 WAITING; none is `BLOCKED`. There is no deadlock report in any of the
three dumps. In every sample the server thread is sleeping in
`MinecraftServer.run`, consistent with the preceding save-and-pause GUI
messages. The changing client stacks pass through
`PonderIndexScreen.drawComponentsPanel` and
`AbstractPonderBrowserScreen.renderCenteredItem`.

The client CPU counter increases from 320,546.88 ms to 417,812.50 ms to
751,984.38 ms. The differences amount to approximately 92.77% and 93.26%
of one core between samples. The instance settings read after sampling
were `maxFps:260`, `enableVsync:false`, `pauseOnLostFocus:false`, and
`guiScale:2`. High CPU in this configuration is a follow-up measurement
item, not by itself proof of a hang or a Ponder defect.

Java monitor-deadlock absence does not rule out native OpenGL problems,
livelock, expensive frames, or input/display faults. Changing runnable
stacks and rising CPU counters also do not prove successful presentation
of frames to the user.

## Errors And Visual Limits

No Ponder exception, reported game crash, OutOfMemoryError,
StackOverflowError, or OpenGL error was found in the captured final log.
This is a bounded log observation, not an assertion that the modpack is
error-free.

Other findings that should not be mislabeled as a Ponder loading crash:

- Line 6427: MMCE Complement's `GuiUtilRenderComponents.func_178908_a`
  overwrite was skipped because DragonCore had already overwritten it.
  This is relevant to future font/layout testing; causation of the reported
  UI issues has not been established.
- The ForgeStudio probe refused its survey in the isolated path with
  `java.io.IOException: Unexpected game instance`. Gameplay initialization
  continued. This path-dependent behavior prevents treating the isolated
  instance as fully equivalent to the original instance.
- Line 6470: Galacticraft reported a missing `overworldMap` folder.
  Later Ponder UI and preview activity occurred; that message alone does
  not establish world corruption or the cause of a freeze.
- Optional-module, texture/model, update-check, and other modpack warnings
  occurred before the demonstrated world-load milestones.

The coordinator's window-capture attempts failed with frame/capture
timeouts. A window selection/activation attempt also encountered the
user-input guard; further interface input was stopped. This is not a new
physical-Escape cancellation and is not a visual pass. The existing human
continuation authorization was not rewritten as a new resumption blocker.
No screenshot-based acceptance is available for this run.

Camera motion, centering, transparent ground layers, rails, Chinese text,
narrow layouts, popup paging, repeated FTB returns, actual input response,
and all-machine visual coverage therefore remain open. The planned repeat
cold-start/reentry matrix, optional-dependency cases, multiplayer, and
original-player progress checks also remain open.

## Probe Output Preservation

After `result.json` existed and PID 19748 was absent, the independent
post-run pass compared the fixed ForgeStudio probe output against the
preflight baseline of 601 files, 313 directories, and 290,047,335 bytes.

Source root:
`C:\CODEX\Modpack-Forge-Studio\test-results\twelve-device-rebaseline-20260930\runtime`.

New evidence directory:
`C:\CODEX\Ponder-MMCE-Integration\validation\probe-evidence-post-candidate-b-20261011-135200-audit`.

- The resulting source contains 602 files, 313 directories, 290,047,935 bytes.
- `startup-10cc86f4-ee62-4e50-ade5-f0ebeab4b111.json` is new.
- `startup-latest.json` changed in content and timestamps; its size remains
  600 bytes. `latest.json` and `latest-baseline.json` are unchanged.
- Both changed files were preserved, 1,200 bytes total. Both have SHA-256
  `DCC0BABE690264D311616E1AD7EBDD5E4F97DA1D5871E8B4C232A54A98E1D9D3`.
  Destination hashes, sizes, creation ticks, and last-write ticks match
  their respective sources and were reread after preservation.
- Full source inventories before and after copying are equal in paths,
  sizes, hashes and file timestamps. Directory paths are also unchanged.
- The new startup diagnostic names candidate b and reports
  `initialization-failed` / `Unexpected game instance`. It is a failed
  probe initialization, not a failed game launch.
- Prior baseline and incremental evidence were retained. The preflight's
  three referenced evidence inputs were hash/timestamp-checked unchanged.
  An older incremental receipt lacks original creation ticks for its
  session file; those historic ticks were not invented. The later receipt
  supplies known pre-run timestamps for `startup-latest.json`.

`receipt.json`, `source-before.json`, `source-after.json`, and the saved
`Preserve-PostRunProbeEvidence.ps1` record this bounded preservation.
Only this new evidence directory was written by that pass. It did not
write probe sources, either game instance, prior receipts, or production
code; it did not operate a process or repeat reconciliation.
This is a file-level before/after check, not an atomic snapshot or a lock
against unrelated future writers.

Post-run JAR checks still found candidate b at `668335...6E8F` and the
original instance at
`39B36481BB1C6AB4683045D73C50D1CB2F33370618A46310F56CC2ABF358346E`.
The original instance has not received this candidate artifact.

## Evidence Hashes

Final stdout is 965,937 bytes. Its SHA-256 matches the reconciliation input:
`829C64748E32318E5EF48E065A933E3F847948AED6F25AE9A630932F5D3387B9`.

| File | SHA-256 |
| --- | --- |
| `result.json` | `517ECA5C8A669CABCFEF415E48ED8C75E56100BA2FAE80131F4ADA7E2E53BD65` |
| `mmce-reconciliation-final.json` | `9F2C3F4E8A3DA2C8391AEA49A637693BFCAE2FA460080B954543E3F5B0B457D3` |
| First thread dump | `DF882BA1BB1D8818A43DA8C6C6CBE2BEC12F4680FD43A4A02F127EBC2F780061` |
| Second thread dump | `3DCF40B62C11F3CF03D1DEC9571B53A57767F8FDFC12E2A99C474CA79DC5FF94` |
| Third thread dump | `7CBA18C985CC3496EB67ADB1D9386258BEDABAD49A6D71C576CC3EAB4ABBE86E` |
| New probe-preservation receipt | `D65B08A00BEB4226CD237B7870D482FB4A59D21404B0156C6C249DABABB4BA86` |

The three current status/plan documents were intentionally not updated by
this runtime-evidence pass; they await the separate new-build checkpoint.
