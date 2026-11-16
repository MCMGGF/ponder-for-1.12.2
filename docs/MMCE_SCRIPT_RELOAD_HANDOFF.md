# MMCE Script Reload Completion Bridge

Historical handoff: the initial implementation and its audit are preserved
below. They are superseded by `MMCE_REGISTRY_READ_GUARD.md`. The current bridge
uses guarded native reads and stable epochs, does not read ScriptStatus, and
is now integrated into ClientProxy and every native index, snapshot and
default-model metadata read. The 39 focused tests are part of the complete
188-test/34-suite successful run. sourceEpoch is included in checkpoint
rollback and stale native sources are rejected. This is automated validation,
not a completed in-game reload or world-entry test.

Verified on 2026-10-11 against the copied test instance, without running scripts,
starting Minecraft, launching Gradle, or modifying the original instance.

## Verified Interface

- Exact event: `youyihj.zenutils.api.reload.ScriptReloadEvent.Post`.
- Optional companion: `ScriptReloadEvent.Pre`, used only to defer an outstanding
  refresh when another reload starts.
- Bus: `MinecraftForge.EVENT_BUS`.
- Stock Forge 1.12.2's `FMLCommonHandler.bus()` uses the same bus object. Register
  only once, not once on each alias.
- `ReloadCommand.reloadScripts(ICommandSender)` posts synchronously on its
  calling thread. `/mm-reload_client` calls it on the client command thread;
  `/ct reload` and `/mm-reload` may run on the integrated-server thread.
- The installed MMCE `ModIntegrationCrafttweaker.onScriptsReloaded(Post)` has
  default NORMAL priority. It reloads script-built machines, modifiers and
  events, recipe registry, joins the structure-cache future, and refreshes JEI
  preview wrappers before returning.
- Subscribe at LOWEST and only mark a pending notification. A client END tick
  performs the lightweight Ponder refresh on the client main thread.
- `InternalUtils.getScriptStatus()` is a public, read-only field getter.
  `ReloadCommand` sets `ScriptStatus.STARTED` only after the Post dispatch
  returns; checking it while a refresh is pending avoids re-entering the Post
  dispatch on client-command reloads. This is one enum check, not a machine scan.

## Success And Failure

Post has only a requester field, with no success flag or cancellation marker.
Installed ZenUtils 1.28.7 posts it after both success and failure returns from
ScriptReloader. A Java exception that aborts execution before Post may prevent
the signal entirely.

The installed ScriptReloader differs from the bundled source snapshot: the
binary does one non-syntax load and tests `LoaderStage.ERROR`; the source
snapshot contains an extra syntax-validation pass. The binary is authoritative.
Do not infer success from the existence of Post or from an empty logger.

MMCE already changes native registries during Pre and Post, including failed
script reloads. The Ponder bridge refreshes from the resulting native registry
after a completed signal; it never retries or executes the scripts. It must not
claim "script reload successful". Exceptions in the Ponder callback are logged,
not retried every tick; a later completed reload can trigger another attempt.

The startup CraftTweaker `ScriptRunEvent.Post` is a different event and is not
observed. A completion received before the initial Ponder registry is ready is
discarded rather than replayed after startup.

## Root Integration Contract

Implemented public entry point:

```java
MmceScriptReloadCompat.register(PonderIndex::reload,
    () -> PonderIndex.getGeneration() > 0L);
```

Call only from ClientProxy initialization. The compat class also guards physical
client, `modularmachinery`, `zenutils`, and `crafttweaker` presence before loading
any optional event or status class. Initial support is explicitly restricted to
MMCE 2.3.2 and ZenUtils 1.28.7, the versions verified here.

Repeated Post notifications coalesce before the client consumes them. Pre
suspends an older pending signal until a new Post arrives. No World, requester,
player, event object, or compiled scene is retained. No geometry is baked.

ClientProxy, PonderIndex and MmceMachineIndex integration was completed by
root and subsequently reviewed read-only; see the current guard document.

## Implemented Files

- `compat/mmce/MmceScriptReloadCompat`: client entry point, one Forge bus
  registration, HIGHEST Pre observer, LOWEST Post observer, guarded END-tick
  execution. The native event callbacks only signal the queue. A status API
  failure disables the optional bridge; a refresh callback failure consumes
  that attempt but permits a later reload notification.
- `compat/mmce/MmceScriptReloadApi`: exact version and physical-side guards,
  non-initializing optional class resolution, exact event-class comparison,
  public static enum-returning status getter validation.
- `compat/mmce/MmceReloadRefreshQueue`: thread-safe, coalescing completion state.
  The callback runs outside the queue lock, and a new completion during a
  refresh remains queued for a later tick.

No imports or field descriptors reference optional MMCE, ZenUtils, or
CraftTweaker classes. The bridge does not retain a World, player, requester, or
native event. Idle ticks return before accessing Minecraft or reflection. While
a completion is pending, polling is limited to the single read-only native
status getter; no registry scan occurs until the one-shot callback.

The native status field is not volatile. This bridge does not modify it or
claim that its getter is a script-success signal. It conservatively waits for
the observed STARTED state and cancels an older pending request on the next
Pre. If native dispatch aborts before resetting status, the bridge does not
attempt to repair or rerun native scripts. Manual Ponder reload remains an
independent fallback.

## Verification

Run:

```powershell
& 'C:\CODEX\Ponder-MMCE-Integration\verification\mmce-script-reload\RunReloadUnitTests.ps1'
```

On 2026-10-11, offline Java-8-target compilation succeeded and all 24 focused
JUnit tests passed (10 queue, 10 API/guards, 4 source-isolation tests).

Coverage includes idle ticks, startup discard, duplicate completion
coalescing, cancellation by a new Pre, client-thread/native-status gates,
worker-thread signaling, callbacks outside the lock, preserving newer
completions, callback failure without automatic retry, absent-mod/server/
unknown-version guards before optional classloading, ignoring unrelated and
startup event types, status API validation/failure, exact event priorities,
one bus registration, and no script execution/scene compilation/model baking.
The two additional API tests verify that reflected VirtualMachineError and
ThreadDeath, including nested InvocationTargetException wrappers, propagate
instead of being mistaken for an optional-API failure.

The independent runner compiles only these new classes and tests against
existing cached dependencies. It does not run Gradle, launch Minecraft, invoke
native script reloads, bake models, or write to the original test instance.
Live end-to-end command testing remains a separate acceptance step.
An independent two-thread audit probe also reproduced the next-Pre overlap;
the unit-test pass does not establish native registry snapshot safety.

## Binary Evidence

- `validation/client-sandbox/mods/zenutils-1.28.7.jar`
  SHA-256 `702B10F6DF944CDF8E0CAEABE115C826A0C013F3588D90F2DF817B3BA8C0D068`.
- `validation/client-sandbox/mods/ModularMachinery-CE-2.3.2.jar`
  SHA-256 `83676773E8C4F63963A48B4921F05898DCDBFDF777230E53E9830CD8F18177FB`.
- `javap -p -c`: ReloadCommand, ScriptReloader, ScriptReloadEvent and its Pre/Post
  classes, InternalUtils, FMLCommonHandler.
- `javap -p -c -v`: ModIntegrationCrafttweaker, confirming NORMAL annotation and
  synchronous future.join before the handler returns.
- `javap -p -c`: MMCE CommonProxy registration on MinecraftForge.EVENT_BUS.
