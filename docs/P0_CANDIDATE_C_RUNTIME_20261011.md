# Candidate C Startup And Capture Outcome

Date: 2026-10-11, Asia/Hong_Kong.

## Scope

This run belongs only to the external-explanation UI artifact:

`BCB1C2C4CA3177F24083CF28883A094FB75EC69A9E8E4FB06E0B5E489F3A7D03`

The production build and installed candidate-c JAR matched before and after
the run. Its 55 suites / 441 passing tests remain offline evidence.
Original-instance Ponder still hashes to
`39B36481BB1C6AB4683045D73C50D1CB2F33370618A46310F56CC2ABF358346E`.

## Preparation

- Fresh sandbox: `validation\p0-candidate-20261011c`.
- Both frozen worlds restored: 164 files, 16,461,843 bytes.
- Restore receipt:
  `validation\world-restores\p0-candidate-20261011c-20261011-145925-786-48d3a8b8\receipt.json`.
- Only the candidate-c copy of the MMCE 2.1.5 patch with no installed target
  was quarantined; the four applicable patches and original instance stayed intact.
- Existing 53-machine / 62-target fixture installed without regeneration.
  Independent audit passed:
  `validation\audit-candidate-c-explanation-ui-20261011.json`.

## Run

Run directory:
`validation\p0-candidate-20261011c\validation-output\20261011-150548-682`.

- PID 31932; start 23:05:49, finish 23:10:58 local time.
- `result.json`: `operator-stop`, exit code -1.
- Initial capture: `FrameArrived timed out: timed out waiting on channel`.
- One recovery after reselecting and activating the current window:
  `window capture timed out: timed out waiting on channel`.
- No game clicks or keys were issued. There are no accepted screenshots.
- After checking executable, argument file and process creation time,
  the coordinator stopped only its own isolated process.
  This is neither a game-crash report nor a graceful in-game exit.

## Observed Evidence

- `stdout.log:6189`: 53 machines indexed, zero failures, 29 ms;
  no previews compiled.
- `stdout.log:6190`: registry generation 1, 196 scenes, six tags, 63 ms.
- `mmce-reconciliation-final.json`: complete published batch, 53/53 IDs,
  no evidence issues; all 62 structure variants lack compilation evidence.
- FTB client links initialized from candidate c's configuration. This is not
  a quest-click, return-navigation or progress-preservation test.
- No player-login, integrated-server or client-world-load record was found.
- No Ponder ERROR/FATAL or exception stack was found. One Ponder client tick
  took 145 ms; other mods do have errors, so this is not a zero-error log.
- The single 23:10:22 thread sample shows frame-rate synchronization
  (`Thread.sleep`, `Sync.sync`, `Display.sync`) in the client main loop.
  No server thread, BLOCKED thread or deadlock report was present.
  A pre-world single sample cannot diagnose the historical world-entry freeze.

## Preserved Outputs

Preflight:
`validation\probe-evidence-pre-candidate-c-20261011-230038-143\preflight.json`.

Post-run receipt:
`validation\probe-evidence-post-candidate-c-20261011-230038-143\receipt.json`.

Two changed/new probe files were preserved, 1,194 bytes total; before/after
source inventories during preservation were equal. The probe still rejects
the isolated path with `Unexpected game instance`. That is not proof of a
Ponder crash or original-instance-equivalent behavior.

## Remaining Work

Restore usable window capture before another expensive client run. Do not
use blind input or repeated startup registration as a substitute for visible
world entry, external-caption layout, paging, fonts, camera stability,
rails/transparency, MMCE appearances, or FTB entry/return tests.
Original mods, worlds and quest progress were not changed. The dedicated
server remains unlaunched with EULA unconfirmed. The overall goal is incomplete.
