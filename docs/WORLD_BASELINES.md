# Frozen World Baselines

Updated: 2026-10-11 (Asia/Hong_Kong).

These are file-level starting points for the P0 comparison of no Ponder, the
original Ponder, and the new candidate. They do not prove that a world loads,
that its first frame is usable, or that the historical freeze is fixed.
No game was launched during baseline capture or preparation. Later isolated
runs are described below. No original world was written or restored.

## Current Baseline

Directory:
`C:\CODEX\Ponder-MMCE-Integration\validation\world-baselines\worlds-20261010-182341-688-f9727f82`

The directory timestamp is UTC; this run occurred on October 11 locally.
`manifest.json` records the original instance and world names verbatim.

| Archive | Files | Source Bytes | ZIP Bytes |
| --- | ---: | ---: | ---: |
| `world-001.zip` | 75 | 8,336,929 | 1,307,509 |
| `world-002.zip` | 89 | 8,124,914 | 1,476,412 |
| Total | 164 | 16,461,843 | 2,783,921 |

SHA-256:

- Manifest: `B41BDEBB16D94EDC412BB779D9775F027378A4BB563762DFE148AA72CBE31604`
- First archive: `5A680DD14BA36BEE679950CE25E11DB207B3F214D05750C83696A77240503393`
- Second archive: `46138791E8578D64CD9377B3A1DF418C459DBE11C03CB0E5F4DC4E55DA421D0C`

The preceding baseline, `worlds-20261010-181831-045-1603dd30`, is retained.
An independent audit checked its complete ZIP contents and rechecked both
original worlds successfully. The current baseline was created after fixing
quoted Java argument-file detection and moving all ZIP verification before
the final source/process checks.

## Capture Contract

`validation\tools\New-WorldBaseline.ps1` requires an explicit `SourceInstance`.
It selects immediate `saves` subdirectories containing a regular `level.dat`,
or accepts explicit leaf world names. A `level.dat` identifies a candidate
world directory; this tool does not parse NBT or validate world playability.

- Source and destination must not overlap. Source ancestors, world contents,
  and output ancestors reject filesystem links.
- Java process inspection must succeed. A target process, an unresolved
  Minecraft command, an argument file, or an unavailable command line causes
  rejection. Unrelated FinalShell is not stopped or modified.
- The capture preserves the complete file and directory set, including empty
  directories and hidden files. Source file lengths, modification times and
  SHA-256 values are recorded; reads and ZIP processing are streamed.
- Limits are 50,000 combined entries, 8 GiB total data and 2 GiB per file.
- Output uses a fresh directory. ZIP files use exclusive `CreateNew` handles.
  Every ZIP entry and complete archive is verified before source files and
  process state are checked again. Archives receive a verified read-only
  attribute.
- Only `manifest.json` with `status=complete` is a successful receipt. It is
  published through an exclusive temporary file and a non-overwriting move.
  `transaction.json` remains `prepared` after success; failure changes it to
  `failed` when possible. Neither a transaction nor a partial ZIP is success.
- Failure preserves evidence and does not delete, rename or restore originals.

This is not a VSS/atomic filesystem snapshot or a service preventing concurrent
game launches. The before/after checks establish the recorded source state
during this stopped-instance capture. Read-only attributes are not tamper
protection; consumers must revalidate hashes before using a baseline.

## Verification

`validation\tools\Test-WorldBaseline.ps1` passed **27/27** synthetic cases:
multiple worlds, literal/Unicode paths, hidden/empty files, a file larger than
16 MiB, reused output, missing world data, invalid/repeated selections, links,
unknown/running/late processes, quoted argument files, source changes, ZIP
corruption/duplicate/missing/extra entries, locked files and receipt failure.
Fault-injection counts are asserted, including mutations during the final ZIP
hash. These fixtures are not playable worlds or a gameplay test.

Report:
`validation\world-baseline-tests\20261010-182236-695-33650dc5\report.json`

## Restore Contract

`validation\tools\Restore-WorldBaseline.ps1` restores into a newly prepared
`PonderValidator` sandbox under the validation directory. Its required inputs
are `SandboxName`, `BaselineDirectory` and `ExpectedManifestSha256`.

The order is deliberately strict:

1. Run `Prepare-ClientSandbox.ps1 -SkipWorlds` with a new sandbox name and
   without `-PonderJar`.
2. Restore the frozen worlds with the restore tool and the pinned manifest hash.
3. Configure the branch's Ponder variant. Use `Set-SandboxPonder.ps1` only for
   the audited new candidate; its artifact policy does not accept the old
   original package as a new release.

