# Ponder ZenScript migration example

Use the migrated Ponder Legacy build on Minecraft 1.12.2 with Cleanroom and
Java 25. The release target is Cleanroom through `0.6.13-alpha`. This example
has a local authoring walkthrough and a ZenScript loader for an external JSON
guide.

1. Merge `config` into the game instance. This installs the published
   walkthrough and the 5×3×5 schematic from Forge commit `c494bf7`. Keep
   the paths and existing user files when merging.
2. To try the ZenScript loader, install CraftTweaker for 1.12.2 and use a
   Ponder build that includes the CraftTweaker integration. Merge `scripts`
   into the game instance and restart the client so CraftTweaker evaluates
   `scripts/ponder_zen_diagnostics.zs`. It queues
   `scripts/ponder_migration/zen_diagnostics_entry.json` through
   `mods.ponder.SceneFiles.loadJson`. That directory is outside Ponder’s
   automatic JSON scan roots, so the guide appears only after the script queues it.
3. Enter a single-player world and run `/pondereditor enable`. Run
   `/ponder reload` if the workspace was already enabled or if you edited
   the queued guide JSON. Restart the game after edits to the `.zs` file.
4. Run `/ponderui minecraft:crafting_table` and select **Ponder ZenScript
   Diagnostics**. Its runtime ID is
   `ponder_authoring:ponder_zen/diagnostics/diagnostics_demo`. With the bridge
   installed, **ZenScript Loader Entry** also appears and its external
   `ponder_zen:diagnostics` index tag is registered. The authoring walkthrough
   is available without CraftTweaker.

The walkthrough shows the separate base and upper reveals, point of interest,
shared captions, section outline, chest bounding box, wide line, click and
scroll indicators, merged chest SNBT, a linked chest that lifts and rotates,
and a book actor whose visibility and lifetime can be replayed. The
walkthrough’s captions store the original formatted values in document-local
shared text and its actor follows deterministic motion. One extra pause makes
the book’s reappearance visible before removal. The current schematic reader
uses the structure’s blocks and block entities and ignores saved entities.

The historical `SceneRegistry`, `TagRegistry`, `SharedText`, `Selection`
and `Vector` ZenScript builders are absent in this branch. The current
`SceneFiles` bridge registers the guide and its custom tag through the
external JSON loader. The executable walkthrough uses authoring steps and
appears under the built-in local authoring tag.

## Diagnostic fixtures

The top-level `diagnostics` directory is optional. Copy one fixture’s JSON
text to the clipboard and use the editor toolbar’s JSON import. Confirm the
import as a draft if prompted, then use **!** to see validation details or
try publishing it. Each fixture should fail validation. Use a fresh draft for
each case. The codes below identify the current validation issues.

| Fixture | Expected issue |
| --- | --- |
| `90_duplicate_id.json` | `scene.id`. Two segments in the same author document use one ID. The historical case duplicated a registry scene ID in a second script. |
| `91_missing_structure.json` | `asset.structure`. The explicitly requested `ponder_zen:missing/structure` asset is absent. |
| `92_invalid_handle.json` | `step.invalid`. The historical bad string handle is invalid at the current numeric `actorId` entry. |
| `93_oversized_nbt.json` | `budget.nbt`. The Tile NBT patch exceeds the current 64 KiB UTF-8 SNBT budget with fewer than 65,536 characters. The historical threshold was 256 KiB. |

The live walkthrough can also be checked with `/ponder list`,
`/ponder compile minecraft:crafting_table` and
`/ponder dump minecraft:crafting_table <scene_index>`. The compilation
output gives the index used by `dump`. The older inspect, validate, export,
and synchronization commands belonged to the Forge script runtime.

To uninstall, remove the published `ponder_zen/diagnostics.json` document.
If you installed the loader, remove its two files under `scripts` and restart.
Both migrated examples use the same workspace structure
`assets/structures/ponder/demo/basics.nbt`. Keep it if another document
references it.
