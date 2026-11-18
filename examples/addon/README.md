# Ponder Example Addon

This standalone Java project demonstrates a client-side Ponder addon for
Minecraft 1.12.2, Java 25 and the Cleanroom branch of Ponder Legacy 0.3.7-beta.
The Gradle development loader is pinned to Cleanroom `0.5.6-alpha`, with
`0.6.13-alpha` as the upper bound for the in-game checks below.

## Build

Set `JAVA_HOME` to a JDK 25 installation and run from the repository root.

```bash
./gradlew remapJar
./gradlew -p examples/addon assemble
```

On Windows, use `./gradlew.bat` in both commands. The first command produces the
MCP-mapped Ponder development JAR and the remapped Ponder client JAR. The addon
uses the development JAR as a compile-only dependency and ships its own classes
and resources. To build against an external checkout or a separately obtained
development JAR, supply the path explicitly.

```bash
./gradlew -p examples/addon assemble -PponderDevJar="/path/to/ponder_legacy-0.3.7-beta-dev.jar"
```

The installable addon is `examples/addon/build/libs/ponder_example-1.0.0.jar`.
For a separate checkout of this project, use Gradle 9.3.1 with JDK 25, pass
`-PponderDevJar`, and include the repository's license and notices.

## Install and verify

Place the remapped addon JAR and the matching remapped `ponder_legacy-0.3.7-beta.jar`
in the Cleanroom client's `mods` directory. Open Ponder from each of these item
stacks and use the scene selector if other scenes are present.

| Vanilla item | Scene | What to look for |
| --- | --- | --- |
| Piston | Moving Independent Sections | The initial controls and callouts, a block section moving and rotating out of the machine, happy particles, and a piston item preview. |
| Sticky Piston | Sticky Piston Demonstration | The mechanism appears and emits green success particles. |
| Paper | Local Pulse | A caption followed by a 40-tick green outline and green success particles at the piston. Replay the timeline to see the same sequence. |

The Ponder index lists the **Example Mechanisms** and **Local Pulse** tags. All
three storyboards load the included `assets/ponder_example/ponder/codec_demo.nbt`
structure under the addon's own namespace.

## Migration from the Forge example

The addon calls `PonderIndex.addPlugin` during its client-only Forge
pre-initialization. The Cleanroom Ponder index rebuilds the registered scenes,
tags and shared text on each reload. One plugin now registers the scenes that
previously used ServiceLoader and Forge IMC. The three historical storyboard IDs
remain `ponder_example:service_loader`, `ponder_example:imc_registration` and
`ponder_example:codec_sync`. Their names identify the existing storyboards for
any pack references. The piston item uses the current preview actor timeline.
The historical entity glow callback has no rendering effect on this timeline.

The former ZenScript smoke pack's `ponder_example:pulse` custom codec is now
the Paper storyboard's Java helper. It replays the outline, success particles
and delay through the local Ponder scene API. Remove the old
`scripts/ponder/scenes/ponder_example_codec.zs` from an instance migrating
from the Forge pack. Launching these scenes requires the two client JARs.
