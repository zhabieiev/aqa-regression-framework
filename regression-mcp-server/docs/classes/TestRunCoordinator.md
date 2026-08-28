# Class dossier: `TestRunCoordinator`

Anchor: `master` at the commit this file is committed under. Read against the
tree, not against `ARCHITECTURE.md`'s summary or any prior report. Line
numbers are given only where a claim needs one, with the line of code quoted
beside it; everything else is stated structurally so it survives an unrelated
edit.

Source read in full this pass:
`regression-mcp-server/src/main/java/com/aqa/mcp/execution/TestRunCoordinator.java`
(418 lines) plus every file cited below.

---

## 1. IDENTITY

| Field | Value |
|---|---|
| Path | `regression-mcp-server/src/main/java/com/aqa/mcp/execution/TestRunCoordinator.java` |
| Lines | 418 (`wc -l`) |
| Kind | `public final class TestRunCoordinator implements AutoCloseable` — a stateful per-JVM service |
| Package | `com.aqa.mcp.execution` |
| Nested types | `Active` (private static), `TimeoutScheduler` (package-private nested `@FunctionalInterface` extending `AutoCloseable`), `SystemTimeoutScheduler` (private static, the default `TimeoutScheduler`) — the "(+3 nested)" in `ARCHITECTURE.md` |
| Tier | **4** (`ARCHITECTURE.md` class inventory) — references classes up to tier 3 (`ReportCapture`, `ProcessOwnershipTracker`, `SystemProcessView`) |
| Fan-in | **1** — only `RegressionMcpServer` references it in production (`ModuleBoundaryRulesTest` line 99 contains the string `import com.aqa.mcp.execution.TestRunCoordinator;` inside a *synthetic source fixture*, not a real dependency) |
| Contract-exposure bucket | **B** — Behaviour-visible. It cannot alter client-facing JSON *shape* (that is `RunSnapshot`/`SurefireSummary`/`FailureArtifact`/`ArtifactContent`), but it originates almost every *value* in a run snapshot and every execution/report error code |

**History** (`git --no-pager log --follow`):

- `5217e0f` (2026-08-14, "Implement safe MCP test execution lifecycle") —
  first appearance; the start/get/cancel lifecycle, the terminal paths, the
  file lock, `recoverIfUnowned`, the ownership observer.
