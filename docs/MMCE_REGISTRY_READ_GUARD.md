# MMCE Registry Read Guard

Implemented and integrated on 2026-10-11. The complete project subsequently
passed its unified Gradle build and automated checks. This final review did
not run Minecraft or native scripts, or modify the original instance.

## Root Integration API

Namespace: `net.createmod.ponder.foundation.content.mmce.MmceRegistryReadGuard`

```java
public static <T> T read(Supplier<T> reader);
public static <T, E extends Exception> T readChecked(CheckedSupplier<T, E> reader) throws E;
public static long currentEpoch();
public static final long NO_COMPLETION = -1L;
```

`CheckedSupplier<T, E>` is a public nested functional interface with
`T get() throws E`. A rejected read throws the public nested
`MmceRegistryReadGuard.ReloadInProgressException`, an IllegalStateException.

The integration is complete. MmceMachineIndex.refresh captures IndexedMachines
inside readChecked and publishes it only after leaving the monitor. Its native
machine loop and completion check enforce the existing 1.5-second soft budget.
currentEpoch is captured during that same guarded read and stored as
sourceEpoch. It is stable and nonnegative while the read executes; outside a
read it returns NO_COMPLETION while any native reload is incomplete.

getSnapshot and getDefaultModelSpec guard their native work and reject a
sourceEpoch mismatch before dereferencing an old RuntimeMachine.source.
Previously cached immutable snapshots remain readable without native access.
Checkpoint and restore both preserve sourceEpoch alongside machine metadata,
snapshot cache, failures and generation, so rollback cannot legitimize stale
native objects.

ClientProxy.init registers the optional reload bridge. The full
PonderIndex.reload and general plugin callbacks are not held under this guard.
Its lifecycle and MMCE plugin checkpoint hooks keep the published scene
registry and backing MMCE data aligned after a failed plugin reload.

## Event Contract

The bridge's HIGHEST Pre acquires the same monitor as bounded reads, waits only
for a current bounded read to finish, increments the epoch and records an
active reload, then releases the monitor. The verified MMCE NORMAL Pre can
then begin mutation. Reads during the reload throw immediately; they do not
wait for the Post event or hold the client thread until scripts finish.

The LOWEST Post pairs with a Pre on the same thread. Once all matching reloads
finish, it publishes a new stable epoch under the monitor and queues that
epoch. No pending native status field is consulted. Duplicate/unmatched Post
does not advance the epoch or unlock a different thread's reload.

Nested/repeated Pre notifications conservatively require matching Posts.
Incomplete or malformed sequences stay closed rather than permitting a
possibly partial registry read. If native execution throws before Post, reads
remain refused without blocking; there is no automatic script rerun or reset.
A new reload alone does not prove that an abandoned old reload completed.

Normal nested reads on the same thread are supported. A reload attempted
inside a native read throws before MMCE's NORMAL Pre and invalidates the gate,
including when the reader catches that exception. This prevents publishing
the outer result after an unsafe reentrant event. The guard uses no game
objects or optional APIs and retains no requester, World, or event.

The queue coalesces epochs, discards startup completions, rejects stale epochs,
and preserves a newer completion received during a callback. The callback runs
outside the queue and guard monitors. If Pre begins after the epoch check,
each root-owned native read is still protected by the shared guard.

## Verified Post Tail

Installed MMCE 2.3.2 completes machine registry reload, modifiers, events,
recipes, factory additions and joined structure-cache work inside its NORMAL
Post handler. Command wrappers only return after ReloadCommand. ZenUtils's
remaining tail logs and sets ScriptStatus.

MMCE does schedule GeoModelExternalLoader.onReload, which subsequently updates
its own baked geometry and animation caches. Bytecode confirms that this
loader reads DynamicMachineModelRegistry.getAllModels and does not write the
machine registry or default-model binding maps used by this adapter. Ponder's
static preview does not consume those native baked/animation caches.

The nonvolatile ScriptStatus getter has been removed from the compatibility
API entirely. No native reflection invocation remains in the reload bridge,
so InvocationTargetException cannot hide a VM error there. VM errors and
ThreadDeath from classloading or guarded readers propagate unchanged.

## Verification

The initial `verification/mmce-script-reload/RunReloadUnitTests.ps1` independently compiled
the new classes for Java 8 and passed 39/39 tests: 13 epoch queue tests,
8 optional API tests, 4 source-isolation tests, and 14 native-read guard tests.
Those tests are included in the unified total below, not an additional total.

Two-thread tests verify that Pre cannot return while a native read is active,
Pre may run outside the bounded read without locking the full plugin callback,
the next read refuses partial data, overlapping threads cannot complete one
another's reload, and exceptions release read monitors/depth correctly.

Final integration verification has also completed:

- Unified `test build forgeServerShimJar` succeeded. The final read-only review
  independently counted 188 tests across 34 suites from
  `build/test-results/test/TEST-*.xml`, with 0 failures, errors or skips.
- MmceMachineIndexReloadTest covers preserving the old index during native
  reload, rejecting uncached access before touching the native source, reading
  immutable cached structures during/after reload, and refusing stale sources
  after checkpoint rollback into a newer native epoch.
- Optional-dependency/class-loading checks: 10/10, recorded in
  `validation/mmce-class-loading-20261011-015859-729/report.json`.
- Artifact-auditor regression cases: 27/27, recorded in
  `validation/artifact-audit-regression-20261010-175914-374.json`.
- Synthetic installation/rollback cases: 11/11, recorded in
  `validation/mod-install-tests-20261010175914.json`. The report explicitly
  records gameStarted=false and originalInstanceChanged=false.
- The audited production candidate SHA-256 is
  `9DDC9114E18981120680BA2279947A6B26E80755AC77E03BF39411D8372934A0`.

The final read-only integration review found no new blocking issue in the
guard, epoch, lazy-access or rollback wiring. This is code and automated-test
evidence, not in-game acceptance: no live reload command, world entry, visual
preview, multiplayer or actual game rollback was verified in this review.
The safety claim is limited to the audited MMCE/ZenUtils event lifecycle.
Other add-ons that mutate native data outside that lifecycle are not covered.
The soft budget does not forcibly interrupt an individual native getter.
