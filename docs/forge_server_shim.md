# Forge Server Shim

The Forge server shim is a lightweight `ponder_legacy` mod jar intended for
stock Forge 1.12.2 dedicated servers.

Use it when the server runs Forge and clients run the full Cleanroom / CRL Ponder backport.

## Build

```bat
scripts\gradle-jdk25.cmd --no-daemon --offline forgeServerShimJar
```

The output jar is written to:

```text
build/libs/ponder_legacy-<version>-forge-server-shim.jar
```

## Install

- Put `ponder_legacy-<version>-forge-server-shim.jar` in the Forge dedicated server `mods` folder.
- Put the full Ponder jar on clients.
- Keep the shim and full jar on the same Ponder version.
- Put one Ponder jar in each `mods` folder.

## Scope

The shim provides:

- the same `modid` (`ponder_legacy`) for Forge mod-list matching
- the public Ponder API classes used by common/server-side integration code
- no-op scene, tag, language, and registry helpers
- permissive network compatibility for full clients

The shim intentionally has no Ponder UI, rendering, scene playback, client tooltip integration, JEI/HEI handling, or JSON scene runtime.

## Verification Status

As of October 11, 2026, the shim builds and passes the recorded artifact and
class-isolation checks. A physical dedicated-server startup, client handshake
and multiplayer quest navigation have not been verified.

The local preflight found Java 8, Forge 14.23.5.2860, the matching vanilla
1.12.2 server and all 21 library files referenced by the Forge manifest.
These files allow an offline isolated server to be prepared under `C:\CODEX`;
their presence is not a successful server run. No EULA acceptance was found
in the inspected test locations, and no agreement was accepted or server
started during preflight.

The prepared directory is
`C:\CODEX\Ponder-MMCE-Integration\validation\forge-shim-server-20261011-045224422Z`.
Its `inputs-manifest.json` pins 24 JARs, three configurations and the Java
executable. EULA remains false; user confirmation is pending. The selected
port is 25580 and must be checked again before launch.

Any subsequent test must use a new disposable directory, bind only to
`127.0.0.1` on a separate port, disable Query/RCON and retain logs. Do not copy
the full client instance, original worlds or the full client Ponder JAR into
this minimal shim test. A successful server-only run would still not prove
client rendering or network compatibility.

