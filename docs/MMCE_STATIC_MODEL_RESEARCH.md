# MMCE Read-Only Controller Model Research

Date: 2026-10-11, Asia/Hong_Kong.

Status: implementation proposal, not an implemented renderer or visual acceptance.
Production files, the original Minecraft instance, and existing worlds were not
modified. No game or Gradle process was started for this research.

Implementation follow-up on 2026-10-11: the independent data adapter and offline
verification are now implemented. See `MMCE_STATIC_MODEL_INTEGRATION.md` and
`verification/mmce-static-model/`. Its 24 focused tests, 10 native reference
cases, and all 25 actual bound model bakes passed. This research document below
preserves its original proposal/evidence scope; GL/gameplay acceptance is still
not implied.

## Conclusion

A small static Geo adapter is feasible without constructing any controller Tile,
World, native renderer, or animation controller. Read the native default-model
registration once, copy resource identifiers, independently decode geometry, and
render a Ponder-owned immutable mesh.

This already covers the **25 explicitly bound default models** found in the
current pack's scripts. It must not claim that all 53 machines have custom
models. There are 28 machine IDs without a default Geo binding declaration in
the inspected scripts. A runtime registry lookup remains authoritative.

The first version should support bind-pose geometry, per-face UVs, cube/bone
transforms, and alpha cutout only. Do not implement MMCE runtime animations,
model-selection events, bloom, OBJ, or arbitrary renderer delegation in this
first version.

## Evidence Scope

All runtime data below comes from the prepared sandbox:

`C:\CODEX\Ponder-MMCE-Integration\validation\client-sandbox`

Installed binaries inspected with `javap -p -c`:

- `mods\ModularMachinery-CE-2.3.2.jar`
  SHA-256: `83676773E8C4F63963A48B4921F05898DCDBFDF777230E53E9830CD8F18177FB`
- `mods\geckolib-forge-1.12.2-3.0.31.jar`
  SHA-256: `B00BC9DE4E3E94CEA0455F8D58B295C1464272135E842BDF3170C2C955C871A5`

The GeckoLib JAR includes its Java sources; the loader, builder, matrix stack,
and renderer sources were read directly from those entries. MMCE source was
also inspected under:

`C:\Users\Seeds\.codex\skills\mmce-ai-machine-generation\references\source-code\core-and-integrations\MMCE\src\main\java`

Important MMCE methods were cross-checked against the installed binary rather
than assuming the bundled source revision is identical.

Existing real startup evidence, not a new launch:

- Sandbox `logs\latest.log:5638`: 25 animation files loaded.
- Sandbox `logs\latest.log:5639`: 25 model files loaded.
- Sandbox `logs\latest.log:6257`: 53 machines, zero index failures.
- These messages are from 2026-10-10, at 23:09:40 and 23:10:12 respectively.
- The log establishes successful model-file loading, not per-machine default
  binding, scene drawing, animation, or world-entry acceptance.

## Actual Resources

Resource inventory: 29 `.geo.json` files, 28 `.animation.json` files, and no
`.obj` or `.mtl` files under the sandbox's external `resources` directory.
This does not assert that unrelated mod JARs contain no OBJ assets.

All 25 explicitly bound models:

- Use Bedrock geometry `format_version: "1.12.0"`.
- Have exactly one geometry entry and cube-based geometry; no `poly_mesh`.
- Have all three declared geometry/PNG/animation files present.
- Have an animation file containing only `animation.fwss.new` with `loop: true`,
  with no bone tracks, sound, particles, or timeline actions.
- Use PNG textures between 128x128 and 512x512. Decoded alpha values are only
  0 or 255, with no partially transparent pixels.
- Have no `emissive`, `bloom`, or `transparent` bone-name prefixes.
- Use per-face cube UVs. Current cubes declare all six faces, but the renderer
  should still skip null quads to handle valid omitted-face resources safely.

The largest bound geometry is `fwssmk2`: 1,971 cubes, 988,603 JSON bytes.
The largest bone count among bound models is 12 (`rubbermachine`).
Several resources contain rotated cubes and zero-thickness faces, so a
bounding-box-only renderer or one omitting cube pivot rotations is insufficient.

All rows below use namespace `modularmachinery` and this resource prefix:

`animations/阶段1/`

Append `.geo.json`, `.png`, or `.animation.json` to the resource stem.
Machine and model IDs are identical in these current declarations.