The restore tool requires no `saves` path, no previous log output, and an empty
`validation-output` directory. A valid prior mod-install receipt also prevents
restoration at this stage; it is not classified as evidence of a game launch.
Do not bypass this rule by deleting previous saves or receipts. Prepare a new
branch instead.

- Manifest identity, SHA-256, complete status, world names, counts and source
  association are checked before staging. Duplicate JSON keys are rejected.
- Input archives and every output file are verified against the inventory.
  Absolute paths, traversal, ADS, reserved names, case collisions, extra or
  missing ZIP entries, filesystem links and special ZIP entries are rejected.
  Extraction is streamed with per-file and total budgets.
- Staging, destination, baseline and source boundaries are checked. Existing
  output paths are never overwritten. Input read guards remain open through
  publication, followed by final output, identity and process checks.
- Only an exclusively published `receipt.json` with `status=complete` is a
  successful restore. Its transaction remains `prepared` on success.
  If failure occurs after the new `saves` directory is published, the directory
  and diagnostic records are retained, success is not reported, and a retry
  refuses to overwrite them.
- No original world, source baseline, sandbox identity or installed mod is
  modified by restoration. This is not a game launch, NBT validator, VSS
  snapshot, or persistent lock preventing a later external game launch.

The restore tool passed **66/66** synthetic cases, including both worlds,
literal/Unicode paths, empty entries, timestamps, reuse rejection, unsafe
paths and ZIPs, directory overlap, late process starts, mutations during
verification and final-receipt failure. Fault-injection counts are asserted.

Report:
`validation\world-restore-tests\20261010190057-6befa4\report.json`

## Restored Comparison Branches

The real baseline was restored into three initially unlaunched branches
under `C:\CODEX\Ponder-MMCE-Integration\validation`. Each contains both worlds,
164 files, 66 internal directories and 16,461,843 bytes. Independent inspection
matched the restored file hashes, lengths, modification times and directory
sets to the frozen inventory.

| Branch | Ponder Variant | Restore Receipt Directory |
| --- | --- | --- |
| `p0-none-20261011a` | None; original JAR archived outside mods | `world-restores\p0-none-20261011a-20261010-190908-616-ba7f4738` |
| `p0-old-20261011a` | Original `0.1.0-beta` | `world-restores\p0-old-20261011a-20261010-190908-519-e232a92f` |
| `p0-candidate-20261011a` | Candidate `0.2.0-beta`, SHA-256 `B0791F4ECCB1F05DE45596DFEB2CBA1844A0999F813B15177C4CAABD766944F0` | `world-restores\p0-candidate-20261011a-20261010-190908-596-84fea183` |

This table records preparation-time versions, not later installed updates.

Each directory contains a `complete` restore `receipt.json`; its transaction
remains `prepared` by contract. The frozen capture manifest remains unchanged:
its `restoredToTestBranch=false` describes capture time, while the later
receipts establish the restorations. Do not rewrite the capture manifest to
update historical state.

The sequence was Prepare without worlds, restore, then configure Ponder.
Configuration and candidate-installation provenance are recorded in
`validation\p0-comparison-20261011a\configuration.json`. All three branches
uniformly quarantine the same obsolete MMCE 2.1.5 patch with no target and
retain the four applicable patches. Original-instance patches are unchanged.
The complete copied-input comparison passed with no unexplained differences:
`validation\p0-comparison-20261011a\report-20261010-192627-298-14b5c259.json`.
It compared 28,736 selected source files and 30,147 normalized copied entries
per branch, including 299 non-Ponder files under mods and all 492 restored
world-file records across the three branches. It is a stopped-instance,
file-level comparison, not an atomic snapshot or runtime-equivalence proof.

The four recorded source-difference paths are the quarantined patch,
`config/modularmachinery_terminal/research-progress-reset.json`,
`config/multishot.cfg` and `options.txt`. The latter three match the exact
Prepare path/window transformations. The Ponder variants and generated
identity/installation files are separately accounted for.

The configuration receipt remains a historical preparation record with
`comparisonVerified=false`; the later audit report establishes the comparison
result. The initial audit source is retained as `Audit-Comparison.initial.ps1`.
Review subsequently tightened exact top-level Ponder JAR path checks, with
separate targeted regression and live-location verification completed:
`validation\p0-comparison-20261011a\ponder-path-supplement-20261010-193321-085-e6582099.json`.
All 10 in-memory cases passed using predicates extracted from the updated
audit's AST, including a nested JAR with a correspondingly falsified
configuration path. The historical records and current Ponder locations,
counts and hashes satisfy those predicates.

