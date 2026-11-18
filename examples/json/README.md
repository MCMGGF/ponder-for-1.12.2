# Ponder JSON example

Use the migrated Ponder Legacy build on Minecraft 1.12.2 with Cleanroom and
Java 25. The release target is Cleanroom through `0.6.13-alpha`. This example
runs in a local single-player instance using the authoring workspace.

1. Merge this example’s `config` directory into the game instance. The directory
   contains the published scene and its 5×3×5 schematic from Forge commit
   `c494bf7`. Keep the paths and existing user files when merging.
2. In a single-player world, run `/pondereditor enable`. If authoring was already
   enabled when copied, use `/ponder reload` or restart the client.
3. Run `/ponderui minecraft:crafting_table` and select **Reloadable JSON Scene**.
   The current scene ID is
   `ponder_authoring:ponder_json/demo_pack/crafting_demo`. To verify the scene in
   the current index, run `/ponder compile minecraft:crafting_table`.

The scene reveals the platform and upper layers, lifts a linked crafting
table, merges a `Lock` SNBT field into the schematic’s chest, and moves,
hides, shows and removes a paper preview actor. The scene also demonstrates
document-local shared text, a keyframe, a right-click hint and a success
effect. Its pauses let the moves and hints remain visible before cleanup.

The authoring index groups this item under the built-in local authoring tag.
The original pack’s custom `ponder_json:examples` tag and global
`minecraft:barrier` index exclusion have no equivalent authoring-document
fields. Shared text stores the final formatted caption since this format
does not substitute parameters per step. The paper follows a bounded,
deterministic trajectory in place of the old live item entity. The
schematic reader uses block and block-entity data and ignores saved entities.

To uninstall, remove `config/ponder_legacy/authoring/published/ponder_json/demo_pack.json`
and reload. The ZenScript migration uses the same
`assets/structures/ponder/demo/basics.nbt` asset. Keep it if either example or
another local authoring document references it.
