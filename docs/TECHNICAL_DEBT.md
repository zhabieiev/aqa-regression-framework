# Technical Debt

This is a living reference of known, accepted debt in the repository. Each
item below was deliberately left in place rather than fixed, because fixing
it was out of scope for the work that surfaced it. None of these are
blockers for any shipped functionality. This file has no stage/gate numbers
and no dated narrative — when an item is fixed, delete it from this file
rather than marking it done.

## 1. Package-dependency cycle in `regression-nextjs-commerce`

**What**: `com.aqa.nextjscommerce.config.UiSettings` imports
`com.aqa.nextjscommerce.driver.BrowserType`, while `driver`'s
`ChromeOptionsFactory`, `DriverSession`, and `DriverFactory` all import
`config.UiSettings` back — a two-package import cycle.

**Location**:
- `regression-nextjs-commerce/src/test/java/com/aqa/nextjscommerce/config/UiSettings.java`
- `regression-nextjs-commerce/src/test/java/com/aqa/nextjscommerce/driver/BrowserType.java`
- `regression-nextjs-commerce/src/test/java/com/aqa/nextjscommerce/driver/ChromeOptionsFactory.java`
- `regression-nextjs-commerce/src/test/java/com/aqa/nextjscommerce/driver/DriverSession.java`
- `regression-nextjs-commerce/src/test/java/com/aqa/nextjscommerce/driver/DriverFactory.java`

**Why accepted**: `regression_validate_architecture`'s ARCH-002 rule
(package-dependency cycles) found this pre-existing cycle when its
real-reactor test was first written. Untangling `config` and `driver`
requires a `regression-nextjs-commerce`-only refactor that was explicitly
out of scope for `regression-mcp-server` validator work. `ArchitectureTool`
does not special-case or suppress it: `ArchitectureToolTest`'s real-reactor
ARCH-002 test asserts that *exactly* this one cycle exists (both
participating files named explicitly) and fails the build the moment any
*different* cycle appears anywhere in the reactor, so this item stays
visible rather than silently tolerated.

**Status**: present, unfixed, guarded by a real-reactor regression test.

## 2. ARCH-001 layering rule is single-hop only (no Symbol Solver)

**What**: `regression_validate_architecture`'s ARCH-001 rule (a
`definitions`-package class must not reach past `steps` into
`pages`/`services`/`components`) flags a `MethodCallExpr` only when its
immediate scope is a field of the `definitions` class whose *declared*
type resolves — via `ImportDeclaration`, not a Symbol Solver — to a
`pages`/`services`/`components` package, or is a Selenium `By`/Playwright
`Locator`/`Page` type directly. Two-hop calls that reach through an
intermediate object, e.g. `regression-core`'s
`S3Definitions.s3Steps.s3ServiceActions().getObject(...)`, are not caught.