- `4537f52` (2026-08-17, "Implement Stage 14 MCP report and artifact
  tools") — added `summary`, `failureSummary`, `artifacts`, `readArtifact`
  and their shared in-memory-terminal guard.
- `75adf49` (2026-08-25, "Add RunSnapshot.skippedTests; close
  TECHNICAL_DEBT.md item A2") — **the last substantive change**: `capture`
  changed from `void` to `Integer`, `persistTerminal` gained a
  `skippedTests` parameter, and the guarded assignment
  `Integer captured = capture(run); if (captured != null) skippedTests = captured;`
  was added at all four terminal call sites plus `recoverIfUnowned`.

---

## 2. RESPONSIBILITY

**Owns:** the entire lifecycle of the *single* test run this JVM may have in
flight — validate → acquire the cross-process lock → persist `QUEUED` →
launch Maven on a worker thread → publish `RUNNING` once a timeout is
installed → wait, clean up processes, capture reports, persist a terminal
snapshot, release the lock — plus restart reconciliation of a run the
previous process left non-terminal.

**Must never own:**

- MCP tool/schema wiring — stays in `RegressionMcpServer`. Respected: this
  class has no `io.modelcontextprotocol` import and no JSON.
- Request validation rules — delegated to the injected
  `Supplier<TestRunRequestValidator>`. Respected.
- Process launching mechanics / `ProcessBuilder` — delegated to
  `MavenProcessLauncher` (`DirectMavenProcessLauncher` is the only
  production impl and the only file in `execution` allowed to name
  `ProcessBuilder`, enforced by `ReadOnlyProductionBoundaryTest`).
  Respected.
- On-disk persistence format — delegated to `RunStore`. Respected (this
  class never touches `Files` directly; `terminate`/`cleanup` touch only
  `Process`/`ProcessHandle`).
- Report parsing — delegated to `ReportCapture`. Respected.

It does construct `RunStore`, the worker pool, and the ownership observer
directly (see §5) — that is composition, not a boundary violation, but the
two executors being non-injectable is the root of the §13c test gaps.

---

## 3. PUBLIC SURFACE

Every member, its callers, and whether it is reachable from an MCP tool.

| Member | Purpose | Callers | MCP tool |
|---|---|---|---|
| `TestRunCoordinator(Path, Supplier<TestRunRequestValidator>)` | production 2-arg ctor; delegates to the 6-arg ctor with all real seams | `RegressionMcpServer.createServer` (line 75); `RegressionMcpServerContractTest` (5×, for description-only contracts) | — |
| `TestRunCoordinator(Path, Supplier, MavenProcessLauncher)` | 3-arg test ctor | none in tree (superseded by the 5-arg) | — |
| `TestRunCoordinator(Path, Supplier, MavenProcessLauncher, TimeoutScheduler, Function<Map,MavenRuntimeConfiguration>)` | 5-arg test ctor | `TestRunCoordinatorTest.coordinator(...)`, `CrossJvmLockTest`, the `secondCapture…` test | — |
| `TestRunCoordinator(Path, Supplier, MavenProcessLauncher, TimeoutScheduler, Function, ProcessView)` | 6-arg test ctor (adds fake `ProcessView`); since the worker seam was added it delegates to the 7-arg ctor, supplying the default worker `Executors.newFixedThreadPool(3, …"regression-mcp-run-worker")` — it is no longer the field-initialising base | `StaleRunRecoveryTest.coordinator(...)` | — |
| `TestRunCoordinator(Path, Supplier, MavenProcessLauncher, TimeoutScheduler, Function, ProcessView, ExecutorService worker)` | 7-arg test ctor (adds injectable worker `ExecutorService`); the field-initialising base ctor — every narrower ctor now reaches it. `close()` shuts this executor down like any other, so a caller passing one hands over its lifetime | none in tree yet (its purpose is the path-A characterization test B8 still needs) | — |
| `RunSnapshot start(StartTestRunRequest, Map<String,String>)` | validate, lock, persist QUEUED, submit `execute` to the worker, return the QUEUED snapshot synchronously | `RegressionMcpServer.startTestRunTool` (line 110); `TestRunCoordinatorTest`, `StaleRunRecoveryTest`, `CrossJvmLockTest`, `RegressionMcpServerContractTest`, STDIO test (via `ControlledMcpServerMain`) | `regression_start_test_run` |
| `RunSnapshot get(String id)` | in-memory `Active` snapshot if the id matches an active run, else `store.get(id)` | `RegressionMcpServer.runActionTool` (get branch, line 158); `cancel` (line 105); tests' `awaitState`/`awaitTerminal` | `regression_get_test_run` |
| `RunSnapshot cancel(String id)` | set `run.cause = CANCELLED` (CAS), `terminate` the process if launched, return the current in-memory snapshot; delegates to `get(id)` for a non-active id | `RegressionMcpServer.runActionTool` (cancel branch, line 158); `close()` (line 374); tests | `regression_cancel_test_run` |
| `SurefireSummary summary(String id)` | reject malformed id, reject an in-memory-active non-terminal run, else `store.summary(id)` | `RegressionMcpServer.testSummaryTool` (line 121); STDIO test | `regression_get_test_summary` |
| `SurefireSummary failureSummary(String id)` | same guard, `store.failureSummary(id)` | `RegressionMcpServer.failureSummaryTool` (line 129); STDIO test | `regression_get_failure_summary` |
| `List<FailureArtifact> artifacts(String id)` | same guard, `store.artifacts(id)` | `RegressionMcpServer.failureArtifactsTool` (line 137); contract + STDIO tests | `regression_get_failure_artifacts` |
| `ArtifactContent readArtifact(String id, String artifactId)` | same guard, `store.readArtifact(id, artifactId)` | `RegressionMcpServer.readFailureArtifactTool` (line 147); contract + STDIO tests | `regression_read_failure_artifact` |
| `void close()` | idempotent; cancel the active run, shut the worker pool (10 s grace then `shutdownNow`), close the timeout scheduler, `shutdownNow` the observer | JVM shutdown hook + `CloseAwareInputStream` EOF callback (`RegressionMcpServer.createServer` lines 81-82); every test's teardown | — (not a tool) |
| `int ownedProcessCount(String id)` *(package-private)* | count of retained process identities for a run — from the live `tracker` if active, else from `store.persisted` | **`TestRunCoordinatorTest` line 264 only** | **not reachable from any tool** |

**Findings:**

- **`ownedProcessCount` has exactly one caller and it is a test.** By
  dossier convention this is a finding; severity **low** — it is a
  documented (`/** …ownership-observation seam for lifecycle tests. */`),
  package-private, read-only seam with no production path, in the same
  category as the injectable `TimeoutScheduler`/`ProcessView` seams. No
  action recommended.
- Two constructors have *zero* callers in the tree. The 3-arg constructor
  is a documented overload sitting between the 2-arg and 5-arg forms; worth
  deleting on the next touch, not worth a debt entry. The 7-arg constructor
  is the new worker seam — uncalled only until the path-A characterization
  test (B8) is written, its intended and sole purpose. Neither is dead in
  the strict sense. Every other public and package-private member has at
  least one caller.
- All seven non-`close` public methods map 1:1 onto the seven
  execution/report MCP tools. `close()` is driven only by the shutdown
  hook and the stdin-EOF callback.

---

## 4. STATE AND INVARIANTS

### Fields (all `private final` unless noted)

| Field | Type | Mutability | Why |
|---|---|---|---|
| `root` | `Path` | final | repository root, passed to `RunStore`, `MavenInvocationFactory` |
| `validator` | `Supplier<TestRunRequestValidator>` | final | re-invoked on **every** `start()` (line 65 `validator.get().validate(request)`) so a module-list change is picked up without a restart |
| `store` | `RunStore` | final | hard-constructed `new RunStore(root)` (line 58) |
| `launcher` | `MavenProcessLauncher` | final | injectable seam |
| `runtimeLoader` | `Function<Map<String,String>, MavenRuntimeConfiguration>` | final | injectable seam; default `MavenRuntimeConfigurationLoader::load` |
| `worker` | `ExecutorService` | final | `Executors.newFixedThreadPool(3, …"regression-mcp-run-worker")` (line 56) — **not injectable** |
| `timeouts` | `TimeoutScheduler` | final | injectable seam; default `SystemTimeoutScheduler` |
| `ownershipObserver` | `ScheduledExecutorService` | final | `Executors.newSingleThreadScheduledExecutor(…"regression-mcp-ownership")` (line 57) — **not injectable** |
| `processView` | `ProcessView` | final | injectable seam; default `SystemProcessView` |
| `active` | `AtomicReference<Active>` | final ref, mutable target | the single-run slot; `null` when idle |
| `closed` | `AtomicBoolean` | final | set once by `close()` |
| `recoveryBlocked` | `AtomicBoolean` | final | set by `recoverIfUnowned()` when a prior run's ownership cannot be proven safe; makes every future `start()` throw `STALE_RUN_RECOVERY_REQUIRED` |

### `Active` (private static) fields

`snapshot` (`volatile RunSnapshot`, reassigned on every state transition);
`request`/`runtime`/`lock` (final); `cause` (`final AtomicReference<TestRunState>`
— the race-winner latch: CANCELLED/TIMED_OUT/ERROR, `null` until something
sets it); `process`/`timeout`/`observation`/`tracker` (all `volatile`,
assigned once during `execute`).

### Invariants enforced in constructors (quoted)

`TestRunCoordinator` has **no** explicit invariant check in its own
constructor. The invariants it depends on are enforced downstream:

- `RunSnapshot` compact constructor:
  `if (runId == null || module == null || environment == null || tags == null || state == null || createdAt == null) throw new IllegalArgumentException("Run snapshot fields are required.");`
- `SurefireSummary` compact constructor enforces
  `passed != tests - failures - errors - skipped` and
  `failureRecords.size() != failures + errors` as hard errors — the values
  this class carries onto a snapshot as `skippedTests` therefore came from
  an internally-consistent summary.
- `OwnedProcessIdentity` / `ObservedProcess`:
  `if (pid <= 0 || startInstant == null || depth < 0 …) throw new IllegalArgumentException(…)`.

Its own *behavioural* invariants (not constructor-enforced):

1. **One run per JVM, one run per repository.** `active.compareAndSet(null, next)`
   (line 72) is the in-JVM gate; `store.acquireActiveLock()` (line 67, a
   `FileChannel.tryLock` on `.regression-mcp/runs/active.lock`) is the
   cross-process gate. Both must succeed or `start()` throws
   `RUN_ALREADY_ACTIVE`.
2. **`RUNNING` is not published until the timeout is installed and
   retained.** Lines 146-154: the `RUNNING` snapshot and the ownership
   observer are only created inside `if (run.cause.get() == null)` after
   `run.timeout` has been assigned a non-cancelled future. If cancellation
   or an immediate timeout won the race, the run stays `QUEUED` on disk
   and goes straight to the deterministic cleanup/terminal path. Tested by
   `runningIsNotObservableUntilTheOwnedTimeoutHandleIsInstalled`,
   `cancellationAtSchedulingPublicationBoundaryStaysQueuedUntilCleanup`,
   `immediateTimeoutAtPublicationBoundaryProducesOnlyTimedOutTerminalState`.
3. **The launched root identity is persisted while still `QUEUED`.** Line
   134 `store.update(run.snapshot, tracker.identities())` runs before the
   `RUNNING` transition, so a scheduler failure between launch and
   publication still has durable ownership evidence for cleanup and for
   restart recovery. Comment at lines 132-133; tested by
   `timeoutSchedulingFailureNeverPublishesRunningAndCleansProcessAndLock`.
4. **Terminal persistence and capture publication precede lock release.**
   `persistTerminal` is always called before the `finally` block's
   `run.lock.close()` (line 181). Tested by
   `terminalPublicationAndLockReleaseFollowRequiredCapturePublication` and
   `repositoryLockRejectsAnotherCoordinatorThenReleasesAfterTerminalPersistence`.
5. **The terminal state is the first cause that was latched, or the process
   exit code.** `firstCause(run, fallback)` (lines 249-255) CAS-loops so
   the *first* writer of `run.cause` wins; `persistTerminal` re-runs
   `firstCause` so a late `cancel()`/timeout cannot change an
   already-latched terminal state. Tested by
   `normalCompletionCancelsTimeoutAndLateCallbackCannotOverwriteTerminalState`.

### Thread-safety contract

Threads that exist while a run is active:

| Thread | Created by | Touches |
|---|---|---|
| **MCP request thread** (the SDK's stdin reader) | outside this class | `start`/`get`/`cancel`/`summary`/…/`close`; reads `active`, `closed`, `recoveryBlocked`; `start` writes `active` + creates the disk record + `worker.submit`; `cancel` writes `run.cause` (CAS) and calls `terminate` |
| **worker thread** (1 of the 3-thread `worker` pool) | `worker.submit(() -> execute(next))` (line 78) | runs `execute` end to end; the *only* writer of `run.process`/`run.timeout`/`run.observation`/`run.tracker`; holds `synchronized (run)` during `capture` and `persistTerminal`; writes `run.snapshot` |
| **2× drainer threads** (the other 2 of the `worker` pool) | `worker.submit(stdout)` / `worker.submit(stderr)` (lines 138-139) | each `BoundedLogDrainer` reads one process stream, writes one capped log file; publishes `written`/`observed`/`dropped` as `volatile` longs; no shared mutable state with the coordinator |
| **ownership observer thread** (`ownershipObserver`, single) | `ownershipObserver.scheduleAtFixedRate(() -> observe(run), 100, 100, MILLISECONDS)` (line 153) | `observe(run)` takes `synchronized (run)`, calls `run.tracker.observe()` + `store.update(...)` while `!run.snapshot.terminal()` |
| **timeout thread** (`SystemTimeoutScheduler`'s single executor) | `timeouts.schedule(...)` (line 141) | one task: `run.cause.compareAndSet(null, TIMED_OUT)` then `terminate(launched)` |
| **JVM shutdown-hook thread** / **stdin-EOF thread** | `RegressionMcpServer` | `close()` |

Guarding:

- `active` / `closed` / `recoveryBlocked` — `Atomic*`, lock-free.
- `Active.cause` — `AtomicReference`; the cross-thread race latch. Every
  writer uses `compareAndSet(null, …)`; every terminal path reads it
  through `firstCause`.
- `Active.snapshot` / `process` / `timeout` / `observation` / `tracker` —
  `volatile`; written only by the worker thread (except `snapshot`, also
  written by `observe`'s `store.update` path — but `observe` only *reads*
  `run.snapshot` and re-persists it, it does not reassign the field).
- `synchronized (run)` (the `Active` monitor) — guards `capture(Active)`
  (line 223), `persistTerminal` (line 235), and `observe` (line 290).
  These three are mutually exclusive. `cancel` does **not** take this
  monitor — it works purely through `run.cause` (atomic) and `run.process`
  (volatile), so it never blocks behind a capture.
- `RunStore` — every mutating method is `synchronized` on the store
  instance *and* on a static `RunStore.STATUS_IO` monitor shared by all
  `RunStore` instances in the JVM, so concurrent coordinators (tests) and
  the observer thread serialise their `status.json` writes.

**Worker pool size is 3, hard-coded** (line 56
`Executors.newFixedThreadPool(3, …)`). What determines it: one thread runs
`execute()`; two run the `BoundedLogDrainer`s that `execute` submits at
lines 138-139; `awaitDrainers()` blocks the `execute` thread on those two
drainers, so they *must* run on different threads. Only one run is ever
active (invariant 1), so 3 is exactly "1 execute + 2 drainers" with **zero
headroom** (see §11 O7). The default `newFixedThreadPool` queue is an
unbounded `LinkedBlockingQueue`, so `worker.submit` only ever rejects
after `worker.shutdown()`.

---

## 5. DEPENDENCIES OUT

| Type | Why | Seam? |
|---|---|---|
| `RunStore` | all persistence: `create`, `update` (×2 overloads), `updateCapture`, `captureLayout`, `log`, `get`, `persisted`, `summary`, `failureSummary`, `artifacts`, `readArtifact`, `acquireActiveLock`, `exists`, `active` | **concrete, hard-constructed** (`new RunStore(root)`, line 58) |
| `MavenProcessLauncher` | `launch(MavenInvocation) -> Process` | **interface**, injectable; default `DirectMavenProcessLauncher` |
| `TestRunCoordinator.TimeoutScheduler` | `schedule(Runnable, int) -> ScheduledFuture<?>` + `close()` | **interface** (nested), injectable; default `SystemTimeoutScheduler` |
| `ProcessView` | `find(pid)`, `descendants(pid, max)`, `destroy(identity, forcibly, grace)` | **interface**, injectable; default `SystemProcessView` |
| `Function<Map<String,String>, MavenRuntimeConfiguration>` | load the Maven runtime for a `start` | **functional**, injectable; default `MavenRuntimeConfigurationLoader::load` |
| `Supplier<TestRunRequestValidator>` | fresh validator per `start` | **functional**, always supplied by the caller |
| `ExecutorService worker` | runs `execute` + drainers | **JDK interface, injectable** via the widest (7-arg) constructor; default `Executors.newFixedThreadPool(3, …"regression-mcp-run-worker")` supplied by the 6-arg ctor. `close()` shuts it down unconditionally (`shutdown` → 10 s `awaitTermination` → `shutdownNow`), so passing one in hands its lifetime to the coordinator — a shared or reused pool must not be injected |
| `ScheduledExecutorService ownershipObserver` | the 100 ms observer | **concrete, hard-constructed** — *not injectable* |
| `MavenInvocationFactory` (static `create`) | build the Classworlds command line | concrete static |
| `ProcessOwnershipTracker` | `new` at lines 130 and 266 | **concrete, hard-constructed** |
| `BoundedLogDrainer` | `new` at lines 136-137 | **concrete, hard-constructed** |
| `ReportCapture` | `new ReportCapture()` at line 228 | **concrete, hard-constructed** |
| `RunSnapshot`, `TestRunState`, `RunId`, `ValidatedTestRunRequest`, `StartTestRunRequest`, `MavenInvocation`, `MavenRuntimeConfiguration`, `RunCaptureLayout`, `ObservedProcess`, `OwnedProcessIdentity`, `CaptureStatus`, `ReportCapture.CaptureOutcome`, `SurefireSummary` / `FailureArtifact` / `ArtifactContent` (return types), `ExecutionPlanningException` | value objects / enums / DTOs / the module exception | — |
| `Process`, `ProcessHandle`, `ScheduledFuture`, `CompletableFuture` (via drainer) | JDK | — |

**Injectable through a constructor:** `validator`, `launcher`, `timeouts`,
`runtimeLoader`, `processView`, `worker` (the last via the 7-arg ctor only).
**Hard-constructed:** `store`, `ownershipObserver`, and — per run
— `ProcessOwnershipTracker`, `BoundedLogDrainer` ×2, `ReportCapture`.

`ownershipObserver` remains hard-constructed. `worker` is now injectable
(7-arg ctor), which is what makes §13c path A **reachable** from a test —
a hold-until-released executor can delay `execute()` past a `cancel()`.
The path is reachable, not yet tested (B8).

---

## 6. DEPENDENTS IN

Only `RegressionMcpServer` (production). What it relies on, split by
signature vs behaviour:

**Signature:** `new TestRunCoordinator(path, () -> ExecutionPlanningFactory.validatorFor(root))`
(line 75); the seven tool handlers call the seven public methods with the
argument shapes above.

**Behaviour:**

- **Exception type + code.** Every handler is `catch (ExecutionPlanningException e) { return errorResult(e.code(), e.getMessage()); }`. It relies on *all* failures being `ExecutionPlanningException` (an
  `IllegalArgumentException`, hence `RuntimeException`) carrying a `.code()`.
  A non-`ExecutionPlanningException` `RuntimeException` from a public method
  would escape to the MCP SDK unmapped — see §9 for the one realistic case
  (`RejectedExecutionException` from `start` during shutdown).
- **`start` is non-blocking** — returns the `QUEUED` snapshot synchronously
  and runs Maven on the worker. `RegressionMcpServer` maps the returned
  snapshot immediately with `runOutput` and never awaits terminal state.
- **`get` reflects the in-memory `Active` snapshot first**, falling back to
  disk only once the run is no longer active — so a poll during a run
  never sees a stale on-disk `QUEUED` after the in-memory state moved to
  `RUNNING`.
- **`cancel` is idempotent and returns immediately** with the current
  (usually still non-terminal) snapshot; the caller is expected to poll
  `get` for the terminal `CANCELLED`.
- **`summary`/`failureSummary`/`artifacts`/`readArtifact` throw
  `RUN_NOT_TERMINAL`** while the in-memory `Active` run for that id is
  non-terminal, even if `status.json` has not yet caught up.
- **`close` is idempotent, bounded (~10 s), best-effort** — used from both
  the shutdown hook and the stdin-EOF callback, possibly concurrently.

Tests additionally rely on: QUEUED persisted before RUNNING; the launched
root identity persisted while QUEUED; terminal snapshot + capture published
before `run.lock.close()`; `startedAt == null` until RUNNING; `exitCode`
null for a run cancelled before launch; the specific codes
`RUN_ALREADY_ACTIVE`, `STALE_RUN_RECOVERY_REQUIRED`, `RUN_NOT_FOUND`,
`INVALID_ARGUMENTS`, `RUN_NOT_TERMINAL`, `NOT_FOUND`, `ERROR`,
`TIMED_OUT`; deepest-first descendant cleanup; the `TimeoutScheduler`
nested type and the package-private constructors.

---

## 7. CONTRACT EXPOSURE

`TestRunCoordinator` is bucket **B**: it does not define any JSON schema,
but it originates the *values* that `RegressionMcpServer.runOutput` serialises
into `data` for `regression_start_test_run` / `regression_get_test_run` /
`regression_cancel_test_run`, and it decides every execution/report error
code. `SurefireSummary`, `List<FailureArtifact>`, `ArtifactContent` pass
through it from `RunStore` unmodified.

### Values it originates → snapshot field → tool

| Snapshot field (`runOutput`) | Origin in this class | Notes |
|---|---|---|
| `runId` | `RunId.generate()` (line 68), `"run-" + 32 hex` | |
| `module` / `environment` / `headless` / `tags` / `timeoutSeconds` | copied from `ValidatedTestRunRequest` via `snapshot(...)` (line 344) | `tags` = `request.effectiveTagExpression()` = `"not @wip"` or `"(<expr>) and not @wip"` |
| `state` | `TestRunState.name()` — `QUEUED` from `snapshot(...)`, `RUNNING` from `replace(...)` (line 150), terminal from `persistTerminal` via `firstCause` / `process.exitValue()==0 ? PASSED : FAILED` (line 160) | `ERROR` reason for restart recovery is `replaceWithReason` |
| `reason` | `state.name()` for every non-QUEUED snapshot (via `replace`); `"SERVER_RESTART_RECOVERY"` (via `replaceWithReason`, line 273); **`null` for the initial QUEUED snapshot only** (`snapshot(...)` passes `null`) | `runOutput` omits `reason` when null |
| `createdAt` | `Instant.now()` in `start` (line 69) | always present |
| `startedAt` | `Instant.now()` at the RUNNING transition (line 150); **stays `null` on the early-cause path** (a run cancelled/timed-out before launch) | `runOutput` omits when null |
| `finishedAt` | `Instant.now()` in `persistTerminal` (line 240) | omitted until terminal |
| `exitCode` | `process != null && !process.isAlive() ? process.exitValue() : null` (line 237) — `null` for a run that never launched or is cancelled-before-launch | omitted when null |
| `reason` (`TIMED_OUT`/`CANCELLED`/`ERROR`) | `firstCause` latch | |
| `stdoutBytes` / `stderrBytes` / `stdoutTruncated` / `stderrTruncated` | `BoundedLogDrainer.bytes()` / `.truncated()` in `persistTerminal` (lines 238-241); `0` / `false` on any path without drainers | the `RUNNING` snapshot hard-codes `0/0/false/false` (line 150) — `TECHNICAL_DEBT.md` B4 |
| `skippedTests` | `(int) SurefireSummary.skipped()` from `ReportCapture.CaptureOutcome`, carried via the guarded local (lines 116/120, 162, 169, 175) and `persistTerminal`'s parameter | `null` until a Surefire report parses; `RUNNING` transition carries `run.snapshot.skippedTests()` (line 150, i.e. `null`) |

### Error codes it originates

- `RUN_NOT_FOUND` — `get(id)`/`cancel(id)` for a malformed **or** unknown id
  (`notFound()`, line 366).
- `INVALID_ARGUMENTS` — `summary`/`failureSummary`/`artifacts`/`readArtifact`
  for a malformed-format id (`"runId has an invalid format."`, lines 186,
  195, 206, 215) and `readArtifact` for a malformed `artifactId` (in
  `RunStore`).
- `RUN_NOT_TERMINAL` — the four report/artifact methods when the in-memory
  `Active` run for that id is non-terminal (lines 189, 198, 209, 218).
- `RUN_ALREADY_ACTIVE` — `start` when the file lock is held (another JVM) or
  the in-JVM `active` slot is taken (lines 74; also from
  `store.acquireActiveLock`).
- `MAVEN_RUNTIME_UNAVAILABLE` — `start` when `closed` (line 63); also
  propagated from `runtimeLoader`, `store.create`, `store.acquireActiveLock`
  IOException.
- `STALE_RUN_RECOVERY_REQUIRED` — `start` when `recoveryBlocked` (line 64).

Everything from `validator.validate(...)` (`UNSUPPORTED_MODULE`,
`UNSUPPORTED_CAPABILITY`, `INVALID_TIMEOUT`, `INVALID_TAG_EXPRESSION`,
`INVALID_ARGUMENTS`) and from `RunStore` (`NOT_FOUND`, `REPORT_MALFORMED`,
`REPORT_INDEX_CORRUPT`, `RUN_STATE_CORRUPT`, `UNSUPPORTED_MIME_TYPE`) passes
straight through.

See §11 O6 / hypothesis H4 for the `RUN_NOT_FOUND` vs `INVALID_ARGUMENTS`
divergence and its `docs/TOOLS.md` mismatch (logged as D14).

---

## 8. TEST COVERAGE

| Test file | Type | What it pins | What would pass unnoticed |
|---|---|---|---|
| `TestRunCoordinatorTest` (19 `@Test`, PROC + concurrency) | real child processes via `ControlledProcessFixture`, `ManualTimeoutScheduler`, injected `runtime()` | PASS/FAIL/CANCELLED/TIMED_OUT transitions; the QUEUED→RUNNING publication boundary (5 tests around the timeout-install race); idempotent cancel; deepest-first descendant cleanup + retained-identity persistence; forced termination; the cross-coordinator `RUN_ALREADY_ACTIVE` + lock-release-after-terminal ordering; capped-log byte/observed/dropped persistence; `normalCompletion…` proves a late timeout callback cannot overwrite a terminal state; `secondCaptureCallInTheRuntimeExceptionPath…` (see O1) | the **early-cause** path (§13c A) and the **`InterruptedException`** path (§13c C) — zero coverage (`TECHNICAL_DEBT.md` B8); the execute()-side `skippedTests` *preservation* semantics (O1); `close()`'s `worker.awaitTermination` timeout → `shutdownNow` branch (no test hangs a worker 10 s) |
| `StaleRunRecoveryTest` (8 `@Test`, UNIT, fake `ProcessView`, launcher that asserts it is never called) | every `recoverIfUnowned` branch: QUEUED→ERROR/`SERVER_RESTART_RECOVERY`; staged-capture published before the recovery terminal state; live-identity cleaned; reused-PID **not** killed; RUNNING-without-identity → `recoveryBlocked`; corrupt `status.json` → `recoveryBlocked`; dead identity → no termination attempt; descendant recovery after the root is gone | the `skippedTests` value carried through recovery — no assertion anywhere in the file (`TECHNICAL_DEBT.md` D10) |
| `CrossJvmLockTest` (1 `@Test`, PROC, second real JVM holds the lock) | `start()` throws `RUN_ALREADY_ACTIVE` and creates **no** run directory while another JVM owns `active.lock` | anything past the lock acquisition |
| `RegressionMcpServerContractTest` (execution-tool rows) | tool **descriptions/annotations**; one real cancelled run and one real failing-with-artifacts run through `ControlledCoordinatorFactory` to exercise `get`/`artifacts`/`readArtifact` schemas end to end | schema/description drift is caught; lifecycle races are not this file's job |
| `RegressionMcpServerStdioIntegrationTest` (`servesControlledExecutionToolsOverStdioAndCleansRunOnEof`, `eofClosesAnActiveControlledRunAndPersistsCancellation`, `servesFailureArtifactToolsForARealFailingRun…`) | real JSON-RPC over a spawned server: `QUEUED`→`RUNNING`, `RUN_NOT_TERMINAL`, `RUN_ALREADY_ACTIVE`, `RUN_NOT_FOUND` (well-formed-unknown id), `INVALID_TIMEOUT`, schema rejection of an extra key, cancel→`CANCELLED`, EOF→persisted `CANCELLED` with ≥1 owned process, the full failure-artifact walk | a **malformed-format** runId to `summary`/`artifacts` (only well-formed-unknown is tested → `RUN_NOT_FOUND`; the malformed case returns `INVALID_ARGUMENTS`, untested — O6/H4); path A and path C |
| Fixtures (not tests) | `ControlledProcessLauncher`, `ControlledProcessFixture`, `ControlledCoordinatorFactory`, `RunStoreLockHolderFixture`, `ControlledMcpServerMain`, `FailingWithArtifactsMcpServerMain`, nested `ManualTimeoutScheduler` | — |

Roughly 28 test methods exercise this class. Two of the four `execute()`
terminal paths and one guard are the material gaps.

---

## 9. FAILURE BEHAVIOUR

**Exception model.** Everything this class throws is
`ExecutionPlanningException extends IllegalArgumentException extends
RuntimeException` with a `.code()`. `RegressionMcpServer` catches exactly
`ExecutionPlanningException` per handler and maps it to
`{"status":"error","error":{"code","message"}}`.

**`start`** throws, in order: `MAVEN_RUNTIME_UNAVAILABLE` (closed) →
`STALE_RUN_RECOVERY_REQUIRED` (recoveryBlocked) → validator codes →
`runtimeLoader` `MAVEN_RUNTIME_UNAVAILABLE` → `acquireActiveLock`
`RUN_ALREADY_ACTIVE` / `MAVEN_RUNTIME_UNAVAILABLE` → `active` CAS-lose
`RUN_ALREADY_ACTIVE` (line 74) → `store.create` IOError → wrapped
`MAVEN_RUNTIME_UNAVAILABLE`, **caught by `catch (RuntimeException)` at line
80 which clears `active`, closes `lock`, and rethrows**. The one case the
line-80 catch rethrows as a non-`ExecutionPlanningException` is
`worker.submit` throwing `RejectedExecutionException` — which happens only
after `close()` has shut the pool. That would escape `RegressionMcpServer`'s
`catch (ExecutionPlanningException e)` unmapped and reach the MCP SDK. Edge,
shutdown-only; noted, not logged (server is terminating).

**`get`** — `RUN_NOT_FOUND` for a malformed or unknown id; otherwise the
in-memory or persisted snapshot. Never blocks.

**`cancel`** — for a non-active id, delegates to `get` (so `RUN_NOT_FOUND`
for a bad id); for the active id, `run.cause.compareAndSet(null, CANCELLED)`
then `terminate(process)` if the process is set (best-effort; `terminate`
swallows its own `InterruptedException` and re-asserts the flag), returns
the pre-terminal in-memory snapshot. Cancellation *completes* asynchronously
on the worker.

**`summary` / `failureSummary` / `artifacts` / `readArtifact`** —
`INVALID_ARGUMENTS` (malformed id / `artifactId`) → `RUN_NOT_TERMINAL`
(in-memory active non-terminal) → then `RunStore` codes (`RUN_NOT_FOUND`,
`NOT_FOUND` with detail `PRE_STAGE14_RUN`/`SUREFIRE_UNAVAILABLE`/
`ARTIFACT_NOT_FOUND`, `REPORT_MALFORMED`, `REPORT_INDEX_CORRUPT`,
`RUN_STATE_CORRUPT`, `UNSUPPORTED_MIME_TYPE`).

**`execute` (worker thread, no caller).** All four paths funnel to
`persistTerminal`. A throw from `capture` or `persistTerminal` *inside a
catch block* propagates through `finally` (which still clears `active` and
releases the lock) and out of `execute` — **uncaught on the worker thread**,
handled by the default handler (stack trace to stderr). The run is then
left non-terminal on disk with no in-memory owner and the lock free; the
next server start's `recoverIfUnowned` resolves it (QUEUED/RUNNING → ERROR /
`SERVER_RESTART_RECOVERY`). §11 O2 argues path C reaches exactly this state
in normal operation.

**Malformed / absent client input** — every documented input path yields a
structured `ExecutionPlanningException`, never an NPE. The one unguarded
`Objects.requireNonNull(timeouts.schedule(...), "Timeout scheduler returned
no handle.")` (line 141) concerns an internal seam, not client input, and
carries a message.

---

## 10. RESOURCES AND LIFECYCLE

**Threads (5 for the coordinator's lifetime):** the 3-thread `worker` pool
(`regression-mcp-run-worker`), the single `ownershipObserver`
(`regression-mcp-ownership`), and `SystemTimeoutScheduler`'s single executor
(`regression-mcp-timeout`). Created in the constructor (the timeout one when
the default `TimeoutScheduler` is built). Closed in `close()`:
`worker.shutdown()` → `awaitTermination(10, SECONDS)` → `worker.shutdownNow()`
on timeout; `timeouts.close()` → `executor.shutdownNow()`;
`ownershipObserver.shutdownNow()`.

**Per-run transient threads:** `execute` runs on one worker thread; two
`BoundedLogDrainer`s run on the other two. All three end when the process
exits (drainers on stream EOF, `execute` after `persistTerminal`).

**Process:** one Maven child per run, from `launcher.launch` (line 125) to
`cleanup(run, process)` / `terminate(process)`. Descendants are tracked
(`ProcessOwnershipTracker`) and reaped deepest-first
(`cleanup` → `cleanupDescendants()` + `cleanupAll()`; `terminate` also walks
`process.toHandle().descendants()` sorted by descendant-count desc). Every
kill re-confirms `identity.sameProcess(observed)` against a fresh
`ProcessView.find` first (never kills a reused PID). On the early-cause path
no process exists.

**Lock / file handles:** one `RunStore.Lock` (`FileChannel` +
`FileLock` on `.regression-mcp/runs/active.lock`) per run — acquired in
`start` (line 67); released in `execute`'s `finally` (line 181, `run.lock.close()`),
or in `start`'s line-73 branch if the `active` CAS loses, or in `start`'s
line-80 catch. `recoverIfUnowned` takes and releases its own lock via
try-with-resources (line 259). Log-file handles are owned entirely by
`BoundedLogDrainer` (try-with-resources in `run()`); capture-file handles by
`ReportCapture`.

**On `close()`:** `closed.compareAndSet(false, true)` (idempotent guard); if
`active` holds a run, `cancel(run.snapshot.runId())` (set cause, terminate
process); then the three executor shutdowns above. `close()` does **not**
directly release the `RunStore.Lock`, persist a terminal snapshot, or join
`execute` beyond the 10 s `worker.awaitTermination` — it relies on the
worker's `execute` reaching its `finally`. If the 10 s elapses,
`worker.shutdownNow()` interrupts the worker → `process.waitFor()` throws
`InterruptedException` → path C (with O2's caveat). The STDIO test
`eofClosesAnActiveControlledRunAndPersistsCancellation` shows the normal
case (fixture process dies fast on `terminate`, `execute` finishes within
the grace period, `status.json` = `CANCELLED`).

---

## 11. OBSERVATIONS

### O1 — `secondCaptureCallInTheRuntimeExceptionPath…` does not exercise the guard it is named for

**Evidence.** The fixture's `ExitValueFailsOnceProcess.exitValue()` throws
`IllegalStateException` on its *first* call. Tracing `execute` for that
test (no `cause` is ever set, so `terminal == null` at line 159): the first
`process.exitValue()` call is **line 160**
(`terminal = process.exitValue() == 0 ? PASSED : FAILED`), which is *before*
the try-block `capture(run)` at line 161. So the throw lands in
`catch (RuntimeException)` (line 171) having never run the try-block
capture; the catch block's `capture(run)` at line 174 is the **first and
only** capture call, it returns `1`, and `if (captured != null)` is a plain
assignment on that path. `persistTerminal`'s own `process.exitValue()`
(line 237) is then the *second* call and returns the real value.

**Impact.** The guard at lines 162 / 169 / 175 is genuinely load-bearing,
but only on one interleaving the test never reaches: try-block `capture`
(line 161) succeeds and sets `skippedTests`, then `persistTerminal` (line
163) throws a `RuntimeException` (realistically `store.update` failing with
a wrapped `IOException`), then `catch (RuntimeException)` re-runs
`capture(run)` which now returns `null` (persisted status is no longer
`PENDING`), and the guard is what keeps the earlier count. Replacing the
guard with `skippedTests = capture(run)` would persist `null` and lose the
count on that path, and the whole suite would still pass. The test as
written still asserts something true (the `RuntimeException` path captures
and persists a skipped count) — it just does not prove the guard.

**Cost / risk.** 1 pass; low risk. A fixture that makes `persistTerminal`'s
*first* `store.update` throw once (e.g. an `AtomicMover`/`RunStore` wrapper),
leaving `exitValue()` alone, would drive line 161 → line 163-throw → line
174 (null) → guarded keep. Assert the persisted `skippedTests` equals the
line-161 value, not `null`.

**Relationship to `TECHNICAL_DEBT.md` D10.** D10 states the execute()-side
guard "does have a dedicated test … which forces a real capture to succeed
once, forces a second (necessarily null-returning) capture call, and
asserts …". That description does not match the control flow above. Logged
as **B11**, which qualifies D10's parenthetical.

### O2 — the `InterruptedException` path re-asserts the interrupt flag, then does interruptible NIO — it may be unable to persist a terminal state (PLAUSIBLE, unverified)

**Evidence.** `catch (InterruptedException)` (line 164) begins with
`Thread.currentThread().interrupt()` (line 165), then calls `cleanup`,
`awaitDrainers`, `capture(run)` (→ `store.persisted` → `Files.readString`),
and `persistTerminal` (→ `store.update` → `RunStore.replace` →
`Files.createTempFile` / `Files.writeString` / `Files.move`). `Files.readString`
and `Files.writeString` operate through `java.nio.channels.FileChannel`,
which is an `InterruptibleChannel`: a blocking read/write invoked on a
thread whose interrupt status is set closes the channel and throws
`ClosedByInterruptException` (an `IOException`). `RunStore.persisted` wraps
`IOException | RuntimeException` into `RUN_STATE_CORRUPT`; `RunStore.update`
wraps `IOException` into `MAVEN_RUNTIME_UNAVAILABLE`.

**Impact if confirmed.** On path C, `capture(run)` (line 168) already throws
`RUN_STATE_CORRUPT` before `persistTerminal` is reached; it propagates out
of the catch block, through `finally` (which still clears `active` and
releases the lock), and out of `execute` uncaught on the worker thread. The
run is left non-terminal (last persisted state `RUNNING` or `QUEUED`) with
no in-memory owner. Within the JVM the client polls `get` and sees a run
stuck `RUNNING`; a later server restart's `recoverIfUnowned` transitions it
to `ERROR` / `SERVER_RESTART_RECOVERY`. This is exactly the path
`TECHNICAL_DEBT.md` B8 flags as untested — the observation is that it may be
not just untested but non-functional.

**Not verified** — depends on JDK `FileChannel` interrupt semantics on the
target platform; not executed this pass. Folded into **B8** as a sharpening:
the characterization test for path C must assert `status.json` reaches a
terminal state, not merely that the branch ran.

### O3 — `synchronized (run)` is held across all of capture's filesystem work

`capture(Active run)` (line 223) holds the `Active` monitor across
`store.persisted` (read `status.json`), `new ReportCapture().capture(...)`
(walk the staging tree, parse every `TEST-*.xml`, SHA-256 every file up to
64 MiB total, two `ATOMIC_MOVE`s, write two index files), and
`store.updateCapture` (rewrite `status.json`). The 100 ms `observe(run)`
blocks on that monitor for the whole capture. **Impact: low** — capture runs
after `process.waitFor()` returns (paths B/C/D), so the process has already
exited and there are no new descendants for the paused observer to miss.
Still a lock-across-I/O smell. Logged as **C7** with the review trigger
"capture ever runs while the Maven process is still alive".

### O4 — capture publication and terminal persistence are two separate critical sections on the same monitor

`capture(Active)` (line 223) and `persistTerminal` (line 235) are separate
`synchronized (run)` blocks; between them the monitor is released. In that
window `observe(run)` can acquire it and re-persist the still-`RUNNING`
snapshot, and `status.json` transiently holds
`capture.status == COMPLETE/PARTIAL` together with `snapshot.state == RUNNING`.
**Not observable through any MCP tool** — `summary`/`failureSummary`/
`artifacts`/`readArtifact` gate on the in-memory `Active.snapshot.terminal()`
(still `false`), and `get` returns the in-memory `RUNNING` snapshot. A
direct reader of `status.json`, or a crash landing exactly there, would see
the inconsistency; on restart `recoverIfUnowned` re-runs `capture` (a no-op
since status ≠ `PENDING`) and resolves the run. Refactor-relevant (the
§13b collapse removes the gap); not separately logged.

### O5 — worker pool size 3 has zero headroom

Line 56 `Executors.newFixedThreadPool(3, …)`. The 3 is exactly "1 `execute`
+ 2 `BoundedLogDrainer`s"; `execute` submits the drainers to the same pool
and then `awaitDrainers` blocks on them, so they must land on the other two
threads. Any future change that adds a third `worker.submit` inside
`execute`'s pre-terminal section, or that permits two concurrent runs,
deadlocks. Worth a one-line comment on the pool declaration; not logged.

### O6 — malformed `runId` yields different error codes across the public surface, and `docs/TOOLS.md` documents only one

`get`/`cancel` → `RUN_NOT_FOUND` for a malformed id (line 88, `notFound()`);
`summary`/`failureSummary`/`artifacts`/`readArtifact` → `INVALID_ARGUMENTS`
(`"runId has an invalid format."`, lines 186/195/206/215). A
**well-formed-but-unknown** id → `RUN_NOT_FOUND` from all six.
`docs/TOOLS.md` (lines 98-102, 187-189) says the four report/artifact tools
return `RUN_NOT_FOUND` for "a missing or foreign `runId`" and never mentions
`INVALID_ARGUMENTS` for a malformed one. Full analysis and the
section-A judgement are under hypothesis **H4**; logged as **D14**.

### O7 — the private `snapshot(...)` factory is a one-call-site helper with 8 hard-coded arguments

`snapshot(id, request, state, created, started, finished, exit, reason,
stdout, stderr, stdoutTruncated, stderrTruncated)` (line 344) is called
exactly once — `start` line 70 — with
`started=finished=exit=reason=null, stdout=stderr=0,
stdoutTruncated=stderrTruncated=false`. It is effectively
`queued(id, request, createdAt)` wearing a 12-argument signature, and it is
the *only* producer of a `RunSnapshot` whose `reason` is `null`. A narrower
factory would make "a QUEUED snapshot has a null reason" explicit rather
than incidental. Cosmetic; not logged.

### O8 — `replaceWithReason` is `replace` plus a parameterised `reason`

`replace(...)` (line 352) and `replaceWithReason(...)` (line 359) build the
identical 17-component `RunSnapshot`; the only difference is `replace` uses
`state.name()` for `reason` and `replaceWithReason` takes it as a
parameter. `replace` could delegate:
`replace(s, state, …) → replaceWithReason(s, state, state.name(), …)`.
Cosmetic; not logged.

### O9 — `java.util.List` imported and also used fully-qualified

`import java.util.List;` (line 6); `java.util.List.of()` at line 242
(`run.tracker == null ? java.util.List.of() : run.tracker.identities()`).
The only FQN-despite-import in the file. Cosmetic; not logged.

### O10 — the four report/artifact guard blocks are 4× duplicated

The
`if (!RunId.valid(id)) throw …INVALID_ARGUMENTS…; Active current = active.get(); if (current != null && current.snapshot.runId().equals(id) && !current.snapshot.terminal()) throw …RUN_NOT_TERMINAL…;`
prologue is character-identical in `summary`, `failureSummary`, `artifacts`,
`readArtifact` (only the trailing `store.xxx(...)` differs). Extract
`private void requireTerminal(String id)`. The `RunId.valid` half is also
redundant with `RunStore`'s own re-check; the `!terminal()` half is not
(it covers "in-memory ahead of disk"). Refactor-relevant (§H5); not
separately logged.

---

## 12. REFACTOR VERDICT

**TEST FIRST.**

Not `SAFE NOW`: two of the four `execute()` terminal paths are unverified
(B8), the guard-preservation test for a third misfires (O1), and path C is
plausibly non-functional under a set interrupt flag (O2).

Not `DO NOT TOUCH`: the class has a clean, established seam pattern
(`launcher`/`timeouts`/`runtimeLoader`/`processView` all injectable) and ~28
tests; the two missing seams (worker `ExecutorService`) are a small,
low-risk addition.

**Characterization tests required before changing `execute()`:**

1. **Early-cause path (A)** — requires a **new seam**: an injectable worker
   `ExecutorService` (package-private constructor parameter, mirroring
   `launcher`/`timeouts`). Test: inject a hold-until-released executor;
   `start()`; `cancel(runId)`; release; assert `get(runId).state() ==
   CANCELLED`, `startedAt() == null`, `exitCode() == null`, the injected
   `MavenProcessLauncher` was never invoked, no owned processes persisted,
   `active` cleared, `acquireActiveLock()` succeeds afterward.
2. **`InterruptedException` path (C)** — **no production change needed to
   reach it** (H1): inject a `MavenProcessLauncher` returning a
   real-process-backed `Process` whose `waitFor()` throws
   `InterruptedException`. The test must assert `status.json` **reaches a
   terminal state** (CANCELLED), `finishedAt` set, lock released, `active`
   cleared — i.e. that the branch persists, not merely that it ran (O2).
3. **execute()-side `skippedTests` guard (O1)** — a fixture where the
   try-block `capture` (line 161) genuinely succeeds and the first
   `persistTerminal` `store.update` then throws once. Assert the persisted
   `skippedTests` equals the line-161 value, not `null`.

Only with 1-3 green: the §13b `finishTerminally(...)` collapse and the
`requireTerminal(id)` extraction (O10) become low-risk cleanups.

---

## 13. TERMINAL PATHS OF `execute()`

`execute(Active run)` (lines 112-183) is a single
`try { … } catch (InterruptedException) { … } catch (RuntimeException) { … }
finally { … }`. Four ways to reach `persistTerminal`.

### 13a — enumeration

#### Path A — early-cause return (lines 117-123)

- **Entry:** `run.cause.get() != null` at line 118 when `execute` starts —
  i.e. `cancel()` or a fired timeout latched a cause between `worker.submit`
  (line 78) and the worker thread entering `execute`. Nothing has been
  launched.
- **Ordered side effects:**
  1. `capture(run)` (line 119) — `synchronized (run)`. Staging dirs exist
     (from `store.create` → `prepareCaptureDirectories`) but are empty, so
     `ReportCapture.capture` throws "Required Surefire XML is absent." →
     returns `CaptureOutcome(CaptureMetadata(…, UNAVAILABLE, …), null)`;
     `store.updateCapture` sets capture status `UNAVAILABLE`; returns
     `null`. `skippedTests` stays `null`.
  2. `persistTerminal(run, run.cause.get(), null, null, null, null)` (line
     121) — `synchronized (run)`. `terminal = firstCause(run, cause)` =
     the latched cause (CANCELLED or TIMED_OUT); `exitCode = null`;
     `stdoutBytes = stderrBytes = 0`; `replace(...)` → terminal snapshot;
     `store.update(snapshot, List.of(), 0,0,0,0)` (tracker is `null`).
  3. `return` (line 122).
  4. `finally` (lines 177-182): `run.timeout` is `null` → skip;
     `run.observation` is `null` → skip; `active.compareAndSet(run, null)`;
     `run.lock.close()`.
- **No** process cleanup, **no** drainer wait, **no** timeout/observation
  cancellation (neither was created).
- **Client sees:** `CANCELLED` (or `TIMED_OUT`); `startedAt == null`,
  `finishedAt` set, `exitCode == null`, `skippedTests == null`, capture
  status `UNAVAILABLE`.

#### Path B — normal completion (lines 124-163)

- **Entry:** launch succeeded; `process.waitFor()` (line 156) returned; no
  `RuntimeException` through line 163.
- **Ordered side effects:**
  1. `process.waitFor()` returns (156).
  2. `cleanup(run, process)` (157) — `run.tracker.observe()`,
     `cleanupDescendants()`, terminate the root if it still matches its
     identity, `cleanupAll()`.
  3. `awaitDrainers(stdout, stderr)` (158) — up to 5 s per stream on
     `BoundedLogDrainer.completion()`.
  4. `terminal` = `run.cause.get()`; if `null`,
     `process.exitValue() == 0 ? PASSED : FAILED` (159-160).
  5. `capture(run)` (161) — `synchronized (run)`; status `PENDING` → real
     `ReportCapture.capture` of the Surefire staging the Maven process's
     redirected plugin wrote → publish Surefire (+ optional Allure) →
     `store.updateCapture`; returns `(int) summary.skipped()` (or `null` if
     the capture came back `UNAVAILABLE`/`MALFORMED`). `if (captured != null)
     skippedTests = captured` (162).
  6. `persistTerminal(run, terminal, process, stdout, stderr, skippedTests)`
     (163) — `synchronized (run)`; `exitCode = !isAlive() ? exitValue() :
     null`; drainer bytes + truncated flags; `replace(...)` → terminal
     snapshot; `store.update(..., observed/dropped byte counters)`.
  7. `finally`: `run.timeout.cancel(false)`; `run.observation.cancel(false)`;
     `active.compareAndSet(run, null)`; `run.lock.close()`.
- **Client sees:** `PASSED` / `FAILED` (or a latched `CANCELLED` / `TIMED_OUT`
  if the process then exited on its own); `startedAt` set, `finishedAt`
  set, `exitCode` set, `skippedTests` set iff a Surefire report parsed.

#### Path C — `catch (InterruptedException)` (lines 164-170)

- **Entry:** `process.waitFor()` (line 156) throws `InterruptedException` —
  the only statement in the try that declares it. In production: `close()`
  → `worker.shutdownNow()` after a 10 s `awaitTermination` timeout.
- **Ordered side effects:**
  1. `Thread.currentThread().interrupt()` (165) — re-assert the flag.
  2. `if (process != null) cleanup(run, process)` (166) — `process` is
     non-null (line 156 was reached).
  3. `awaitDrainers(stdout, stderr)` (167).
  4. `capture(run)` (168); `if (captured != null) skippedTests = captured`
     (169).
  5. `persistTerminal(run, firstCause(run, CANCELLED), process, stdout,
     stderr, skippedTests)` (170).
  6. `finally`: cancel `run.timeout`; cancel `run.observation`;
     `active.compareAndSet(run, null)`; `run.lock.close()`.
- **Client sees:** `CANCELLED` (or a cause already latched).
- **Caveat (O2):** with the interrupt flag re-asserted at step 1, the NIO
  in steps 4-5 may throw `ClosedByInterruptException`-derived
  `RUN_STATE_CORRUPT` / `MAVEN_RUNTIME_UNAVAILABLE`, in which case **no
  terminal snapshot is persisted** — `finally` still clears `active` and
  releases the lock, and the run is resolved only by a later restart's
  `recoverIfUnowned`. Unverified.

#### Path D — `catch (RuntimeException)` (lines 171-176)

- **Entry:** any `RuntimeException` (which includes every
  `ExecutionPlanningException`) from the try block that is not
  `InterruptedException`. Sources include: `MavenInvocationFactory.create`,
  `launcher.launch` (`MAVEN_LAUNCH_FAILED`),
  `processView.find(pid).orElseThrow` (`PROCESS_IDENTITY_UNAVAILABLE`),
  `new ProcessOwnershipTracker` (`PROCESS_IDENTITY_LIMIT_EXCEEDED`),
  `store.update` (`MAVEN_RUNTIME_UNAVAILABLE`),
  `Objects.requireNonNull(timeouts.schedule(...))` (NPE) / the scheduler
  throwing, `timeout.isCancelled()` → `TIMEOUT_SCHEDULING_FAILED`,
  `ownershipObserver.scheduleAtFixedRate` (`RejectedExecutionException`),
  `process.exitValue()` (line 160), `capture(run)` (line 161),
  `persistTerminal` (line 163).
- **Ordered side effects:**
  1. `if (process != null) cleanup(run, process)` (172) — `process` may be
     `null` (failure before line 126).
  2. `awaitDrainers(stdout, stderr)` (173) — either may be `null` (failure
     before lines 136-137).
  3. `capture(run)` (174); `if (captured != null) skippedTests = captured`
     (175).
  4. `persistTerminal(run, firstCause(run, ERROR), process, stdout, stderr,
     skippedTests)` (176).
  5. `finally`: `if (run.timeout != null) cancel`; `if (run.observation !=
     null) cancel` (both may be `null`); `active.compareAndSet(run, null)`;
     `run.lock.close()`.
- **Client sees:** `ERROR` (or a cause already latched — e.g. a timeout
  that fired before the scheduling path threw → `TIMED_OUT`). Tested by
  `timeoutSchedulingFailureNeverPublishesRunningAndCleansProcessAndLock`
  and `secondCaptureCallInTheRuntimeExceptionPath…` (see O1).

### 13b — side-effect commonality

| Side effect | A | B | C | D |
|---|---|---|---|---|
| `capture(run)` + `store.updateCapture` | ✓ | ✓ | ✓ | ✓ |
| null-guarded `skippedTests` assignment | ✓ | ✓ | ✓ | ✓ |
| `persistTerminal` (`synchronized`, `replace`, `store.update`) | ✓ | ✓ | ✓ | ✓ |
| `firstCause(run, <default>)` inside persist | cause already set | `run.cause` else PASSED/FAILED | default CANCELLED | default ERROR |
| `active.compareAndSet(run, null)` (finally) | ✓ | ✓ | ✓ | ✓ |
| `run.lock.close()` (finally) | ✓ | ✓ | ✓ | ✓ |
| `process.waitFor()` (normal return) | – | ✓ | throws | maybe |
| `cleanup(run, process)` | – (no process) | ✓ | ✓ (process ≠ null) | ✓ if process ≠ null |
| `awaitDrainers` | – (no drainers) | ✓ | ✓ | ✓ (args maybe null) |
| `Thread.currentThread().interrupt()` | – | – | ✓ | – |
| cancel `run.timeout` (finally) | no-op (null) | ✓ | ✓ | null-guarded |
| cancel `run.observation` (finally) | no-op (null) | ✓ | ✓ | null-guarded |

**Common to all four:** `capture` → guarded `skippedTests` →
`persistTerminal` → (finally) `active` clear → `lock.close()`. That five-step
tail is identical everywhere.

**Unique / conditional:** `process.waitFor()` as a normal return (B only);
`Thread.currentThread().interrupt()` (C only); `cleanup` + `awaitDrainers`
(absent in A; present but null-tolerant in B/C/D); timeout/observation
cancellation (unconditional in B/C, null-guarded in D, no-op in A).

**Evidence for/against a single terminal transaction.** The four paths
already converge on an identical tail. The only genuine per-path
differences are the *default cause* handed to `persistTerminal`, whether
`cleanup` + `awaitDrainers` ran first, and C's interrupt re-assert. A
private
`finishTerminally(Active run, TestRunState defaultCause, Process process,
BoundedLogDrainer stdout, BoundedLogDrainer stderr)` doing
`if (process != null) cleanup(run, process); awaitDrainers(stdout, stderr);
Integer c = capture(run); if (c != null) skippedTests = c;
persistTerminal(run, firstCause(run, defaultCause), process, stdout,
stderr, skippedTests);` would absorb B's tail and the whole of C and D,
reducing each catch/return site to one line; A would call it with
`process=stdout=stderr=null` (its `cleanup`/`awaitDrainers` become no-ops
via the null checks). This is **evidence that a collapse is feasible and
would eliminate the three near-identical blocks** — it is **not** a
recommendation. Two blockers stand in front of it: B8 (paths A and C need
characterization tests first) and O2 (path C may be broken in a way a naive
collapse preserves — the collapse must not re-assert the interrupt flag
before the persist, or must save/restore it around the NIO).

### 13c — the two untested paths

#### Path A (early-cause return)

- **A test must control:** that the worker thread does not begin
  `execute(next)` until after `cancel(runId)` (or a fired timeout) has
  latched `run.cause`.
- **Does the class give a test that control today? Yes, since the worker
  seam was added** — the 7-arg constructor takes the worker
  `ExecutorService`. Before that seam existed the worker was created
  internally and none of the constructors — the public 2-arg or the three
  package-private test ctors exposing `launcher`, `timeouts`,
  `runtimeLoader`, `processView` — took it. `start()` does
  `worker.submit(() -> execute(next))` and returns; a test that then calls
  `cancel()` is racing an idle 3-thread pool that picks the task up in
  microseconds. `cancel()` *is* callable at that point (`active` is set
  before `store.create` and `worker.submit`). With the seam a test can
  inject a hold-until-released executor and hold the worker between
  `worker.submit` and the body of `execute` for as long as it needs.
- **Seams that exist and do not help:** `TimeoutScheduler` (can latch the
  cause via `fire()`, but cannot delay the worker); `MavenProcessLauncher`
  (too late — the early-cause check precedes `launcher.launch`).
- **Seam that makes it reachable:** the injectable worker `ExecutorService`
  (7-arg constructor). Inject a hold-until-released executor; `start()`;
  `cancel(runId)`; release; assert the early-cause outcome.
- **Reachable from a test without modifying the class? Yes**, as of the
  worker seam. What remains is to write the test — it matches B8's own
  suggested "`CountDownLatch`-gated worker `ExecutorService` test double".
  The path is reachable, **not yet tested**.

#### Path C (`InterruptedException` catch)

- **A test must control:** that the worker thread, while inside
  `process.waitFor()` (line 156), receives an `InterruptedException`.
- **Does the class give a test that control today? Yes, via an existing
  seam.** `MavenProcessLauncher` is injectable and the returned `Process`
  is fully test-owned — the suite already subclasses `Process`
  (`ExitValueFailsOnceProcess`, `IgnoreGracefulTerminationProcess`). A
  launcher returning a `Process` whose `waitFor()` simply
  `throw new InterruptedException()` lands `execute` in
  `catch (InterruptedException)` with **no production change**. (Caveat:
  `processView.find(process.pid())` at line 128 must succeed first — either
  back the fake `Process` with a real short-lived OS process, as
  `ExitValueFailsOnceProcess` does, or use the 6-arg constructor to inject
  a fake `ProcessView` returning an `ObservedProcess` for the fake pid.)
- **Seams that exist:** `MavenProcessLauncher` + `Process` subclassing
  (sufficient to *reach* the branch); the 6-arg constructor's `ProcessView`
  (for the pid lookup).
- **Seams that do not exist:** none are needed to reach the branch — this
  contradicts B8's implication that "no test interrupts the worker thread"
  means a new seam is required. What is missing is any way to make the
  branch's own behaviour *observable in isolation*; per O2 the
  characterization test must assert `status.json` actually reaches a
  terminal state.
- **Reachable from a test without modifying the class? Yes** (H1
  confirmed).

### 13d — do the capture-and-keep blocks survive a collapse?

Under the `finishTerminally(...)` collapse in §13b, the three
`Integer captured = capture(run); if (captured != null) skippedTests =
captured;` blocks in B/C/D become **one** occurrence inside
`finishTerminally`, and path A's copy folds in as well. The repeated blocks
therefore **disappear** under the collapse. Consequently, extracting *only*
the capture-and-keep triple as its own helper — without the surrounding
`cleanup` / `awaitDrainers` / `persistTerminal` collapse — has **little
value**: it would still leave four call sites each doing the same three
neighbouring calls in the same order. The worthwhile unit of refactoring is
the whole terminal tail, not the capture fragment alone.

---

## Hypotheses — confirm / refute

| # | Verdict | Basis |
|---|---|---|
| **H1** | **CONFIRMED** | `process.waitFor()` (line 156) is the only `InterruptedException` source in the try; `launcher` and the returned `Process` are test-owned; a `Process` whose `waitFor()` throws lands execution in `catch (InterruptedException)` with no production change (given `processView.find(pid)` is made to succeed — real backing process or injected fake `ProcessView`). See §13c. |
| **H2** | **CONFIRMED** | The early return runs on the internal, non-injectable `worker` pool (line 56) and its guard (line 118) precedes every injectable collaborator. Deterministic reach needs a new seam — an injectable `ExecutorService`. A test calling `cancel()` after `start()` returns is racing an idle pool and would be flaky. See §13c. |
| **H3** | **CONFIRMED, with a sharpening** | The guard *is* load-bearing, but only on the narrow interleaving where the try-block `capture` (line 161) succeeds and `persistTerminal` (line 163) then throws a `RuntimeException`; there `capture(run)` at line 174 returns `null` (persisted status no longer `PENDING`) and the guard preserves the line-162 count — a plain `skippedTests = capture(run)` would persist `null`. **But** the test named for this (`secondCaptureCallInTheRuntimeExceptionPath…`) never reaches that interleaving: its fixture throws at line 160, before the try-block capture, so capture runs exactly once (in the catch) and the guard is a plain assignment there. The guard is currently unproven by tests. See §11 O1, logged as B11. Early-cause / normal / `InterruptedException` paths each only ever reach `capture` once, so a plain assignment would not regress *them*. |
| **H4** | **CONFIRMED; NOT section A** | Malformed-format `runId` → `RUN_NOT_FOUND` from `get`/`cancel` (line 88), `INVALID_ARGUMENTS` from `summary`/`failureSummary`/`artifacts`/`readArtifact` (lines 186/195/206/215). Well-formed-but-unknown `runId` → `RUN_NOT_FOUND` from all six (`RunStore` → status file absent). Traced to `regression_get_test_run` / `regression_cancel_test_run` (`data` error `code`) and the four report/artifact tools. `docs/TOOLS.md` (lines 98-102, 187-189) documents `RUN_NOT_FOUND` for the report/artifact tools' bad `runId` and lists `INVALID_ARGUMENTS` only as "schema-level input rejection" — it documents **one** of the two codes and is silent on the app-layer `INVALID_ARGUMENTS`. **Not section A** ("Published behaviour returns a wrong or misleading answer"): `INVALID_ARGUMENTS` for syntactically invalid input is neither wrong nor misleading — arguably more precise than `RUN_NOT_FOUND`. The gap is that `docs/TOOLS.md` is incomplete. Logged as **D14** (closed by a one-line docs note, matching D12's shape). |
| **H5** | **CONFIRMED** | The prologue of `summary` (185-190), `failureSummary` (194-199), `artifacts` (206-210), `readArtifact` (215-219) is character-identical apart from the trailing `store.summary(id)` / `store.failureSummary(id)` / `store.artifacts(id)` / `store.readArtifact(id, artifactId)`. Extract `requireTerminal(String id)`. The `RunId.valid` half duplicates `RunStore`'s own check (`readSummary` line 177, `terminalRecordForArtifacts` line 261); the `!terminal()` half does not (it guards "in-memory `Active` ahead of `status.json`"). See §11 O10. |
| **H6** | **CONFIRMED** | `capture(Active)` (line 223) and `persistTerminal` (line 235) are two separate `synchronized (run)` blocks; `observe` (line 290) synchronises on the same monitor; `run.observation.cancel(false)` is in `finally` (line 179), after `persistTerminal`. Between the two blocks, `observe` can re-persist the `RUNNING` snapshot and `status.json` transiently carries `capture=COMPLETE/PARTIAL` with `state=RUNNING`. **Not client-observable through any MCP tool** — the report/artifact methods gate on the in-memory `Active.snapshot.terminal()` (still `false`) and `get` returns the in-memory `RUNNING` snapshot. A post-`persistTerminal` observation tick is a no-op (`!run.snapshot.terminal()` is now false). See §11 O4. |
| **H7** | **CONFIRMED** | `capture(Active run)` holds `synchronized (run)` across `store.persisted` (read `status.json`), `new ReportCapture().capture(...)` (tree walk, XML parse, SHA-256 of every file up to 64 MiB, two `ATOMIC_MOVE`s, index write) and `store.updateCapture` (rewrite `status.json`). See §11 O3, logged as C7. |
| **H8** | **CONFIRMED** | If `worker.submit(() -> execute(next))` (line 78) throws `RejectedExecutionException` after `store.create` (line 77), the `catch (RuntimeException)` at line 80 clears `active` and closes `lock` and rethrows — leaving a `QUEUED` record on disk with no in-memory owner and the lock free. The next coordinator's `recoverIfUnowned` lists it via `store.active()` (non-terminal), takes the `tracker == null` branch, and `store.update(replaceWithReason(snapshot, ERROR, "SERVER_RESTART_RECOVERY", …))` — resolving it to `ERROR` / `SERVER_RESTART_RECOVERY`. Covered by `StaleRunRecoveryTest.queuedRunRecoversToStructuredTerminalError`. In practice `submit` only rejects after `close()`, so this coincides with shutdown; impact low. Not separately logged (recovery already handles it; the `start`-side trigger is a shutdown-race edge). |
| **H9** | **PARTIALLY CONFIRMED** | The private `snapshot(...)` factory (line 344) has a single call site (`start`, line 70) at which 8 of its 12 arguments are hard-coded (`started=finished=exit=reason=null`, `stdout=stderr=0`, two `false`) — §11 O7. `replaceWithReason` (line 359) differs from `replace` (line 352) **only** in `reason` derivation (`state.name()` vs a parameter) — §11 O8. But "passed the same constant at every call site" is **not** true of `replace`: its two call sites (line 150 RUNNING, line 240 terminal) pass different, computed arguments. Only the one-call-site helpers (`snapshot`, `replaceWithReason`) are trivially "constant at every call site". |
| **H10** | **CONFIRMED** | `import java.util.List;` (line 6); `java.util.List.of()` used fully-qualified at line 242. The only FQN-despite-import in the file. §11 O9. |

---

## What this dossier did NOT verify, and why

- **O2 is not verified by execution.** Whether `Files.readString` /
  `Files.writeString` inside `RunStore` actually throw
  `ClosedByInterruptException` when the worker thread's interrupt flag is
  set (path C) depends on JDK `FileChannel` interrupt semantics on the
  runtime platform; this pass changed no code and ran no test. It is stated
  as PLAUSIBLE. The B8 edit and the §12 characterization test for path C
  are written to force the question.
- **The full `RunStore` / `ReportCapture` / `SurefireSummaryParser` /
  `AllureResultParser` internals** were read only to the depth needed to
  trace what `TestRunCoordinator` observes from them (return types, thrown
  codes, the `synchronized`/`STATUS_IO` locking, the `(int) summary.skipped()`
  cast). Their own dossiers are separate review-order items.
- **`recoverIfUnowned`'s interaction with a concurrently-live second
  coordinator** (the `catch (ExecutionPlanningException)` at line 277,
  "another coordinator owns the repository lock") is covered by
  `StaleRunRecoveryTest` only through the fake-view unit tests; the
  real-two-JVM case is `CrossJvmLockTest`, which exercises `start`, not
  `recoverIfUnowned`. Not independently re-derived here.
- **Timing / performance** of holding the monitor across capture (O3) — not
  measured; the "low impact" judgement rests on capture running strictly
  after `process.waitFor()` returns, which is structural, not benchmarked.
- **`mvn` was run only as `validate`** (per this pass's instruction); the
  276/0/0/5 `regression-mcp-server` suite result from earlier the same day
  is taken as current since no production code changed.
- **The 3-arg constructor** was confirmed to have no in-tree caller by
  grep; a caller outside the repository (there is none for this module) was
  not ruled out.
