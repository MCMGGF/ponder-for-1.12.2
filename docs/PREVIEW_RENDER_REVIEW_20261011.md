# Preview Rendering Review

Date: 2026-10-11 (Asia/Hong_Kong)

Scope: the current Ponder preview runtime, block submission pipeline, debug
caption layout and scene navigation. This review does not establish the
cause or resolution of the original world-entry freeze.

## Confirmed Defects

- Bounds sampled at only the start, midpoint and end missed geometry during
  complete or overlapping rotations. A complete turn can have identical
  sampled endpoints while extending far outside those endpoints in between.
- Actor MOVE events have a null location and a relative offset. The old
  bounds loop only considered events with a location, omitting movement.
- Directional show/hide transitions translate blocks even when there is no
  section move or rotation. Those blocks must contribute to the envelope.
- The translucent depth comparator used the opposite order to the GUI
  projection. Fading opaque blocks also wrote depth in the opaque pass.
- Multi-layer Forge blocks were treated as a single layer. A model using
  both an opaque shell and a translucent surface could lose one of them.
- Current GL color does not multiply the BLOCK buffer's per-vertex alpha.
  Partially visible geometry therefore did not use the intended opacity.
- Original-position neighbours culled shared faces after sections moved or
  rotated apart, leaving holes in the separated geometry.
- A failed model could leave the shared Tessellator buffer building. A
  native TileEntity renderer could also leak depth/blend/texture state.
  Cached GlStateManager setters alone do not repair direct native GL
  mutations when the Java cache still contains the saved value.
- The first scene's previous action and last scene's next action restarted
  the current scene and reset its camera. Debug captions did not provide
  the same bounded pagination as the regular viewer.

## Correction Boundaries

Continuous motion bounds must preserve actual transform order, easing,
instant steps and multi-axis rotations. Subdivision may tighten conservative
interval bounds but must not replace continuous coverage with frame sampling.
Actor movement instead uses its existing linear progress and spawn/reset
semantics. Static structures must not receive arbitrary global padding.

Block models use a private buffer, supported Forge layers, vertex-color
modulation and transform-aware neighbour access. Authored rail states
continue bypassing the dispatcher's actual-state recomputation. The old
black shadow plane remains disabled.

Native TileEntity calls need driver/cache state synchronization, bounded
matrix-stack cleanup and client vertex-array protection. This is not a
claim that arbitrary corruption of a caller's lower matrix stack, native
memory or a third-party renderer's global caches can be recovered.

Scene-boundary navigation is a no-op. Debug captions share the existing
paging/input ownership rules. The FTB stable-scene links and quest-return
contracts are retained.

## Historical Regression Evidence

The previous full build's four JARs and 39 suites / 262 JUnit results are
preserved with hashes in:

`validation/build-before-motion-review-20261011-053049-614/receipt.json`

The following failed runs deliberately exercised defects before their
respective production fixes. They are not successful build evidence:

- `validation/test-motion-regression-20261011-053151-275.log`
- `validation/test-navigation-regression-20261011-053848-412.log`
- `validation/test-render-regression-20261011-054322-683.log`

The debug-caption test was not run against old production; its first draft
required a helper not yet present there. Do not count a compilation failure
as reproduction of the caption layout defect.

## Verification Status

The final full command was:

`scripts/gradle-jdk25.cmd --no-daemon --offline test build forgeServerShimJar`

It succeeded with 46 suites and 347 JUnit cases, zero failures, errors or
skips. The 85 additions cover motion (19), actors (13), runtime (1),
navigation/captions (5), block pipeline (12), block buffer (10), render state
(10), model-view guard (10) and client-array guard (5). These are not added
again to the full total.

The 5000-block / 120-rotation case took 0.143 seconds for the complete JUnit
case on this machine. The real bundled section-scene case took 0.168 seconds
and checks its `50-degree X + 1800-degree Y` rotation and `(0, 4, 5)` movement.
These timings do not measure GPU frame rate or native renderer throughput.

The first full attempt ran 347 tests with one failure in the new real-scene
fixture: it used the nonexistent `ponder` resource namespace and loaded an
empty schematic. The fixture now uses `Ponder.asResource` / `Ponder.MOD_ID`.
Assertions were not weakened. Both attempts are retained:

- Failed: `validation/build-preview-review-20261011-062642-830.log`
- Failed XML: `validation/test-preview-review-failed-resource-20261011-0626.xml`
- Passed: `validation/build-preview-review-20261011-062854-298.log`
- Totals: `validation/preview-review-test-summary-20261011-063008-712.json`

The production-remapped client artifact is:

`build/libs/ponder_legacy-0.2.0-beta.jar`

SHA-256:
`F484A96EEC004F5ED0016943AF3298B274A27709DE62B11093B00964CDC394C1`

The artifact audit passed: 317 classes at major 52, remapped command methods,
required resources/bridges, and no test or dependency classes bundled.
Java 8 bytecode is not stock Forge-client acceptance; Cleanroom metadata and
the existing JAVA_16 mixin setting remain.

It is installed only in `validation/p0-candidate-20261011a/mods`. The
transaction `previous-ponder-20261011-063043-930-00403fc1` is committed and
preserves the unlaunched `CA640B...F9E59` predecessor. Post-install hashes
confirm one active candidate JAR, all 56 existing chapter outputs unchanged,
and the original instance still on `39B364...346E`.

- Artifact: `validation/artifact-audit-preview-review-20261011-063008-340.json`
- Installation: `validation/preview-review-postinstall-20261011-063133-237.json`
- FTB audit: `validation/audit-candidate-preview-review-20261011-063132-892.json`
- Preserved build:
  `validation/build-preview-review-verified-20261011-063350-227/receipt.json`

The independent installed-FTB-parser audit passed with zero errors, 185 SNBT
files, 56 output hashes and 107 new UIDs checked. Existing quest data and
installation transactions were not regenerated. All four current build JARs
and 46 test XML files have separate hash-verified copies in the preserved
build directory.

No game or desktop control is authorized by automatic goal continuation
after the later physical Escape stop. The server remains unlaunched with
`eula=false`. The original instance, quest progress and worlds are unchanged.

No new-build visible frames, actual font rendering, mouse interaction,
third-party rail/TESR output, cold starts, world reentries, multiplayer or
saved-world rollback have been accepted. Offline projection and fake-GL
tests cannot substitute for those checks.

The continuous tests compare CPU transforms and runtime actor positions.
GL rotation uses float angles and per-axis small-angle thresholds, so this
is not a claim of bit-identical native projection. Sparse grouped sections
can have conservative AABB margin. Real GL, font reflow, and user-perceived
camera framing remain part of the client acceptance gate.