**Location**: `regression-mcp-server/src/main/java/com/aqa/mcp/validation/ArchitectureRules.java`
(ARCH-001 rule implementation, see the class javadoc and the ARCH-001
method's own doc comment).

**Why accepted**: JavaParser's Symbol Solver was deliberately not added
speculatively; the project's standing rule is that a Symbol Solver is
added only once a specific rule demonstrates a genuine need for full type
resolution. Single-hop, declared-type-only detection was accepted as
adequate first-cut coverage.

**Status**: present, unfixed. Adding full two-hop (and deeper) detection
would require introducing a Symbol Solver into `ArchitectureRules`, which
has not been authorized.

## 3. Structural duplication across the three validator `Tool` classes

**What**: `ModuleBoundariesTool`, `FrameworkConventionsTool`, and
`ArchitectureTool` each independently implement the same set of methods —
`evaluate`, `parseRequest`, `reportOutput`, `moduleResultOutput`,
`violationOutput`, `inputSchema`, `violationSchema`, `moduleResultSchema`,
`outputSchema`, `readOnlyAnnotations`, `successResult`, `errorResult`, and
`serialize` — with near-identical bodies. Each file is roughly 180-195
lines, of which an estimated 120-140 lines per file are duplicated
schema/envelope/evaluation-loop boilerplate rather than logic specific to
that tool's rule set.

**Location**:
- `regression-mcp-server/src/main/java/com/aqa/mcp/validation/ModuleBoundariesTool.java`
- `regression-mcp-server/src/main/java/com/aqa/mcp/validation/FrameworkConventionsTool.java`
- `regression-mcp-server/src/main/java/com/aqa/mcp/validation/ArchitectureTool.java`

**Why accepted**: each tool shipped independently across separate gates of
work, and consolidating the shared shape into a common helper was judged a
separate, purely-internal refactor with no effect on any tool's external
schema or behavior — not worth bundling into the gate that shipped the
third tool. See `docs/ROADMAP.md` for a concrete extraction proposal.

**Status**: present, unfixed. Does not affect any tool's external
behavior or schema; internal-only debt.

## 4. Direct AssertJ assertions inside `GeneralDefinitions`'s `@Then` steps

**What**: `regression-core`'s `GeneralDefinitions` record makes direct
`assertThat(...)` (AssertJ) calls inside its own `@Then`-annotated Cucumber
step methods (e.g. `varIsEqualToString`, `varIsEqualToObject`,
`varListContainsItem`), rather than delegating assertions to a dedicated
step/assertion layer.

**Location**: `regression-core/src/main/java/com/aqa/core/definitions/GeneralDefinitions.java`

**Why accepted**: `GeneralDefinitions` sits at the `definitions` layer,
where CLAUDE.md's stated architecture keeps definitions thin (bind
Gherkin input, delegate) rather than asserting directly. No
`regression_validate_architecture` rule (ARCH-001..004) currently covers
this shape — it was noted as informational during the Architecture
Validator's design work but never turned into a scoped rule, since a
`definitions`-layer assertion rule needs its own design pass distinct from
ARCH-001..004's pages/components-focused scope.

**Status**: present, unfixed, not covered by any current validator rule.

## 5. Unreproduced `@ui` hang in `regression-jhipster` (2026-08-21)

**What**: on 2026-08-21 a `regression-jhipster` `@ui`-tagged Cucumber run
hung for over five minutes with zero output and no exception, then passed
on an identical immediate retry. Root cause unidentified — no exception,
no partial output, and no known trigger to reproduce on demand.

**Location**: `regression-jhipster/src/test/java/com/aqa/jhipster/runners/RunCucumberTest.java`
(the `@ui`-tagged suite); mitigated reactor-wide by
`forkedProcessTimeoutInSeconds` in the root `pom.xml`'s Surefire
`pluginManagement` configuration.

**Why accepted**: a bounded reproduction probe was run the same day — five
consecutive `mvn -pl regression-jhipster -am test -Dcucumber.filter.tags="@ui" -Denv=dev`
runs against the live app (confirmed reachable via `curl`, HTTP 200,
immediately before the probe). All five passed cleanly with no hang: 27s,
23s, 31s, 25s, and 26s wall clock, each producing 5 Scenarios (5 passed),
24 Steps (24 passed), BUILD SUCCESS. An intermittent failure that does not
reproduce on demand does not justify open-ended investigation beyond a
bounded probe. The reactor-wide Surefire `forkedProcessTimeoutInSeconds`
of 900 (root `pom.xml`) converts any future hang into a diagnosable
failure after 15 minutes rather than an open-ended stall, which was judged
sufficient mitigation for now.

**Status**: present, unreproduced, mitigated by the 900-second Surefire
fork timeout. If it recurs: before killing anything, capture Surefire's
timeout output (the forked-process-timeout error and any partial log) and
a thread dump of the forked JVM (e.g. `jstack` against the forked
Surefire process, found via its process tree) — that diagnostic evidence
is exactly what this item currently lacks.

## 6. Playwright traces are not captured by ReportCapture (2026-08-22)

**What**: this is a `regression-jhipster`-only characteristic —
`regression-nextjs-commerce` uses Selenium WebDriver, not Playwright, and
produces no traces at all. In `regression-jhipster`, `UiHooks` starts
Playwright tracing for every `@ui` scenario when enabled
(`UiHooks.java:93-97`, `startTracing()`), gated by the `UI_TRACE`
configuration property (`UiHooks.java:167-178`, `isTraceEnabled()`), but
only writes the trace `.zip` to disk when the scenario actually fails
(`UiHooks.java:99-110`, `stopTracing()`: a passing scenario's trace is
discarded via the no-path `tracing().stop()` overload). The file is written
via a direct filesystem call to `target/playwright/traces/`
(`UiHooks.java:27`, `TRACE_DIRECTORY`), entirely outside
`regression-mcp-server`'s capture pipeline.