| Machine ID | Resource Stem | Bones | Cubes |
| --- | --- | ---: | ---: |
| `alloykiln` | `合金窑合/1` | 1 | 230 |
| `blastfurnace` | `高炉炉子/1` | 1 | 339 |
| `blockcutter` | `方块切割机/1` | 1 | 674 |
| `coarserefiningfurnace` | `粗炼炉/1` | 1 | 232 |
| `cokeoven` | `焦炉/1` | 1 | 236 |
| `compressor` | `压缩机/1` | 1 | 333 |
| `crusher` | `粉碎机/1` | 1 | 921 |
| `electricarcfurnace` | `电弧炉/1` | 1 | 544 |
| `extractor` | `提取机/1` | 1 | 335 |
| `flintcrusher` | `燧石粉碎机/1` | 1 | 322 |
| `fwssmk1` | `流水筛石机1/fwss` | 1 | 641 |
| `fwssmk2` | `流水筛石机2/fwss` | 1 | 1971 |
| `hemppeelingmachine` | `剥麻机/1` | 3 | 230 |
| `industrialworkbench` | `工业制作台/1` | 1 | 69 |
| `loom` | `织布机/1` | 7 | 86 |
| `mechanicalpartsassemblytable` | `机械零件装配台/1` | 9 | 374 |
| `metalstampingmachine` | `金属冲压机/1` | 2 | 416 |
| `orewasher` | `洗矿机/1` | 2 | 932 |
| `quarry` | `采矿场/1` | 7 | 624 |
| `refiningfurnace` | `精炼炉/1` | 1 | 273 |
| `resindryingfurnace` | `树脂干燥炉/1` | 9 | 168 |
| `rubbermachine` | `橡胶机/1` | 12 | 91 |
| `sourceofmeltingfire` | `熔火之源/1` | 5 | 32 |
| `steammetalstampingmachine` | `蒸汽金属冲压机/1` | 9 | 86 |
| `thesourceofclearsprings` | `清泉之源/1` | 8 | 78 |

Representative declaration references under sandbox `scripts`:
`工业制作台.zs:21`, `水力洗矿坊.zs:13`, `水力洗矿坊.zs:19`,
`蒸汽金属冲压机.zs:43`, and their adjacent `setMachineGeoModel` calls.
Constants used for five model names were resolved from their explicit local
string declarations; no model IDs were guessed from filenames.

Machine IDs without a default-model declaration found in those scripts:

```text
1
2
dyson_nexus_xii
extendable_calculator_subsystem_l4
extendable_calculator_subsystem_l6
extendable_calculator_subsystem_l9
extendable_digital_storage_subsystem_l4
extendable_digital_storage_subsystem_l6
extendable_digital_storage_subsystem_l9
extendable_fabricator_subsystem_l4
extendable_fabricator_subsystem_l6
extendable_fabricator_subsystem_l9
geothermalgenerator
iron_centrifuge
mmcen_demo_compute_pylon
mmcen_demo_compute_tower
mmcen_demo_consumer_press
mmcen_demo_data_source
mmcen_demo_fabricator
mmcen_demo_research_table
mmt_player_research_terminal
smelting_type_prototype
steamboiler
steamcompressor
steamcrusher
thermalgenerator
triple_function_hatch_validator
windgenerator
```

Four geometry files are not referenced by the inspected Geo registration calls:

- `阶段1/蒸汽金属冲压机/1_01.geo.json`
- `阶段1/xxxxxxxx/1.geo.json`
- `杂/zzz.geo.json`
- `model_preview/dyson_nexus_xii/dyson_nexus_xii.geo.json`

The Dyson asset has 137 bones, 2,255 cubes, bloom-prefixed bones, and three real
animation tracks (`startup`, `running`, `shutdown`). A corresponding machine JSON
exists, but no default Geo binding declaration was found. Do not associate it
automatically just because its filename resembles the machine ID. Binding this
asset and supporting its visual effects are separate later tasks.

## Safe Metadata Interface

Verified native interface:

```java
DynamicMachineModelRegistry.INSTANCE
    .getMachineDefaultModel(DynamicMachine machine);

MachineControllerModel.getModelName();
MachineControllerModel.getModelLocation();
MachineControllerModel.getTextureLocation();
MachineControllerModel.getAnimationFileLocation();
```

`getMachineDefaultModel` performs only a `Map.get`. The four zero-argument
model getters return fields. No Tile, world, animation, model pool, event, or
render queue is involved in those exact methods.

