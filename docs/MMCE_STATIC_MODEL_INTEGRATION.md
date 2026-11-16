# Static Model Integration Contract

Implemented and independently tested on 2026-10-11. No native renderer or
controller Tile is used. Actual GL/game acceptance remains a separate gate.

The UI integration entry point is:

```java
MmceStaticModelPreviews previews = MmceStaticModelPreviews.getInstance();
MmceStaticModelPreviews.Preview preview =
    previews.getPreview(machineId, minecraft.getResourceManager());
```

Call only after the user explicitly selects Appearance, not on screen opening
or every frame. `Preview.hasMesh()` determines
whether model mode is available; `getNote()` is the Chinese capability/failure
text. `getMesh()` returns owned immutable quad vertices. `getTexture()` returns
the explicit registered texture ResourceLocation, never a guessed filename.

`MmceStaticModelMesh.forEachVertex(VertexConsumer)` sends
`x, y, z, u, v, normalX, normalY, normalZ` in GL_QUADS order. Vertex positions
already include MMCE's `(0.5, 0.01, 0.5)` controller-local anchor and NORTH
orientation. The UI adds only `snapshot.getOrigin()`. No additional `/16`,
centering translation, or controller-facing rotation is needed.

`mesh.getBounds()` has public final `minX/minY/minZ/maxX/maxY/maxZ` coordinates
computed from the transformed vertices. Add the same snapshot origin before
combining with structure bounds. The first explicit appearance request fits this
union; ordinary mode switches retain it without moving or zooming the camera.
After resource invalidation, another explicit request is required. Expanded
bounds then preserve the user's transform until Reset Camera is used.

Use `DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL`, alpha cutout and depth
writes; do not put a black rectangle behind the model. Disable culling for
native-equivalent thin/two-sided faces. Restore GL state in the caller's finally.
Display the static shell instead of the full block structure in assembled model
mode, not overlaid on top of it. Layer assembly stays a structure-only view.

The service lazily attaches an IResourceManagerReloadListener and invalidates
both successful and failed results on resource reload. Check
`previews.isCurrent(preview)` before drawing a held result; a stale screen may
refresh its result once or fall back to structure. `previews.clear()` is the
explicit Ponder reload/world-unload hook. Screen disposal releases its Preview
reference; the service owns only a bounded mesh cache, not a World or Tile.

The current screen chooses structure fallback after invalidation. It does not
silently bake again. Both showcase and Debug views have Structure / Appearance
controls; starting playback or seeking returns to the layer-based structure.
`PreviewStaticModelRenderer` uses a private submission buffer, paired client
attribute preservation, non-pushing scissor setup and LWJGL 2/3 query adapters.
The ordinary structure fit excludes controller selection boxes, which are not
visual mesh bounds. Real-client GL and input acceptance remains outstanding.

`Preview.getResourceGeneration()` identifies its resource epoch.
`isCurrent(preview)` compares both that epoch and the current
`MmceMachineIndex.getGeneration()`. The first manager attachment and listener
registration can advance the epoch more than once; only equality matters.
Explicit index refresh cannot leave an old preview current even before the
next model request. Resource reload clears failures as well as successes.

The index descriptor now says only static structures and registered default
appearances are previewed; the actual service result supplies availability or
failure detail. Unsupported effects/geometry/missing bindings remain explicit
structure fallbacks. Dynamic structure maximum variants must not be claimed
to dynamically resize the single registered static model.

## Limits And Coverage

- Metadata comes only from the indexed native machine key and the audited exact
  MachineControllerModel getters. Optional classes are loaded reflectively.
- 2 MiB geometry, 256 bones, depth 64, 4096 cubes and 98304 vertices per model.
- PNG texture cap 4 MiB and 1024 pixels per axis; only alpha 0/255 is admitted.
- Owned mesh cache: 12 entries, at most 16 MiB of vertex arrays. Failed results
  share the entry cap. There are no per-frame native model calls.
- Supports per-face/box UVs, omitted faces, cube and parent/child bone rotations,
  pivots, inflation, mirror and zero-thickness faces using the installed Gecko
  data builder and a new private matrix stack.
- Signed cube dimensions are preserved. The actual electricarcfurnace asset
  contains one `size: [-2, 1, 1]` cube, which the stock builder supports.
- Unsupported model versions, multiple geometries, meshes, bloom/emissive/
  transparent bones, partial alpha, malformed hierarchy and invalid coordinates
  fail with a Chinese structure-fallback note.

## Independent Verification

Run from PowerShell; neither command invokes Gradle or starts Minecraft:

```powershell
& 'C:\CODEX\Ponder-MMCE-Integration\verification\mmce-static-model\RunModelUnitTests.ps1'
& 'C:\CODEX\Ponder-MMCE-Integration\verification\mmce-static-model\RunNativeGeoBake.ps1'
```

Both completed successfully:

- `unit-test-results.txt`: 24/24 tests, including schema limits, bad hierarchy,
  signed extents, resource bounds, alpha checks, ownership, cache eviction,
  success/failure reload invalidation, and source/API isolation.
- `native-bake-results.txt`: 10 native geometry reference cases plus 25/25
  explicitly bound model/resource sets, using the installed GeckoLib 3.0.31 JAR
  and stock Forge 1.12.2 SRG JAR. These are actual library bakes, not JSON-only
  inspection or substitute geometry stubs.
- All 25 meshes have finite nonempty bounds, unit normals, expected bone/cube/
  vertex counts, and valid cutout textures. Combined vertex arrays are 7,862,016
  bytes. Largest is fwssmk2: 1,971 cubes, 47,304 vertices, 1,513,728 bytes.
- Synthetic checks cover controller anchor/units, flat faces, omitted faces,
  mirror UVs, bone rotation, out-of-order parent/child rotation, cube pivot,
  bone pivot, inflate, and signed dimensions.

The bake timings in the output are single-run offline observations after
synthetic warm-up, not a GUI latency or gameplay performance guarantee.
Original pack files were read only through the copied C:\CODEX sandbox; no
original instance files or worlds were modified.