Two independent barriers block serving such a trace through the MCP server,
even for a `regression-jhipster` run: (1) `ReportCapture.capture()` only
ever reads two staging roots, `layout.surefireStaging()`
(`ReportCapture.java:48`) and `layout.allureStaging()`
(`ReportCapture.java:58`) — `RunCaptureLayout` (`RunCaptureLayout.java:7-8`)
has no third field for a Playwright root, and `RunStore.layout(...)`
(`RunStore.java:322-332`) never constructs one, so a trace is never staged
or published in the first place; and (2) even if a trace file were
published as an artifact, `.zip` is not on `RunStore`'s MIME allow-list
(`RunStore.java:33-34`, `ALLOWED_ARTIFACT_MIME_TYPES`, enforced at
`RunStore.java:248-250`) — a `.zip` resolves to `application/octet-stream`
via `mimeTypeOf` (`RunStore.java:301-316`), which is not an allowed type —
and the artifact listing itself is built solely from the published Allure
result set (`RunStore.java:287-291`, `toArtifact`), so a trace file has no
`artifactId` to be requested by regardless of MIME type.

**Location**:
- `regression-jhipster/src/main/java/com/aqa/jhipster/ui/hooks/UiHooks.java`
  (lines 27, 93-97, 99-110, 167-178)
- `regression-mcp-server/src/main/java/com/aqa/mcp/execution/ReportCapture.java`
  (lines 48, 58)
- `regression-mcp-server/src/main/java/com/aqa/mcp/execution/RunCaptureLayout.java`
  (lines 7-8)
- `regression-mcp-server/src/main/java/com/aqa/mcp/execution/RunStore.java`
  (lines 33-34, 248-250, 287-291, 301-316, 322-332)

**Why accepted**: this was inspected as part of the 2026-08-22 MCP demo
session work. Extending `ReportCapture` with a third staging root (mirroring
the existing Surefire-required/Allure-optional pattern) was considered and
rejected as the most invasive of the available fixes, for a characteristic
with no observed impact: no trace files exist on disk from any recent run,
`regression-nextjs-commerce` (the module the current demo work targets)
never produces one at all, and a `regression-jhipster` fix would also
require deliberately relaxing the artifact MIME allow-list to admit `.zip`
— a security-relevant change to a hardened, closed allow-list that needs
its own explicit review, not a side effect of a UI-hooks change. Decision:
document only, for now.

**Status**: present, unfixed, documented only. No `ReportCapture`,
`RunStore`, or `UiHooks` change is scheduled.

## 7. The run snapshot carries no progress signal while RUNNING (2026-08-22)

**What**: `regression_get_test_run`'s `stdoutBytes`/`stderrBytes` fields are
hardcoded to `0` the moment a run transitions to `RUNNING`
(`TestRunCoordinator.java:148`,
`replace(run.snapshot, TestRunState.RUNNING, Instant.now(), null, null, 0, 0, false, false)`),
and the periodic background update that keeps a `RUNNING` run's persisted
record alive every 100ms (`TestRunCoordinator.java:281-294`, `observe()`)
re-persists that same unchanged snapshot rather than recomputing byte counts
from the live `BoundedLogDrainer`s — real totals are computed only once, at
terminal persistence (`TestRunCoordinator.java:226-240`, `persistTerminal()`:
`long stdoutBytes = stdout == null ? 0 : stdout.bytes();`). A client polling
`regression_get_test_run` while a run is `RUNNING` therefore has no way to
tell, from these two fields, whether the run is progressing at all — this
was observed empirically in the 2026-08-22 session recording
(`regression-mcp-server/docs/SESSION_DEMO.md`): all 7 `RUNNING` polls
reported `stdoutBytes`/`stderrBytes` of `0`, with real values appearing only
on the 8th, terminal poll.

