# First P0 Runtime Observation

Reviewed: 2026-10-11, Asia/Hong_Kong.

## Scope And Result

The user explicitly resumed isolated testing. One cold launch was performed
with **no Ponder installed**, using `p0-none-20261011a`. The original instance
was not the launch target. Neither the old-Ponder branch nor the current
candidate branch was launched.

World loading and later client execution are evidenced, but visible gameplay,
input responsiveness and Ponder integration have **not** passed acceptance.
The physical Escape stop ended Computer Use. Only the verified test Java
process was terminated; the launcher recorded `operator-stop`, not a crash
or a normal successful game exit. At the end of that run, renewed affirmative
permission was required. The user subsequently provided it, and the separate
candidate run is recorded in `docs\IMPLEMENTATION_STATUS.md`; this historical
no-Ponder report is not an active permission blocker.

Run directory:
`C:\CODEX\Ponder-MMCE-Integration\validation\p0-none-20261011a\validation-output\20261011-025908-554`.

## Timeline

Times below are Hong Kong local time on October 11, 2026.
Log line numbers refer to `stdout.log` in the run directory.

| Time | Evidence | Observation |
| --- | --- | --- |
| 10:59:09 | `process.json` | Java PID 40968 launched with the sandbox argument file and offline identity `PonderValidator` |
| 11:04:01 | line 6100 | Integrated server started |
| 11:04:06 | line 6158 | Start region preparation |
| 11:04:12 | line 6313 | Offline test player logged in |
| 11:04:17 | line 6418 | Client opened `GuiChatOF` |
| 11:05:28 | line 6435 | Unknown-command response; the submitted command is not recorded, so its text cannot be inferred |
| 11:05:46 | line 6455 | Client opened `GuiWrapper` |
| 11:06:01 | lines 6542-6548 | In-game menu event and server save/pause |
| 11:06:03 | line 6551 | Another `GuiChatOF` event |
| 11:06:57 | `operator-stop.json` | Only the verified isolated Java process was terminated after physical Escape |
| 11:06:58 | `result.json` | Launcher completed with `reason=operator-stop`, exit code -1 |

The Computer Use helper returned `FrameArrived timed out` / `window capture
timed out`. Text-only window inspection worked, but both F2 input attempts
were rejected because user input was detected. No successful automated
world-selection input or game screenshot was obtained. Do not attribute the
world selection or GUI events to a verified automated action.

## World-Loaded Thread Samples

Three `jcmd Thread.print -l` outputs were captured. Each accompanying
`.identity.json` records matching PID, Java path, argument file and process
creation time before and after collection, exit code 0, and matching dump
SHA-256. Snapshot times come from the dump headers, not the filenames or
collection-completion times.

| Dump Filename | Snapshot Time | Client Thread, Starting At Line 38 | Server Thread |
| --- | --- | --- | --- |
| `world-threads-20261011-030556-809-1.txt` | 11:05:59 | `RUNNABLE`: `blankmod.RenderMachinePreview` bounds calculation through the FTB quest-icon drawing path | line 1039: `TIMED_WAITING`, `MinecraftServer.run -> Thread.sleep` |
| `world-threads-20261011-030611-198-2.txt` | 11:06:14 | `RUNNABLE`: SmoothFont / SuperheavyFontRenderer / chat and HUD drawing | line 1035: same server-loop sleep location |
| `world-threads-20261011-030625-625-3.txt` | 11:06:28 | `RUNNABLE`: OpenGL buffer binding through OptiFine VBO world rendering | line 1020: same server-loop sleep location |

Client CPU counters increase from 289609.38 to 301171.88 to 312687.50 ms;
server counters increase from 19343.75 to 20546.88 to 21500.00 ms.
No Java deadlock report, `Thread.State: BLOCKED` or `waiting to lock` entry
appears in these snapshots. All three outputs reach their JNI footer.

The differing client stacks and increasing CPU counters do not support a
persistent single-location Java stall throughout this sampling interval.
They do not exclude intermittent stalls, native graphics problems, incorrect
pixels, input problems or the user's historical loading failure. One sample
inside model-bound calculation is not a performance profile.

## Other Findings And Boundaries

- At 11:05:54, MMCE's native JEI structure-preview path logs an
  `alfheim$lightingEngine` reflection warning from `DummyWorld` at log lines
  6461 and 6500. Later GUI events continue. This occurred without Ponder;
  it is not evidence that the new Ponder implementation caused the warning
  or the historical freeze.
- The native MMCE warning and the `blankmod` quest-icon renderer in sample 1
  are separate paths. Neither is proven to be a freeze cause.
- The ForgeStudio probe again refuses its sandbox path. Original-instance
  probe behavior therefore remains a separate acceptance gate.
- No generated Ponder machine chapter was installed in this branch.
  Original quest rendering is not acceptance of the new 53-machine chapter.
- The launched branch has now been written by the game and is evidence, not
  a pristine baseline. A repeated cold comparison must use a fresh restored
  branch, without overwriting this run or reusing its modified worlds.
- Latest candidate SHA-256 remains
  `B0791F4ECCB1F05DE45596DFEB2CBA1844A0999F813B15177C4CAABD766944F0`.
  Original-instance deployment and candidate gameplay are still incomplete.

## Evidence Integrity

Existing logs, dumps and receipts were read without rewriting them.

| File | SHA-256 |
| --- | --- |
| `stdout.log` | `87F6329CFBB83138EC678133F4CA2167C8D1950EB5D966973A3970C7FAA7CC66` |
| `result.json` | `406C3C6935A676BF9CF79EBBAC1677AE6764CCA00400207CB3BF43D1190D0D52` |
| `operator-stop.json` | `FF621E2E9E86E31D52F6F6FB039F0EFC3B6F3D818B7244259EB5ACD7A7CCC086` |

Next authorized gameplay should explicitly identify the candidate branch
containing `0.2.0-beta` before launch. The old-version comparison, candidate
visual/input checks, final quest configuration, repeated starts, multiplayer
and original-instance-equivalent checks remain required before release.
