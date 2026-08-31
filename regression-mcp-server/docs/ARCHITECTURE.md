# Architecture

This document maps `regression-mcp-server`'s internal structure: what each
package owns, how its 67 production classes depend on one another, how a
request actually flows through the system, what survives a restart, where
untrusted data enters, and what has to change to extend it. It is written
for a fresh agent — any vendor, no conversation history — picking up
inspection or refactoring work on this module.

**Anchor commit**: `7107c49fa305dde53ac3d6d0e009da67d773d859` (branch
`master`), confirmed CI-green via `gh run list --commit`. This document
prefers structural claims ("X depends on Y only through the nested
interface Z") over line numbers, because line numbers rot silently while
structural claims survive refactoring — `docs/TECHNICAL_DEBT.md` item A1's
own line-number citation already drifted once across an unrelated edit.
Where a line number is given at all in this document, the line of code is
quoted beside it so drift is visible rather than silent.

For per-tool schema/input/output detail, see [`docs/TOOLS.md`](TOOLS.md).
For per-test coverage detail, see [`TEST_MAP.md`](TEST_MAP.md). For known
debt and open questions, see the root [`docs/TECHNICAL_DEBT.md`](../../docs/TECHNICAL_DEBT.md).

## Layer map

The module has three packages under `com.aqa.mcp`.

**`com.aqa.mcp` (root)** owns: server bootstrap and the single MCP entry
point (`RegressionMcpServer` — tool registration, JSON-Schema authoring,
domain-to-JSON mapping, error envelopes for all 14 tools);
`REGRESSION_ROOT` resolution (`RepositoryRootResolver`, `RepositoryRoot`);
reactor-module discovery and classification (`ModuleList`,
`ModuleDescriptor`, `ModuleType`, `ModuleTypeClassifier`); Gherkin
feature/scenario discovery (`FeatureDiscovery`); the overview tool's data
assembly (`FrameworkOverview`); and the composition seam that wires a
`TestRunRequestValidator` from a resolved module list
(`ExecutionPlanningFactory`). It must never own process launching, run
persistence, or Java-source static analysis.

**`com.aqa.mcp.execution`** owns everything about actually running a
Maven test suite and reporting on it: request validation, the
direct-launcher process path, process ownership/cleanup, the run store
and lifecycle, and result capture/parsing. It must never own MCP
tool/schema wiring (stays in the root package) or Java-source/Gherkin
static analysis (validation's job).

**`com.aqa.mcp.validation`** owns static analysis of product-module Java
source: scanning, the rule/report model, the three fixed rule lists, and
the three public `Tool` classes that each wire their own MCP
schema/envelope/call-handler. It must never own process launching or run
persistence.

**Dependency direction**: root imports from both `execution` and
`validation`; `execution` and `validation` import from neither the root
package nor each other. Verified by reading every file's import list in
both packages — no file in `execution` imports `com.aqa.mcp.validation.*`
or bare `com.aqa.mcp`, and no file in `validation` imports
`com.aqa.mcp.execution.*` or bare `com.aqa.mcp`.

**What enforces that direction versus what merely holds true today**:
- The isolation invariant that matters most for security — no dependency
  on `regression-core` or any product module — is enforced by
  `ModuleBoundaryRules`' `McpServerIndependence` rule (MOD-003) plus
  `ReadOnlyProductionBoundaryTest`, which asserts by direct source-text
  scan that no file in the root or `validation` packages contains
  `ProcessBuilder`/`java.net`/`Files.write`/etc., and that only
  `DirectMavenProcessLauncher.java` inside `execution` contains
  `ProcessBuilder` at all. This is a real, currently-passing test.
- `ArchitectureRules`' `NoPackageCycles` rule (ARCH-002) is
  module-structure-agnostic and runs against every declared reactor
  module including `regression-mcp-server` itself —
  `ArchitectureToolTest.realReactorHasNoArch002PackageCycles` proves this
  by mapping all five reactor modules, `regression-mcp-server` included,
  to their `RuleProfile` and asserting zero cycles. A cycle between root
  and `execution` (or `execution` and `validation`) would already be
  caught by this passing test.
- ARCH-002 only detects **cycles**, not one-way coupling. If `execution`
  ever started importing from `validation` in one direction only (added
  coupling, not a cycle), nothing in the rule set would fire. As far as
  this module's tests go, `execution`/`validation` sibling independence
  today holds only because it happens to be true, not because any rule
  would catch a future one-way violation (`docs/TECHNICAL_DEBT.md` item
  C6).

## Class inventory

67 classes across three packages. Tier = dependency depth (tier 0
references no other module class; tier N references only classes of tier
< N). Fan-in = number of other module classes that reference it
(excluding the class's own file). Bucket: **S**chema-visible (a change can
alter client-facing JSON), **B**ehaviour-visible (cannot alter JSON shape
but can alter what a client observes), **I**nternal (neither).

### com.aqa.mcp (root) — 11 classes

| Class | Kind | Purpose | Tier | Fan-in | Bucket |
|---|---|---|---|---|---|
| `RegressionMcpServer` | entry point | Builds the server, registers all 14 tools, maps domain objects to JSON-RPC | 6 | 0 (composition root) | S |
| `ToolSchemas` | static utility | Builds the closed input/output JSON Schemas for all 14 tools; extracted from `RegressionMcpServer` | 1 | 1 | S |
| `RepositoryRootResolver` | static utility | Resolves/validates `REGRESSION_ROOT` | 1 | 1 | I |
| `RepositoryRoot` | record | Immutable resolved root path | 0 | 6 | I |
| `ModuleList` (+`ModuleDescriptor`) | record | Parses the root pom.xml's declared modules, classifies each | 2 | 3 | S |
| `ModuleType` | enum | 6 module classifications | 0 | 4 (3 real, 1 comment-only) | S |
| `ModuleTypeClassifier` | static utility | Maps a module directory name to a `ModuleType` | 1 | 1 | I |
| `RepositoryInspectionException` | exception | Structured error code for discovery-tool failures | 0 | 3 | B |
| `FeatureDiscovery` (+4 nested records) | static utility | Gherkin feature/scenario discovery via the official Cucumber parser | 3 | 1 | S |
| `FrameworkOverview` | record | Assembles the overview tool's output | 1 | 1 | S |
| `ExecutionPlanningFactory` | static utility | Composition seam: builds a `TestRunRequestValidator` from `ModuleList` | 3 | 1 | I |

### com.aqa.mcp.execution — 35 classes

| Class | Kind | Purpose | Tier | Fan-in | Bucket |
|---|---|---|---|---|---|
| `TestRunCoordinator` (+3 nested) | service | The one-run lifecycle coordinator: start/get/cancel, terminal persistence, stale-run recovery | 4 | 1 | B |
| `TestRunRequestValidator` | service | Validates a start request against a registered profile, timeout bounds, tag syntax | 2 | 2 | B |
| `ExecutionProfile` | record | One registered module's execution capability | 0 | 4 | B |
| `ExecutionProfileRegistry` | static registry | The fixed two-entry (`COMMERCE`, `JHIPSTER`) profile map | 1 | 1 | B |
| `ValidatedTestRunRequest` | value object | Post-validation request | 1 | 2 | B |
| `StartTestRunRequest` | record | Raw, unvalidated client input | 0 | 2 (1 comment-only) | B |
| `MavenRuntimeConfigurationLoader` | static utility | Loads `REGRESSION_MAVEN_HOME`/`java.home` from environment | 2 | 1 | I |
| `MavenRuntimeConfiguration` | value object | Validated, `toRealPath`-resolved Maven install paths | 1 | 3 | I |
| `MavenInvocationFactory` | static utility | Builds the exact Classworlds command line per run | 2 | 1 | B |
| `MavenInvocation` | record | Immutable java executable + working dir + argument list | 0 | 2 | B |
| `MavenProcessLauncher` | interface | Lifecycle seam for process launching | 1 | 2 | I |
| `DirectMavenProcessLauncher` | service | The one production launcher: `new ProcessBuilder(...).start()`, never a shell | 2 | 1 | I |
| `ProcessView` | interface | Seam over `ProcessHandle` | 2 | 3 | I |
| `SystemProcessView` | service | The one production `ProcessView`: JDK `ProcessHandle` only | 3 | 1 | I |
| `ObservedProcess` | record | One process-table observation with a provable start instant | 0 | 5 | I |
| `OwnedProcessIdentity` | record | An immutable, server-retained process identity | 1 | 4 | B |
| `ProcessOwnershipTracker` | service | Bounded (128-identity) retained ownership set; deepest-first cleanup | 3 | 1 | B |
| `RunStore` (+3 nested) | service | All on-disk persistence: `run.json`/`status.json`, atomic replace, active-run file lock | 2 | 2 | S/B (persistence internal; direct role in every schema-visible type's durability) |
| `RunId` | static utility | Generates/validates the `run-<32 hex>` id format | 0 | 2 | B |
| `RunSnapshot` | record | Immutable, client-safe run status | 1 | 3 | S |
| `TestRunState` | enum | 7 lifecycle states with a terminal flag | 0 | 2 | B |
| `CaptureMetadata` (+2 nested) | record | Persisted capture state, deliberately containing no absolute paths | 0 | 5 | I |
| `CaptureStatus` | enum | `PENDING/COMPLETE/PARTIAL/UNAVAILABLE` | 0 | 3 | I |
| `RunCaptureLayout` | record | Server-generated, run-bound staging/final/index paths, never serialized | 0 | 3 | I |
| `ReportCapture` (+3 nested) | service | Validates staged Surefire/Allure output, atomically publishes it | 3 | 1 | S |
| `SurefireSummaryParser` (+2 nested) | static utility | Hardened, XXE-resistant Surefire XML parser with bounded output | 1 | 1 | S |
| `AllureResultParser` (+1 nested) | static utility | Bounded, depth-limited Allure result JSON streaming parser | 1 | 1 | S |
| `SurefireSummary` (+5 nested) | record | The authoritative, immutable Surefire+Allure-enriched summary | 0 | 7 | S |
| `PublishedReportIndex` | record | On-disk index readers consume directly | 1 | 2 | I |
| `FailureArtifact` | record | Derived, read-time-computed view of one published capture file | 0 | 4 | S |
| `ArtifactContent` | record | One artifact's metadata plus raw bytes | 1 | 3 | S |
| `BoundedLogDrainer` | `Runnable` | Drains one process stream to a capped 16 MiB file, retains a 64 KiB tail | 0 | 1 | B |
| `CloseAwareInputStream` | `InputStream` decorator | Notifies the coordinator on EOF/read-failure of the server's own stdin | 0 | 1 | I |
| `ExecutionPlanningException` | exception | Structured `(code, message)` exception used throughout `execution` | 0 | 10 | B |
| `PublicDiagnosticSanitizer` | static utility | Bounds/sanitizes every string crossing the public MCP boundary | 0 | 2 | I (effect is S via `SurefireSummary.FailureRecord`) |

### com.aqa.mcp.validation — 21 classes

| Class | Kind | Purpose | Tier | Fan-in | Bucket |
|---|---|---|---|---|---|
| `ModuleBoundariesTool` | public `Tool` wiring | MCP wiring for `regression_validate_module_boundaries` | 5 | 1 | S |
| `FrameworkConventionsTool` | public `Tool` wiring | MCP wiring for `regression_validate_framework_conventions` | 5 | 2 (1 comment-only) | S |
| `ArchitectureTool` | public `Tool` wiring | MCP wiring for `regression_validate_architecture` | 5 | 2 (1 comment-only) | S |
| `ModuleBoundaryRules` (+4 nested rules) | static rule list | MOD-001..004 | 4 | 1 | B |
| `ArchitectureRules` (+4 nested rules) | static rule list | ARCH-001..004 | 4 | 1 | B |
| `FrameworkConventionRules` (+7 nested rules) | static rule list | FC-001, FC-001-PW, FC-002, FC-002-PW, FC-003, FC-004, FC-005 | 4 | 1 | B |
| `ValidationRule` | interface | `id()`/`profiles()`/`evaluate(EvaluationContext)` contract | 3 | 6 | B |
| `EvaluationContext` | record | One module's evaluation input | 2 | 7 | S |
| `ValidationScopeValidator` | static utility | Validates/resolves a scope request against declared modules | 2 | 3 | B |
| `ValidationReport` | record | The full multi-module report | 2 | 3 | S |
| `JavaSourceScanner` | static utility | Walks `src/main/java` + `src/test/java`, parses every `.java` file | 1 | 3 | B |
| `BasePackages` | static utility | Derives a module's base package as the longest common prefix | 1 | 3 confirmed (+1 unverified) | I |
| `RuleProfileResolver` | static utility | Maps a `ModuleType` name to a `RuleProfile` | 1 | 4 | B |
| `ModuleProfile` | record | One reactor module's `(module, RuleProfile, basePackage)` | 1 | 6 | S |
| `ModuleValidationResult` | record | One module's full result | 1 | 3 | S |
| `SourceUnit` | record | One parsed file: module, relative path, JavaParser `CompilationUnit` | 0 | 9 | I |
| `Violation` | record | One rule finding | 0 | 8 | S |
| `RuleProfile` | enum | `CORE,API,UI,API_UI,MCP,TEST_ONLY` | 0 | 12 | S |
| `ValidationException` | exception | Structured `(code, message)` exception for the validation package | 0 | 6 | B |
| `ValidationScopeRequest` | record | Raw `(module, profile)` filter input | 0 | 3 | I |
| `ValidatedValidationScope` | record | The resolved list of modules a request applies to | 0 | 3 | I |

**A class whose fan-in is empty**: only `RegressionMcpServer` has zero
in-module fan-in, and that is expected, not a finding — it is the
module's sole composition root (its callers are the JVM's `main()` and
the test suite, outside the production dependency graph by construction).
No other class in the module has empty fan-in.

## Flow walkthroughs

### (a) A discovery call — `regression_list_features`

1. The MCP SDK delivers the JSON-RPC request to the `callHandler` lambda
   built in `RegressionMcpServer.featureListTool` — a process/thread
   boundary (the MCP SDK's own reader thread).
2. `RegressionMcpServer.moduleArgument` validates the raw arguments
   in-process.
3. `RepositoryRootResolver.resolve` re-resolves and re-validates
   `REGRESSION_ROOT` **on every call**, never cached at startup — a
   filesystem boundary (`Files.exists`/`toRealPath`/`Files.isRegularFile`
   on the root pom.xml).
4. `FeatureDiscovery.discover` calls `ModuleList.forRoot` (a second,
   independent read and XML parse of the root pom.xml) to resolve the
   module's declared relative path, then resolves and `toRealPath`-validates
   the module's `src/test/resources/features` directory — another
   filesystem boundary.
5. `FeatureDiscovery` walks the feature tree (`Files.walk`, filesystem
   boundary), validating every path/symlink against the real root, sorts
   deterministically, enforces the 10,000-file/1 MiB bounds.
6. `FeatureDiscovery` reads each `.feature` file's bytes (filesystem
   boundary) and hands them to the official `GherkinParser` (in-process,
   no further I/O), which performs Pickle-based scenario expansion.
7. `RegressionMcpServer.featureOutput` maps the result to the closed JSON
   shape; `successResult`/`serialize` write the response through the MCP
   SDK's STDIO transport, the only writer to `System.out` — the process
   boundary back to the client.

### (b) The execution lifecycle — `regression_start_test_run` through a
later poll and report

1. `RegressionMcpServer.startTestRunTool`'s handler parses the request and
   calls `TestRunCoordinator.start`.
2. `TestRunCoordinator.start` checks `closed`/`recoveryBlocked`
   in-memory, then validates via a **fresh** `TestRunRequestValidator`
   built by `ExecutionPlanningFactory.validatorFor`, re-reading
   `ModuleList` on every start call (another filesystem read).
3. `MavenRuntimeConfigurationLoader.load` reads
   `REGRESSION_MAVEN_HOME`/`java.home` and `toRealPath`-verifies the
   Maven install shape — a filesystem boundary.
4. `RunStore.acquireActiveLock` takes the cross-process `active.lock`
   file lock (`FileChannel.tryLock`) — the only-one-run-at-a-time
   enforcement point, a filesystem-wide, cross-JVM lock.
5. `RunStore.create` writes `run.json`, `status.json`, `stdout.log`,
   `stderr.log`, and the capture staging directories under
   `.regression-mcp/runs/<runId>/`.
6. `start` submits `execute(next)` to a 3-thread worker `ExecutorService`
   and returns the `QUEUED` snapshot immediately — a **thread boundary**:
   the caller returns while `execute()` runs on a worker thread.
7. On the worker thread, `MavenInvocationFactory.create` builds a
   `MavenInvocation`; `DirectMavenProcessLauncher.launch` calls `new
   ProcessBuilder(...).start()` — **the process boundary**: a new OS
   process running Maven via the Classworlds launcher, never a shell.
8. `ProcessOwnershipTracker` is constructed from the launched process's
   observed identity (`SystemProcessView.find`, a read-only OS
   process-table read); `RunStore.update` persists that identity durably
   **before** the run is ever published `RUNNING` — deliberate ordering,
   so a crash between launch and this persist cannot leave an unrecorded
   orphan process.
9. Two `BoundedLogDrainer`s run on their own worker-pool threads, reading
   `process.getInputStream()`/`getErrorStream()` (pipe boundary) and
   writing to `stdout.log`/`stderr.log` (filesystem boundary), capped at
   16 MiB each.
10. A timeout is scheduled on a separate single-thread scheduler; once
    installed, the run publishes `RUNNING`, and a fixed-rate ownership
    observer re-observes the process tree and re-persists every 100ms.
11. `process.waitFor()` blocks the worker thread until Maven exits
    (process boundary). `cleanup()` walks and terminates any surviving
    descendant/root process.
12. `awaitDrainers` blocks up to 5 seconds per stream for the drainers to
    complete (thread-join boundary).
13. `capture()` calls `ReportCapture.capture`, which validates the staged
    Surefire directory the Maven process's own redirected Surefire plugin
    wrote to (filesystem boundary: `Files.walkFileTree`, hashing every
    file), parses the XML (`SurefireSummaryParser`, hardened DOM parser,
    in-process), optionally does the same for Allure, then **atomically
    moves** both from staging to their final location (`Files.move` with
    `ATOMIC_MOVE`) and writes an index JSON.
14. `persistTerminal` writes the final `RunSnapshot` (filesystem write),
    releases the `RunStore.Lock` (releases the cross-process file lock),
    and the worker thread's `execute()` call ends.
15. A later `regression_get_test_run` poll: `TestRunCoordinator.get`
    first checks the in-memory active reference (no I/O, while the run is
    still owned by this JVM) and falls back to `RunStore.get` (a
    `status.json` read) once the run is no longer active.
16. The four report/artifact tools each re-verify the run is terminal,
    then read straight from the already-published index/report files,
    re-verifying SHA-256 digests against the index on every read — no
    re-parsing of Surefire/Allure XML/JSON at this stage, only at capture
    time.

### (c) A validator call — `regression_validate_architecture`

1. `RegressionMcpServer` registers `ArchitectureTool.tool(...)` with a
   `Supplier<Map<String,String>>` that resolves `moduleTypeByName` fresh
   per call, itself re-reading `ModuleList.forRoot` — a third independent
   read/parse of the root pom.xml for this one call, alongside the one
   inside `ArchitectureTool.evaluate` below.
2. `ArchitectureTool.evaluate` parses the scope request, calls
   `ValidationScopeValidator.validate`, then for **every declared
   module** (not just the scoped one) calls `JavaSourceScanner.scan` —
   the expensive step: walks both `src/main/java` and `src/test/java`
   for that module (filesystem boundary) and parses every `.java` file
   with a freshly constructed `JavaParser` (in-process CPU work, one
   parser instance per `scan()` call). This happens identically for all
   three validator tools regardless of request scope, with no cache
   across calls — see `docs/TECHNICAL_DEBT.md` item B10.
3. `BasePackages.derive` computes each module's base package from its own
   just-parsed sources (in-memory).
4. For each module actually in scope, `ArchitectureRules.all()`'s four
   rules each walk the already-parsed `CompilationUnit` ASTs in memory
   (no further I/O) and return `Violation`s.
5. `ArchitectureTool.moduleResultOutput` partitions ARCH-004's violations
   into `advisoryViolations`; `successResult`/`serialize` write the
   response — the same process boundary as walkthrough (a)'s final step.

## Lifecycle and ownership

**Per-JVM (server process)**: one `TestRunCoordinator` (created once in
`RegressionMcpServer.createServer`, closed via a JVM shutdown hook
registered in the same method); its three executors (a 3-thread worker
pool, a 1-thread ownership observer, and the timeout scheduler's own
1-thread executor — 5 background threads for the coordinator's lifetime);
the `CloseAwareInputStream` wrapping `System.in`. `TestRunCoordinator`'s
constructor also runs `recoverIfUnowned()` synchronously — the first
thing the coordinator does is take the active-run lock and reconcile any
run left `RUNNING` by a previous, uncleanly-shut-down server process.

**Per-run**: one `TestRunCoordinator.Active` instance (mutable snapshot,
request/runtime/lock, an atomic `cause` reference, volatile
process/timeout/observation/tracker fields); one `RunStore.Lock` (wraps
the cross-process `active.lock` `FileChannel` lock — held from `start()`
until `execute()`'s `finally` block, covering the run's entire lifetime);
one `ProcessOwnershipTracker` (up to 128 retained identities); two
`BoundedLogDrainer`s; one scheduled timeout future; one 100ms-period
observation future. On disk: `.regression-mcp/runs/<runId>/` containing
`run.json` (write-once, immutable), `status.json` (atomically replaced on
every transition), `stdout.log`/`stderr.log` (append-only, capped), and
`staging/`/`reports/`/`artifacts/` subdirectories.

**Per-process (the spawned Maven JVM)**: owned entirely by the OS between
`DirectMavenProcessLauncher.launch` and `TestRunCoordinator.cleanup`'s
termination call; the module's own process-table bookkeeping
(`ObservedProcess`/`OwnedProcessIdentity`) is a read-only shadow of that
OS-level lifetime — every termination decision re-confirms
`identity.sameProcess(observed)` against a fresh OS read before acting,
specifically to avoid killing a PID reused by an unrelated process.

**Per-request**: every read-only tool re-resolves `REGRESSION_ROOT` and,
for discovery/validation tools, re-reads `ModuleList` — nothing is cached
across requests except what `TestRunCoordinator` itself holds for an
active run. Deliberate: so a malformed root pom.xml fails only the
individual call that needed it, never server startup.

**What survives a server restart**: everything under
`.regression-mcp/runs/` on disk. A new `TestRunCoordinator`'s constructor
calls `recoverIfUnowned()`, which reads every still-`active()`
(non-terminal) persisted run and either proves its owned processes are
gone (transitions it to `ERROR` with reason `SERVER_RESTART_RECOVERY`) or,
if it cannot prove that, sets `recoveryBlocked` and refuses all new
`start()` calls until resolved. What does not survive: the in-memory
`Active` object, the three executors (recreated fresh), and any process
the previous JVM had not already durably recorded an `OwnedProcessIdentity`
for.

## Data model

**`SurefireSummary`** is the authoritative, immutable aggregate; its
compact constructor enforces `passed == tests - failures - errors -
skipped` and `failureRecords.size() == failures + errors` as real,
enforced invariants. `allureAvailability`/`detailsTruncated` are the two
fields that mean different things depending on which tool reads them
(`docs/TECHNICAL_DEBT.md` item A1: `regression_get_test_summary` ORs in
an extra condition that `regression_get_failure_summary` does not). No
other same-field-different-meaning instance was found among fields read
by both tools.

**`CaptureMetadata`/`ReportCapture.CaptureOutcome`**: `CaptureMetadata` is
persisted, server-internal bookkeeping, deliberately containing no
absolute paths. `CaptureOutcome` is a separate, non-persisted record
pairing the metadata with the freshly-parsed skipped-test count, so the
caller never re-reads the published report to learn it.

**`RunSnapshot`** is the one client-facing run-status shape, reused
verbatim by start/get/cancel. Nullable fields and why: `startedAt`/
`finishedAt` (null until that phase), `exitCode` (null unless the process
actually exited — a cancelled-before-launch run's `exitCode` stays null
forever, correctly), `reason` (null only for the just-created `QUEUED`
snapshot), `skippedTests` (null until a Surefire report has been parsed
for this run). All five are persisted verbatim and read back verbatim at
response time with no further derivation — unusual among this module's
types in having nothing derived from it at response time.

**`FailureArtifact`/`ArtifactContent`** are explicitly documented in their
own class comments as derived, read-time-computed, never themselves
persisted — confirmed: `RunStore.toArtifact` recomputes them fresh,
including `artifactId` as a deterministic truncated SHA-256 of
`runId:relativePath`, on every call.

**`ValidationReport`/`Violation`/`ModuleValidationResult`** are never
persisted — every validator call recomputes its full report from scratch.
`ModuleValidationResult.truncated` is notable: all three Tool classes
construct it with a **hardcoded literal `false`** (see
`docs/TECHNICAL_DEBT.md` item D12) — the field exists in the schema and is
always emitted, but nothing in the current code ever produces `true`.

## Boundary and trust surface

| Boundary | Where | Validates/bounds | On malformed input |
|---|---|---|---|
| Root pom.xml XML | `ModuleList.readDeclaredModules` | Secure `DocumentBuilderFactory` (DOCTYPE disallowed, external entities disabled); module paths must be relative and resolve inside root | `POM_ERROR`, server keeps running |
| Product-module pom.xml (execution only) | `MavenInvocationFactory.trustedRepositoryRoot` | `Files.isDirectory`/`isRegularFile(pom.xml)`, `toRealPath` | `MAVEN_RUNTIME_UNAVAILABLE` |
| Gherkin `.feature` files | `FeatureDiscovery` | 1 MiB/file, 10,000 files, symlink+containment check, UTF-8 | `FEATURE_FILE_TOO_LARGE`/`FEATURE_FILE_LIMIT_EXCEEDED`/`FEATURE_PATH_VIOLATION`/`GHERKIN_PARSE_ERROR` |
| Java source files | `JavaSourceScanner` | 1 MiB/file, 10,000 files, symlink+containment check, JavaParser at a fixed language level | `SOURCE_FILE_TOO_LARGE`/`SOURCE_FILE_LIMIT_EXCEEDED`/`SOURCE_PATH_VIOLATION`/`SOURCE_PARSE_ERROR` |
| Surefire XML (capture time) | `SurefireSummaryParser`, via `ReportCapture` | Hardened DOM parser (DOCTYPE/external-entity disabled); bounded suite/testcase/failure-record/message/stack counts; every numeric attribute bounds-checked | `MalformedReportException` -> `CaptureStatus.UNAVAILABLE` status `MALFORMED`, never a partial summary |
| Allure result JSON (capture time, optional) | `AllureResultParser` | Jackson streaming parser with `StreamReadConstraints`; bounded result-file/step count and depth; every string sanitized | Any failure downgrades to `allureAvailability: "REJECTED"` — never blocks the authoritative Surefire summary |
| Staged capture directories | `ReportCapture.validate` | `Files.walkFileTree` with depth/count/size/running-total checks, symlink rejection, real-path containment | Capture attempt marked `UNAVAILABLE`, staging cleaned up |
| Published artifact bytes | `RunStore.readArtifact` | Re-verifies containment, symlink absence, exact size and SHA-256 digest at **read** time, not just publish time; MIME allow-list re-checked at read time | `REPORT_INDEX_CORRUPT`, `UNSUPPORTED_MIME_TYPE`, or `NOT_FOUND` |
| Run store files (write) | `RunStore.replace`/`writeNew`/`create` | Symlink re-check immediately before every write; atomic same-directory temp-file-then-move; `writeNew` refuses to overwrite `run.json` | `IOException` -> `MAVEN_RUNTIME_UNAVAILABLE` |
| Capture publish (write) | `ReportCapture.publish` | Requires staging/target share the same `FileStore` (so `Files.move` is genuinely atomic); refuses to publish over an existing target; re-verifies every file's size+hash post-move | Publish failure cleans up the target, capture marked `UNAVAILABLE` |
| Process launch | `DirectMavenProcessLauncher.launch` | Only ever invoked with a `MavenInvocation` built from server-owned, `toRealPath`-verified paths and a `ValidatedTestRunRequest`. `request.environment()` is the one exception: it is raw client input constrained only by list membership in `profile.environments()`, then reaches the command line unescaped (`docs/TECHNICAL_DEBT.md` item D13) — inert today only because both registered profiles declare exactly `"dev"` | `IOException` -> `MAVEN_LAUNCH_FAILED` |
| Environment variables | `REGRESSION_ROOT`, `REGRESSION_MAVEN_HOME`/`java.home`/`os.name` | Both go through `toRealPath`/existence/shape checks before use; never echoed back except through `PublicDiagnosticSanitizer.redactSecrets`, which additionally redacts any env-var value matching a token/secret/password/credential/`*_key` pattern | Startup failure or `MAVEN_RUNTIME_UNAVAILABLE` |
| MCP request payloads | every tool's `callHandler` | Closed JSON schemas (`additionalProperties:false`) enforced by the MCP SDK before the handler runs, plus a second, redundant manual argument-shape check inside most handlers | `INVALID_ARGUMENTS` from the manual layer, or schema-level rejection (SDK behavior, not this module's source) |

## Extension points

**Registering a third execution profile**: add one `ExecutionProfile`
entry and one map entry in `ExecutionProfileRegistry` — the sole
authority; no change needed in `TestRunRequestValidator`,
`MavenInvocationFactory`, `TestRunCoordinator`, or `RegressionMcpServer`,
all already generic over "whatever profile the registry returns." The
target module's own pom.xml must wire `mcp.surefire.reportsDirectory`/
`mcp.allure.resultsDirectory` (confirmed by `regression-jhipster`'s and
`regression-nextjs-commerce`'s existing POMs), and `supportsHeadless`/tags
semantics must make sense for that module's test technology —
`TestRunRequestValidator.validateHeadless` has only a binary
reject-if-false behavior, and `MavenInvocationFactory` unconditionally
appends `-Dcucumber.filter.tags=...` regardless of whether the target
module's runner reads that property.

**Adding a fifteenth tool**: add one more factory-method call to
`RegressionMcpServer.createServer`'s tool list, following either the
inline `SyncToolSpecification.builder()` pattern the 11
`RegressionMcpServer`-native tools use, or the fully-self-contained
`Tool.tool(...)` static-factory pattern the three validation tools use
from a separate package. The envelope/wrapper boilerplate — the `oneOf`
success/error JSON-Schema shape, the runId-only and no-args input shapes,
result/error wrapping and serialization, and the two annotation builders
— is already factored into shared, single-source-of-truth helper methods
reused by 2 to 10 of the existing 11 tools; a well-modeled new tool
reuses these directly rather than duplicating them. A simple new
runId-scoped tool costs on the order of 15-25 new lines of genuinely
tool-specific code (comparable to `regression_get_test_summary`'s own
footprint); only a tool as structurally complex as
`regression_get_failure_summary` (nested recursive schema, a dedicated
bounded-response wrapper) approaches 50.

**Adding a new validation rule**: add one private nested class
implementing `ValidationRule` to the appropriate fixed rule-list file and
add it to that file's `all()` list. No registration elsewhere is needed —
the owning `Tool` class's `evaluate` already iterates whatever `all()`
returns, filtered by `rule.profiles().contains(profile)`. If the rule is
advisory rather than blocking, the owning `Tool` class's
`moduleResultOutput` needs one more `if (SOME_RULE_ID.equals(...))`
branch — copied by hand into that one `Tool` class only, since nothing
shares this partitioning logic across the three Tool classes today.

## Review order

Leaves first, hubs last. Hubs (fan-in > 5) sit mostly at low tiers —
`RuleProfile` (fan-in 12), `ExecutionPlanningException` (10), `SourceUnit`
(9), `Violation` (8), `EvaluationContext`/`SurefireSummary` (7 each),
`ValidationRule`/`ModuleProfile`/`ValidationException` (6 each) — meaning
tier alone would place them early. Read them early (most are tiny), but
treat any *change* to their public surface as the last thing reviewed in
their neighborhood, since it ripples through every caller at once.

**Group 1 — tier 0 (24 classes)**, any order within the group:
`RepositoryRoot`, `ModuleType`, `RepositoryInspectionException`,
`FailureArtifact`, `StartTestRunRequest`, `CaptureStatus`,
`ObservedProcess`, `ExecutionPlanningException`* (hub), `CloseAwareInputStream`,
`MavenInvocation`, `RunId`, `TestRunState`, `ExecutionProfile`,
`CaptureMetadata`, `RunCaptureLayout`, `PublicDiagnosticSanitizer`
(security-critical despite tier 0 — change never without a negative-case
test), `SurefireSummary`* (hub), `BoundedLogDrainer`, `RuleProfile`* (hub,
busiest type in the module), `SourceUnit`* (hub), `Violation`* (hub),
`ValidationException`* (hub), `ValidationScopeRequest`,
`ValidatedValidationScope`.

**Group 2 — tier 1 (19 classes)**: `RepositoryRootResolver` (pairs with
`RepositoryRoot`), `ModuleTypeClassifier`, `FrameworkOverview`,
`ToolSchemas` (root-package, schema authoring extracted from
`RegressionMcpServer`; dossier `docs/classes/ToolSchemas.md` already
written), `ArtifactContent` (pairs with `FailureArtifact`), `PublishedReportIndex`,
`OwnedProcessIdentity` (pairs with `ObservedProcess` — neither
comprehensible alone), `MavenRuntimeConfiguration`, `SurefireSummaryParser`,
`AllureResultParser`, `ExecutionProfileRegistry`, `ValidatedTestRunRequest`,
`RunSnapshot` (the module's single most schema-central type),
`ModuleProfile`* (hub), `ModuleValidationResult`, `RuleProfileResolver`,
`BasePackages`, `JavaSourceScanner`, `MavenProcessLauncher` (review
bundled with `DirectMavenProcessLauncher`, tier 2, below — interface plus
its sole implementation, the same treatment as `ProcessView`/
`SystemProcessView`).

**Group 3 — tier 2 (10 classes)**: `ModuleList` (pairs with
`RepositoryRoot`/`RepositoryRootResolver`/`ModuleType`/`ModuleTypeClassifier`
as one root-package discovery-chain unit), `MavenInvocationFactory`
(review with the open `request.environment()` finding, item D13, in
hand), `MavenRuntimeConfigurationLoader`, `TestRunRequestValidator`
(pairs with `ValidatedTestRunRequest`/`ExecutionProfileRegistry` — the
validation-order contract is only comprehensible reading all three
together), `RunStore` (hub-adjacent by consequence despite low raw
fan-in — every schema-visible type's durability runs through it),
`EvaluationContext`* (hub), `ValidationReport`, `ValidationScopeValidator`,
`DirectMavenProcessLauncher` (bundled with `MavenProcessLauncher`,
above), `ProcessView` (pairs with `SystemProcessView`, below).

**Group 4 — tier 3 (6 classes)**: `SystemProcessView` (bundled with
`ProcessView`), `ProcessOwnershipTracker` (review with `ObservedProcess`/
`OwnedProcessIdentity`/`ProcessView`/`SystemProcessView` already fresh),
`ReportCapture` (pairs with `SurefireSummaryParser`/`AllureResultParser`;
also the class holding the one-way `RunStore.AtomicMover` coupling noted
above), `FeatureDiscovery`, `ExecutionPlanningFactory`, `ValidationRule`*
(hub — trivial interface, but the center of all ~15 rule
implementations; review it here, defer any change until every
implementer has its own dossier).

**Group 5 — tier 4, the rule-set files (3 units, each bundling its own
nested rules)**: `ModuleBoundaryRules`, `ArchitectureRules` (carries the
ARCH-001 accepted gap and the ARCH-002 cycle-detection algorithm — the
most algorithmically interesting file in `validation`), `FrameworkConventionRules`
(the largest, 7 nested rules).

**Group 6 — tier 5, the three Tool classes (1 unit — their shared shape
is only comprehensible together)**: `ModuleBoundariesTool`,
`FrameworkConventionsTool`, `ArchitectureTool`.

**Group 7 — tier 4, execution's own hub, reviewed last in its package**:
`TestRunCoordinator` — the module's largest, most complex class. Its
dossier (`docs/classes/TestRunCoordinator.md`) was written ahead of order
and reached a TEST FIRST verdict; all four of its `execute()` terminal
paths now have characterization tests, with the `skippedTests`
preservation guard (`docs/TECHNICAL_DEBT.md` item B11) the remaining test
gap. Dossiers for the classes it depends on are still the prerequisite for
acting on that verdict.

**Group 8 — tier 6, reviewed absolutely last**: `RegressionMcpServer` —
depends on everything above it; the only dossier for which every other
dossier is a genuine prerequisite, not merely helpful context.