As a secondary, related observation from the same recording: `reason`
duplicated `state` at every observation in that session (`RUNNING`/`RUNNING`
on every non-terminal poll, `PASSED`/`PASSED` at the terminal poll) — both
values trace back to the same `replace(...)` helper
(`TestRunCoordinator.java:345-350`) passing `state.name()` as the `reason`
argument unconditionally.

**Location**: `regression-mcp-server/src/main/java/com/aqa/mcp/execution/TestRunCoordinator.java`
(lines 148, 226-240, 281-294, 345-350)

**Why accepted**: both characteristics were observed empirically during the
2026-08-22 MCP demo session recording rather than sought out, and neither
affects correctness — every field is internally consistent (byte counts are
real and correct at the moment they're finally populated; `reason` is never
wrong, just redundant with `state`). Adding genuine live progress reporting
would require recomputing byte counts from the live drainers inside
`observe()` instead of re-persisting the unchanged snapshot, which is a
behavior change to the execution lifecycle, not a documentation fix; it is
not currently scheduled.

**Status**: present, unfixed. Not currently scheduled for a fix.

## 8. `regression_get_test_summary`'s `detailsTruncated` is true for almost every run (2026-08-22)

**What**: `regression_get_test_summary`'s `detailsTruncated` field is
computed as (`RegressionMcpServer.java:327`):
```
boolean detailsTruncated = summary.detailsTruncated() || summary.suites().stream().anyMatch(suite -> !suite.testcases().isEmpty());
```
The second operand — "any suite has at least one parsed testcase" — is true
for essentially any real Surefire report with at least one test, regardless
of whether anything was actually truncated. This makes the field
misinformative by name: a client reading `detailsTruncated: true` off this
tool has no way to tell, from the field alone, whether it means "your data
was cut off" or merely "this run had test cases" (which is true of any
run with at least one test).

The same-named field on `regression_get_failure_summary` means something
different: it uses `summary.detailsTruncated()` directly, with no such OR
(`RegressionMcpServer.java:335`, inside `failureSummaryOutput`):
```
"detailsTruncated", summary.detailsTruncated());
```
This was observed directly in the 2026-08-22 session recording
(`regression-mcp-server/docs/SESSION_DEMO.md`): on the identical run,
`regression_get_test_summary` reported `"detailsTruncated":true` while
`regression_get_failure_summary` reported `"detailsTruncated":false`.

What `summary.detailsTruncated()` itself actually tracks — file
`regression-mcp-server/src/main/java/com/aqa/mcp/execution/
SurefireSummaryParser.java`: two count bounds, `MAX_SUITES = 100` and
`MAX_TESTCASES = 500` (lines 27-28), checked at line 71
(`boolean bounded = ordered.size() > MAX_SUITES || cases.size() > MAX_TESTCASES;`),
and one size bound on failure detail text, `MAX_FAILURE_DETAIL_BYTES = 28 *
1024` (line 32), whose overflow sets a `truncated` flag at line 116
(`if (recordTruncated[0]) summaryTruncated[0] = true;`) inside `record(...)`
(lines 105-119); the two are combined at line 75:
```
records, "UNAVAILABLE", bounded || truncated[0]);
```
So `summary.detailsTruncated()` reflects a genuine, bounded truncation event
(too many suites, too many testcases, or a failure message/stack trace cut
by the byte bound) — it is `regression_get_test_summary`'s own additional
OR condition (the testcase-emptiness check) that turns the field into
something else entirely for that one tool.

**Location**:
- `regression-mcp-server/src/main/java/com/aqa/mcp/RegressionMcpServer.java`
  (lines 327, 335)
- `regression-mcp-server/src/main/java/com/aqa/mcp/execution/SurefireSummaryParser.java`
  (lines 27-28, 32, 40, 71, 75, 105-119, 116)

**Why accepted**: observed empirically in the 2026-08-22 MCP demo session
recording. This is not a response-shape quirk to merely document — it is a
published output field whose name misleads a reader about what happened.
No fix is scheduled: changing what `detailsTruncated` means or is computed
from on `regression_get_test_summary` is a change to a published output
field's meaning, not a quiet edit, and needs its own explicit decision
before any change is made.

**Status**: present, unfixed, no fix scheduled.