Implementation constraints:

1. Invoke this bridge only on the physical client, on the client thread, after
   checking both `modularmachinery` and `geckolib3`.
2. Use the already indexed native `RuntimeMachine.source` as the map key. Do not
   synthesize a DynamicMachine or infer a model name from registry suffixes.
3. Resolve lazily when opening a model preview. Keep common-side descriptors free
   of native MMCE/Gecko client types so dedicated-server loading stays safe.
4. Initially accept only the audited concrete `MachineControllerModel` class.
   Unreviewed subclasses may override getters and are not automatically safe.
5. Immediately copy the model name and three ResourceLocations into a Ponder
   value object. Retain no native provider, animation processor, or pooled model.
6. Null binding, missing API, or missing resource means ordinary block-structure
   fallback with a specific note, not a guessed model or a native Tile fallback.

Suggested Ponder-owned interfaces, not existing APIs:

```java
ModelSpec lookupDefaultModel(ResourceLocation machineId);
StaticModelMesh bakeBindPose(ModelSpec spec, IResourceManager resources);
void drawStaticModel(StaticModelMesh mesh, PreviewTransform transform);
void clearModelPreviewCache();
```

`ModelSpec` contains identifiers/status only. `StaticModelMesh` contains owned
vertex arrays, local visual bounds, texture identifier, and resource generation.
Neither contains World, TileEntity, EntityPlayer, MachineControllerModel,
AnimationController, or a render task.

## Independent Geometry Path

The installed GeckoLib loader proves this data-only decode sequence exists:

```java
RawGeoModel raw = Converter.fromJsonString(json);
// Require FormatVersion.VERSION_1_12_0.
RawGeometryTree tree = RawGeometryTree.parseHierarchy(raw, location);
GeoModel model = new GeoBuilder().constructGeoModel(tree);
```

Read `json` through Minecraft's `IResourceManager` with a strict byte cap and
close the resource/stream. This preserves the active resource-pack and
ResourceLoader resolution, including the current Chinese paths.

Use GeckoLib's proven POJO/tree/cube builder rather than recreating UV, mirror,
inflate, and cube-rotation rules. An explicit new stock `GeoBuilder` also avoids
delegating into a third-party registered namespace builder. If a future model
requires a custom namespace builder, mark it unsupported until separately audited.

`new GeoModelLoader().loadModel(resourceManager, location)` is a verified
convenience API, but it selects `GeoBuilder.getGeoBuilder(namespace)` and reads
through the library's unbounded resource helper. The explicit capped sequence
above is the preferable isolated implementation.

Flatten the resulting fresh model into immutable Ponder vertex arrays once:

- Allocate a private `software.bernie.geckolib3.util.MatrixStack`.
- Apply bone translation, pivot, Z/Y/X rotations, scale, and inverse pivot.
- Apply each cube's pivot/rotation/inverse pivot.
- Copy transformed quad vertices, UVs, and normals; skip null quads.
- Preserve the native flat-cube normal correction for zero-thickness faces.
- Check every numeric value for finiteness and compute bounds from actual
  transformed vertices, not from the controller collision box.
- Do not call any animation method. Discard the mutable GeoModel after baking.

Gecko's cube builder already converts positions to block units and handles the
X-axis convention. Do not divide the baked vertices by 16 again.
The native MMCE render origin adds `(0.5, 0.01, 0.5)` relative to the controller.
The current Ponder snapshots use NORTH, requiring no facing rotation; any future
direction support must match native SOUTH=180, WEST=90, EAST=270 degrees.

Prefer Ponder-owned immediate buffer submission. No MMCE renderer, task, or
render-manager class is needed, and no model geometry should be rebuilt per frame.
The stock `IGeoRenderer` default implementation uses a shared static MatrixStack
and unconditionally touches GL state. Do not use it as the isolation boundary;
reuse the data/math helpers with an owned stack instead.

## Minimal Drawing Scope

For these current 25 models, opaque/cutout drawing is sufficient:

- Bind the resolved PNG, enable alpha cutout, retain depth testing/writes.
- Preserve zero-thickness surfaces; do not discard them as empty cubes.
- Fully restore GL state in `finally`, including depth mask, alpha test, blend,
  culling, current color, lighting/lightmap, matrix state, and texture binding
  according to the caller's rendering contract.
- Never reuse the black base-plane workaround or draw a solid quad behind the
  model. Transparency belongs to texture alpha and depth handling.
