# MMCE Script Reload Concurrency Audit

This document records the initial race and recommendations. The implemented
follow-up is described in `MMCE_REGISTRY_READ_GUARD.md`. The queue-only probe
remains a negative control; guarded-read concurrency regressions now pass.
Root has completed index, lazy-read, source-epoch, checkpoint and ClientProxy
integration. The unified 188-test/34-suite run passed; the final read-only
integration review found no new blocking issue. None of these checks is an
in-game reload/world-entry acceptance result.

Audited on 2026-10-11. No game, script reload, Gradle, deployment, or original
instance modifications were performed.

## Original Blocking Finding

The original notification queue protected its own pending flag, not the native
registry read performed by the refresh callback. It is not possible to prove
that the original bridge was single-thread safe when a reload command could run on
the integrated-server thread.

The narrow case "Pre occurs between status read and takePending" behaves as
follows:

1. If only Pre occurs, it clears pending before takePending, so the older
   refresh is skipped.
2. If the next Pre and Post both occur in that gap, pending becomes true again
   and an old STARTED observation can consume the new completion. A generation
   token could reject this stale observation.
3. The more important case remains even with a generation token: after
   takePending succeeds, refresh runs outside the queue monitor. A concurrent
   Pre can return, followed immediately by MMCE's NORMAL Pre handler mutating
   machine and model registries while Ponder reads them.

Read-copy-read, a second status check, an epoch check immediately before the
callback, or rejecting only worker-thread Post notifications does not close
the third race. Rejecting worker Post also does not stop a later worker Pre
from interrupting an already running client refresh.

## Deterministic Reproduction

`verification/mmce-script-reload/ReloadRaceProbe.java` runs the actual queue:
the simulated client enters refresh, the worker executes reloadStarted and
begins simulated native mutation, then the refresh resumes and observes the
overlap. The probe is diagnostic evidence, not a passing correctness test.

```powershell
javac --release 8 -d verification/mmce-script-reload/race-classes src/main/java/net/createmod/ponder/compat/mmce/MmceReloadRefreshQueue.java verification/mmce-script-reload/ReloadRaceProbe.java
java -cp verification/mmce-script-reload/race-classes net.createmod.ponder.compat.mmce.ReloadRaceProbe
```

Observed result:

```text
NATIVE_PRE_OVERLAPS_CLIENT_REFRESH=true
```

## Actual Native Ordering

The installed ZenUtils 1.28.7 ReloadCommand bytecode:

- Sets RELOAD before posting Pre.
- Posts Pre synchronously, then executes the scripts, then posts Post
  synchronously.
- Discards EventBus.post's boolean return for both events.
- Sets STARTED only after Post returns, outside any finally block.

The installed MMCE 2.3.2 Pre and Post handlers both have default NORMAL
priority. Its Pre clears the machine model registry, resets existing machine
maps, clears structure information pools, preloads machines, loads JSON
machines, and replaces native registered machines. Therefore even Pre alone
has substantial mutations that can overlap a lock-free Ponder refresh.

The HIGHEST bridge Pre precedes the verified MMCE NORMAL Pre handler, so it is
a usable barrier point for these known mutations. It is not a guarantee about
other add-ons that mutate data before the event or at an earlier/equal
priority.

## Status, Cancellation, Exceptions

`InternalUtils.scriptStatus` flags are exactly ACC_PRIVATE | ACC_STATIC
(0x000a), not ACC_VOLATILE. Its getter is a bare getstatic and setter a bare
putstatic. Polling it does not create a cross-thread synchronization edge.

Publishing queue.pending after a Post does publish native writes that precede
that notification, including RELOAD and the earlier MMCE NORMAL handler.
However the later STARTED write occurs after the queue publication, and its
cross-thread observation has no independent happens-before guarantee.
Repeated reflection invocation does not turn the field volatile.

The exact Pre, Post, and their parent have no Cancelable annotation or
cancellation override. In the verified Forge Event API they are not
cancellable; setCanceled would throw. In addition, ReloadCommand ignores the
post return value, so cancellation is not a supported command-redirection
hook.

Forge EventBus.post stops iteration and rethrows a listener failure. ZenUtils
does not restore status in finally:

- Failure before Post may leave RELOAD and produces no completion.
- Failure in a Post listener before our LOWEST handler yields no completion.
- Failure after our handler may leave a pending completion and RELOAD.
  The bridge must not interpret that as success or rerun scripts.
- ScriptReloader returning false is different: Post still runs, native MMCE
  handlers still run, and ordinary flow subsequently sets STARTED.

## Implemented Recommendation

The integrated fix shares a small private synchronization guard between the bridge's
HIGHEST Pre and a bounded, audited native MMCE snapshot capture. Hold that
guard for the complete native read/copy, not only the notification poll. Pre
must acquire it before returning to the verified NORMAL MMCE mutation.
Release it before invoking general Ponder plugins, UI work, or arbitrary
callbacks. Publish an immutable snapshot afterward using root's transaction.
Pairing/epoch state should reject stale status observations and overlapping
Pre/Post generations.

Holding the queue monitor across the entire PonderIndex.reload Runnable
would exclude the known next-Pre mutation, but also blocks the integrated
server while arbitrary plugin callbacks run. A callback waiting on that
server would deadlock. That is not a safe general-purpose callback contract.

An alternative is an upstream-supported way to serialize all native reload
and snapshot work on the client thread, but the installed non-cancellable
events do not provide one. If a bounded snapshot guard cannot be introduced,
automatic registry refresh should remain disabled rather than being labeled
fully safe. The native reload scripts must not be intercepted or rerun by
this bridge.

## Historical Small Fix

The intermediate MmceScriptReloadApi unwrapped nested InvocationTargetException wrappers and
rethrows VirtualMachineError and ThreadDeath. It still propagates ordinary
reflection failures for the optional compatibility handler to report.
Two new tests cover direct and nested wrappers, increasing focused test
results to 24/24. No root/index/plugin files were changed.

The final implementation removes that native status getter and reflection
invocation entirely, so the wrapper path no longer exists in the reload
bridge. Current classloading and guarded-reader tests verify fatal errors are
not swallowed; the current integration/test status is documented separately.
