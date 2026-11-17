# Ponder for 1.12.2

Minecraft `1.12.2` backport of
[Ponder](https://github.com/Creators-of-Create/Ponder),
targeting **Cleanroom / CRL**.

This branch is not a straight upstream recompile. It is a practical backport
with rewritten UI, rendering, runtime, and compatibility layers for the 1.12.2
modding environment.

The `main` branch of `MCMGGF/ponder-for-1.12.2` carries the Cleanroom
backport and its `0.3.x-beta` release line. The independent Java 8 / Forge
`1.3.0-alpha.1` implementation previously on `main` is preserved on
[`codex/forge-1.3.x`](https://github.com/MCMGGF/ponder-for-1.12.2/tree/codex/forge-1.3.x).
Each implementation has its own API, JSON scene format, and multiplayer
support. See [the changelog](CHANGELOG.md) for the Chinese source-publication
notes and the recent display fixes.

## At a Glance

| Item | Value |
| --- | --- |
| Minecraft | `1.12.2` |
| Runtime target | `Cleanroom / CRL` |
| Current source version | `0.3.7-beta` Cleanroom source publication |
| Current state | build and tests are separate from artifact-specific runtime acceptance |
| Compatibility target | not stock Forge 1.12.2 |

## What This Repository Already Has

- component, tag, scene, and localization registration
- in-game Ponder browser and showcase UI for 1.12.2
- `/ponder`, `/ponderui`, and related client/debug entry points
- external JSON scene loading through `mods.ponder.SceneFiles`
- custom 1.12.2 JSON scene DSL with reusable interaction definitions
- live GUI snapshot support for selected screens
- JEI / HEI compatibility handling for the Ponder UI
- a growing runtime layer for world, overlay, particle, and GUI operations
- a local Ponderer-style editor, blueprint capture, structure/image assets, bounded package exchange and offline generation import
- preview-only player actions, three-dimensional block outlines and foreground target callouts
- miniature/full-scale projectors and explicitly isolated vanilla menu practice
- a six-chapter, pack-specific early petroleum line with real structure snapshots,
  separate conduits, batch storage, player actions and throughput troubleshooting

The current authoring entry is `/pondereditor` in a singleplayer world. See
[the authoring guide](docs/LOCAL_AUTHORING.md) for the actual controls, conversion
boundaries and workspace layout. Source version does not identify the JAR installed
in an existing instance; use that instance's installation receipt and actual JAR hash.
The `0.3.2-beta` reading-layer change is documented in
[the reading-layer notes](docs/READING_LAYER_032.md). Native acceptance and installation
are not implied by a source version or passing unit tests.
The new oil-line entry and its deliberately semi-automatic startup/storage procedure
are documented in [the oil-line guide](docs/EARLY_OIL_LINE_033.md).
The oil lessons now also expose an isolated operating exercise through the book
button, with resource ledgers, station controls, safe-start goals and fault
feedback. See [the operating-practice guide](docs/OIL_OPERATING_PRACTICE_034.md).
This is a local teaching model, not a live MMCE container or pipe-network emulator.

## Current Constraints

The declared Ponderer step capability table describes explicit 1.12.2 conversions,
not unrestricted parity with every modern or third-party callback. Unknown steps
remain drafts and cannot publish. Arbitrary entity/menu callbacks, multiplayer
content synchronization and online AI are outside this singleplayer version.
Particles use bounded deterministic representatives; complex translucent rendering
and arbitrary modern resources are not guaranteed to be geometrically identical.
Actual runtime, viewport, saved-world and installation acceptance must be recorded
against this candidate's own JAR hash rather than inherited from an Alpha run.

## Documentation

### Language Entry

- [English](docs/lang/en_us/README.md)
- [简体中文](docs/lang/zh_cn/README.md)

### JSON Tutorial

- [English JSON tutorial](docs/lang/en_us/json_tutorial.md)
- [中文 JSON 教程](docs/lang/zh_cn/json_tutorial.md)

### JSON Examples

- [English example index](docs/examples/json_tutorial/lang/en_us/README.md)
- [中文示例索引](docs/examples/json_tutorial/lang/zh_cn/README.md)

### Wiki

- [GitHub Wiki / Tutorials](https://github.com/wu-xiao-ya/ponder-for-1.12.2/wiki)

## Build

This workspace expects a modern JDK for Gradle.

Typical commands:

```bat
gradlew.bat compileJava
gradlew.bat test remapJar sourcesJar
gradlew.bat forgeServerShimJar
```

`forgeServerShimJar` builds a stock Forge 1.12.2 dedicated-server compatibility jar.
See [Forge server shim](docs/forge_server_shim.md).

Detailed setup notes:

Use JDK 25 for Gradle and the client build. The production client jar is
`build/libs/ponder_legacy-0.3.7-beta.jar`; the `-dev`, `-sources` and
`-forge-server-shim` jars serve different purposes. The shim retains its
Java 8 target and provides no client demonstrations.

CraftTweaker bridge sources compile when compatible `CraftTweaker2-*.jar`
files are present under `libs/api/` or in the existing local Gradle cache.
Those external dependencies are not committed. A build without them explicitly
excludes `compat/crafttweaker`; it does not validate the ZenScript bridge.

- [English development setup](docs/lang/en_us/development_setup.md)
- [开发环境说明](docs/lang/zh_cn/development_setup.md)

## Repository Layout

- `src/`
  - source code and bundled assets
- `docs/`
  - public documentation, wiki source, and JSON examples
- `gradle/`, `gradlew`, `gradlew.bat`
  - build environment
- `PORTING_NOTES.md`
  - migration notes and porting strategy

## Project Direction

The near-term goal is stability first:

- keep the 1.12.2 branch usable
- avoid shipping test-only internals as part of the long-term public surface
- continue rebuilding the missing runtime pieces behind the scenes

The longer-term goal is deeper runtime parity, with large rewrites accepted
where necessary.