- Show the custom mesh as an assembled-model view. During layer assembly, keep
  the structure view; a single model cannot truthfully be split into block layers.
- Avoid drawing the complete custom shell on top of the complete structure,
  which produces occlusion and z-fighting. A deliberate structure/model mode is
  preferable to pretending their geometry is additive.
- Freeze fitting bounds for the opened scene using the union of structure and
  model visual bounds. Switching modes must not cause unsolicited camera zoom.

Proposed initial admission limits, to be tuned from real measurements:
2 MiB geometry text, 256 bones, depth 64, 4,096 cubes / 98,304 vertices, finite
coordinates, and a bounded mesh cache with a total byte budget. These limits
cover the inspected 25 resources; they are not measured performance guarantees.
Check limits before recursive expansion and during mesh construction.

On explicit Ponder reload, resource reload, world unload, and screen disposal,
release owned mesh state as appropriate. Resource reload must invalidate failed
lookups too. Do not make the initial optional renderer depend on a new MMCE
automatic hot-reload hook; the existing explicit reload limitation remains.

## Forbidden Shortcuts

- Creating any native machine/factory controller Tile, even in a detached World.
- Filling `machine`/`rotation` NBT to make a controller appear formed.
- Calling controller `getCurrentModel()` / `getCurrentModelName()`: these reach
  model-selection events.
- Calling `MachineControllerModel.getModel()`, `getRenderInstance()`,
  `initializePool()`, or `setLivingAnimations()`: these use shared mutable model
  pools/processors or animation callbacks.
- Calling `MachineControllerRenderer.renderDummy(...)`: verified bytecode
  enters `renderWithDefault`, which calls both `getModel()` and
  `setLivingAnimations(ctrl, ctrl.hashCode())`. It is not a read-only adapter.
- Calling `MachineControllerRenderer.getTask`, `GeoModelRenderTask`,
  `ControllerModelRenderManager`, or `TaskExecutor.FORK_JOIN_POOL`.
- Calling `GeoModelExternalLoader.onReload()` or clearing native queues/pools.
- Evaluating scripts or posting controller events to discover a visual model.

Useful bundled-source references, relative to the MMCE source root listed above:

- `github/kasuminova/mmce/client/model/DynamicMachineModelRegistry.java:29`
- `github/kasuminova/mmce/client/model/MachineControllerModel.java:63`
- `github/kasuminova/mmce/client/model/MachineControllerModel.java:91`
- `github/kasuminova/mmce/client/model/ModelPool.java:47`
- `github/kasuminova/mmce/client/renderer/MachineControllerRenderer.java:103`
- `github/kasuminova/mmce/client/renderer/MachineControllerRenderer.java:148`
- `github/kasuminova/mmce/client/renderer/MachineControllerRenderer.java:338`
- `hellfirepvp/modularmachinery/common/tiles/base/TileMultiblockMachineController.java:1588`
- `hellfirepvp/modularmachinery/common/tiles/base/TileMultiblockMachineController.java:1622`

## Validation Gates

Before enabling the model view:

1. Offline bake every one of the 25 bound resource sets through the actual Gecko
   builder. Verify nonempty finite meshes, cube/quad counts, resource closure,
   and visual bounds. JSON inspection alone is not this bake test.
2. Test omitted UV faces, mirror, inflate, rotated bones/cubes, zero-thickness
   cubes, malformed hierarchy/cycles, nonfinite values, oversized files, missing
   PNGs, and cache invalidation.
3. Add source/API isolation tests proving the model adapter never references
   forbidden native Tile/renderer/task entry points.
4. Verify absence of MMCE or GeckoLib and dedicated-server class loading still
   leave ordinary Ponder usable.
5. In a disposable game instance, inspect industrialworkbench (small),
   rubbermachine (12 bones), quarry (multiple bones/thin faces), orewasher
   (rotations), and fwssmk2 (largest current bound model).
6. Compare NORTH alignment to the controller anchor; check GUI scales, screen
   sizes, mode switching, pauses, scene changes, and camera stability.
7. Open/close repeatedly, reload resources, leave/re-enter the world, and compare
   native MMCE task/queue state and retained objects before/after. The preview
   must not add native controller tasks or retain the departed world.

Still unperformed here: native geometry baking, GL drawing, screenshots,
resource-pack override acceptance, memory/performance measurements, queue
instrumentation, and actual world-entry/exit testing.