The supplement binds the initial full report, archived initial script, updated
audit and unchanged configuration by SHA-256. It does not repeat the complete
file audit or prove that all other inputs were unchanged between these two
checks. No audit or receipt is rewritten to imply a game-entry result.

At preparation and during the initial runs, these P0 branches retained only
the original quest data. The 53-machine test chapter was then installed in
the stopped candidate after its first run, as described below. Neither the
original instance nor the older sandbox received the new JAR.

On October 11 the user authorized testing and `p0-none-20261011a` was launched.
Its world-loading log and three verified thread dumps are retained under
`validation-output\20261011-025908-554`; visual/input acceptance did not
complete before another physical Escape stop. The owned process and launcher
are terminal. The old-version branch remains unlaunched; the candidate was
subsequently launched after the next explicit user resumption.
See `docs\P0_NONE_RUNTIME_20261011.md`.

The no-Ponder branch is now a used run, not a pristine restore. Its restoration
receipt and the earlier comparison report remain historical evidence; they
do not claim its current world files still match the capture. Preserve this
branch and use a fresh restored branch for a repeated cold comparison.

On October 11 at 11:30:11 Hong Kong time, `p0-candidate-20261011a` launched
with candidate SHA-256 `B0791F4ECCB1F05DE45596DFEB2CBA1844A0999F813B15177C4CAABD766944F0`.
Immediately before launch its restored worlds still matched all 164 baseline
file hashes, 66 directories and 16,461,843 bytes. Its current world files
have since been used by the game and are not a pristine baseline.
The run under `validation-output\20261011-033010-490` records client-world
load, a published index matching all 53 configured MMCE machines, and later
preview compilation. Visual/input acceptance remains incomplete.
The launcher ended this owned process at 11:50:13 with `runtime-budget`.
The final registration/compilation report and three thread samples are
documented in `docs\P0_CANDIDATE_RUNTIME_20261011.md`.

After that run, chapter transaction
`transactions\p0-candidate-20261011a-20261011-035123-674-fb340076` committed
the existing 53-machine fixture to the candidate. Independent audit
`audit-candidate-chapter-20261011-0351.json` passed with zero issues.
That data preparation is not part of the earlier tested configuration;
do not rerun the original prepared-file audit against this changed branch
and interpret its expected differences as unexplained corruption.

The post-run built-in demo corrections were then built and installed in the
stopped candidate at 12:10:25. The active JAR is now SHA-256
`21B9E032782BD19728BCE30086C01602C8BF63B79BE4580A1D5850E49C6B3C15`.
The tested package was archived by the committed replacement transaction
`validation-output\previous-ponder-20261011-041022-893-52009b98`.
The new hash has not been launched; neither the previous runtime nor the
prepared-file comparison establishes acceptance of this new configuration.

## Next Use

The prepared-file comparison and supplemental location checks are complete;
the gameplay gates are not.
Interactive testing has been explicitly resumed. Use unused or freshly
restored comparison branches with the same Java, heap, resolution, offline test identity and
runtime budget. Keep shared diagnostic configurations identical; do not alter
only the candidate branch to suppress fingerprint warnings.

The installed ForgeStudio recipe probe embeds the original instance path.
It disables its survey in all three sandboxes, but baseline mode still enables
load-stage and world-ready sampling in the original instance. File equality
does not eliminate this path-dependent behavior. The experiment compares
Ponder variants with the probe disabled; it cannot establish original-instance
stability or exclude probe-related cost. Existing fixed-path ForgeStudio
diagnostic output has been preserved under
`validation\probe-evidence-20261010-194104-127-50e61e0c\runtime`.
Its complete `receipt.json` has SHA-256
`3E5372569722670058A253C3B982211C853B304D07C9438C9828A41E69CCB0BF`:
599 files, 313 subdirectories and 290,046,132 bytes, with matching source
before/after and destination paths, sizes, file hashes and timestamps.
The source was not modified and no Java or game was launched. This is not
an atomic snapshot or a persistent lock; recheck for new or changed source
output before any later authorized runtime test.

The offline `PonderValidator` identity is not the original player's identity;
this experiment is not proof of original-player quest progress preservation.
Do not reuse worlds modified by another comparison run. The no-Ponder run
provides world-loading and thread evidence, not gameplay acceptance or a
candidate result. The separate candidate run provides its own registration,
compilation and thread evidence, not visual acceptance. The user's explicit
resumption superseded the earlier physical Escape stop; respect any new stop
and do not bypass screenshot or input failures.
