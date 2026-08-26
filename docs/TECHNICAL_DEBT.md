# Technical Debt

This file tracks known, accepted debt and open questions at the
cross-module and repository level, including `regression-mcp-server`.
Per-module limitations for individual product modules are not duplicated
here — see "Where module-level debt lives" at the end of this file.

Items are grouped into four sections, by what kind of thing they are and
what action they call for:

- **A. Defects** — published behaviour returns a wrong or misleading
  answer. Action: fix, or explicitly accept with a stated reason why the
  wrong answer is tolerable. A repository can ship with open items in this
  section, but only when that reason is stated.
- **B. Debt** — the fix is understood and was deferred, usually because it
  was out of scope for the work that surfaced it. Action: schedule.
- **C. Accepted characteristics** — a decision was made and nothing is
  scheduled to change. Action: none, but every item in this section states
  a concrete review trigger — the event that would reopen it.
- **D. Open questions and unproven assumptions** — these are closed by
  observation or verification when the triggering event occurs, not by
  proactive work. Each item states what to capture when that happens.

Each item's identifier is its section letter plus a number (A1, A2, B1,
B2, ...), assigned in the order items appear in this file. New items are
appended within their section; existing identifiers are never reused, even
after the item they named is deleted. Because a deleted identifier is
never reused, anything outside this file that cites an item by identifier
must be made self-contained when that item is fixed, rather than left
pointing at a gap. When an item is fixed, it is deleted
from this file rather than marked done — this file has no stage/gate
numbers and no dated narrative of past fixes. Because new items are
appended within their section and identifiers are never reused, position
within a section carries no meaning — priority is read from the Cost field
and the section's action verb, not from the order items appear in.

**Citation rule**: every item identifies its location by file path plus
the name of the class, method, field, property, or workflow step involved.
Line numbers are given only as a secondary aid, and are marked "as of
2026-08-23" wherever given, because they rot: as of 2026-08-23,
`docs/ROADMAP.md` cited `regression-nextjs-commerce/pom.xml` lines 21-22
and 111/116-117 for its `mcp.surefire.reportsDirectory` /
`mcp.allure.resultsDirectory` wiring, while that wiring was actually at
lines 23-24 and 113/118 in the same file at that time — a line-number
citation goes stale the moment an unrelated edit shifts the file, while a
named property or method does not.

Every item's metadata line also carries a **Cost** estimate: the number of
agent passes — discrete working sessions carried out under an instruction
like this one — that planning judges the fix would take. These are
planning estimates made at the time an item is written, not measurements,
and are not revisited unless the item itself is rewritten.

## A. Defects

Published behaviour returns a wrong or misleading answer. Action: fix, or
explicitly accept with a stated reason why the wrong answer is tolerable.

### A1. `regression_get_test_summary`'s `detailsTruncated` is true for almost every run

Module: regression-mcp-server | Cost: 2 passes

**What**: `regression_get_test_summary`'s `detailsTruncated` field is
computed in `RegressionMcpServer.summaryOutput`
(`RegressionMcpServer.java:327` as of 2026-08-23):
```
boolean detailsTruncated = summary.detailsTruncated() || summary.suites().stream().anyMatch(suite -> !suite.testcases().isEmpty());
```
The second operand — "any suite has at least one parsed testcase" — is
true for essentially any real Surefire report with at least one test,
regardless of whether anything was actually truncated. This makes the
field misinformative by name: a client reading `detailsTruncated: true`
off this tool has no way to tell, from the field alone, whether it means
"your data was cut off" or merely "this run had test cases" (true of any
run with at least one test).

The same-named field on `regression_get_failure_summary`
(`RegressionMcpServer.failureSummaryOutput`, `RegressionMcpServer.java:335`
as of 2026-08-23) means something different — it uses
`summary.detailsTruncated()` directly, with no such OR:
```
"detailsTruncated", summary.detailsTruncated());
```
This was observed directly in the 2026-08-22 session recording
(`regression-mcp-server/docs/SESSION_DEMO.md`): on the identical run,
`regression_get_test_summary` reported `"detailsTruncated":true` while
`regression_get_failure_summary` reported `"detailsTruncated":false`.

What `summary.detailsTruncated()` itself tracks —
`SurefireSummaryParser.parse` — is a genuine, bounded truncation event: two
count bounds, `MAX_SUITES = 100` and `MAX_TESTCASES = 500`
(`SurefireSummaryParser.java:27-28` as of 2026-08-23), checked at
`SurefireSummaryParser.java:71` (`boolean bounded = ordered.size() >
MAX_SUITES || cases.size() > MAX_TESTCASES;`), and one size bound on
failure detail text, `MAX_FAILURE_DETAIL_BYTES = 28 * 1024`
(`SurefireSummaryParser.java:32`), whose overflow sets a `truncated` flag
inside `SurefireSummaryParser.record` (`SurefireSummaryParser.java:105-119`,
`if (recordTruncated[0]) summaryTruncated[0] = true;` at line 116); the two
are combined at `SurefireSummaryParser.java:75`
(`records, "UNAVAILABLE", bounded || truncated[0]);`). It is
`regression_get_test_summary`'s own additional OR condition — not
`summary.detailsTruncated()` itself — that turns the field into something
else entirely for that one tool.

**Consequence of fixing this**: `regression-mcp-server/docs/SESSION_DEMO.md`
records the current, misleading value (`"detailsTruncated":true` from that
OR condition) verbatim as part of a real session transcript, and already
carries an annotation (added 2026-08-23) beside that value pointing at
this item and stating it reflects pre-fix behaviour rather than a genuine
truncation. Fixing this field's computation does not require adding that
annotation — it already exists — but does require rewriting it: the
annotation currently identifies the behaviour by pointing at this item's
identifier, and this file's own rule is that a fixed item is deleted and
its identifier never reused, so once A1 itself is gone, an annotation that
still points at "item A1" points at nothing. The fix must make the
annotation self-contained — describing the pre-fix behaviour on its own
terms, in the past tense, rather than by reference to an item that will no
longer exist — as part of any fix, not as a follow-up.

**Why this is tolerable for now**: no known consumer of
`regression_get_test_summary` currently acts on `detailsTruncated` — the
only observed reader is the session transcript cited above, a human-facing
record, not an automated one. The error also runs in the safer direction:
the field over-reports truncation rather than hiding it, so a client that
takes it at face value is led to fetch more detail than it needs, never to
miss detail that was actually cut off.

**Location**: `regression-mcp-server/src/main/java/com/aqa/mcp/RegressionMcpServer.java`
(`summaryOutput`, `failureSummaryOutput`, lines 327 and 335 as of
2026-08-23); `regression-mcp-server/src/main/java/com/aqa/mcp/execution/SurefireSummaryParser.java`
(`MAX_SUITES`, `MAX_TESTCASES`, `MAX_FAILURE_DETAIL_BYTES`, `parse`,
`record`; lines 27-28, 32, 71, 75, 105-119 as of 2026-08-23);
`regression-mcp-server/docs/SESSION_DEMO.md`.

## B. Debt

The fix is understood and was deferred. Action: schedule.

### B2. Allure plugin/report versions are declared per module instead of centralized

Module: cross-module (regression-petstore-api, regression-nextjs-commerce) | Cost: 1-2 passes

**What**: `allure.version`, `allure.maven.plugin`, and
`allure.report.version` are each declared as independent `<properties>`
inside every module that uses Allure, rather than being defined once in
the root `pom.xml` and inherited. `regression-petstore-api/pom.xml`
declares all three, and `regression-nextjs-commerce/pom.xml` declares the
same three properties of its own. The root `pom.xml` manages no Allure
property, dependency, or plugin at all. The two modules currently agree on
every value — `allure.version` 2.35.3, `allure.maven.plugin` 3.0.2,
`allure.report.version` 2.39.0 — so there is no divergence today, but
nothing stops that agreement from drifting the next time either module's
Allure wiring is touched in isolation, since each module's properties are
edited independently with no shared source of truth.

**Why this is not a simple hoist**: `allure-bom` (the Java test-adapter
library, versioned by `allure.version`) and `allure-commandline` (the
standalone report-renderer distribution that `allure-maven`'s `report`
goal downloads, versioned by `allure.report.version`) are independently
published artifacts whose version sets do not coincide. This was verified
directly while wiring up commerce's report generation: `allure.version`
2.35.3 is a real, published `allure-bom` release, but
`io.qameta.allure:allure-commandline:2.35.3` does not exist on Maven
Central — pointing `allure.report.version` at it makes `mvn allure:report`
fail outright with a dependency-resolution error, even though the module
builds and tests normally. `allure.report.version` had to be set to
`2.39.0` (the value petstore already used successfully) instead,
specifically because it is a real `allure-commandline` release, with no
assumption that it needs to track `allure.version` numerically. **This
warning survives any future centralization**: `allure.version` and
`allure.report.version` must remain two separate properties, never
collapsed into one shared "Allure version" — they name independently
versioned artifacts that do not track each other, and treating them as one
would silently reintroduce this exact failure mode.

**Additional cost of scheduling this fix**: any `pom.xml` change
invalidates the `setup-java` Maven dependency cache key used in CI, so the
next CI run after this fix is a cold run (full dependency re-download)
rather than a cache hit.

**Location**:
- `pom.xml` (root — no Allure properties or dependency/plugin management
  present)
- `regression-petstore-api/pom.xml` (`allure.version`, `allure.maven.plugin`,
  `allure.report.version` properties, and its `allure-maven` plugin block)
- `regression-nextjs-commerce/pom.xml` (same three properties, and its
  `allure-maven` plugin block)

### B3. Structural duplication across the three validator `Tool` classes

Module: regression-mcp-server | Cost: 3-4 passes

**What**: `ModuleBoundariesTool`, `FrameworkConventionsTool`, and
`ArchitectureTool` each independently implement the same set of 13
methods — `evaluate`, `parseRequest`, `reportOutput`, `moduleResultOutput`,
`violationOutput`, `inputSchema`, `violationSchema`, `moduleResultSchema`,
`outputSchema`, `readOnlyAnnotations`, `successResult`, `errorResult`, and
`serialize` — with near-identical bodies. All 13 are confirmed present, by
name, in all three files as of 2026-08-23. Measured line counts as of
2026-08-23: `ModuleBoundariesTool.java` 179 lines,
`FrameworkConventionsTool.java` 193 lines, `ArchitectureTool.java` 192
lines — an estimated 120-140 lines per file is duplicated
schema/envelope/evaluation-loop boilerplate rather than logic specific to
that tool's rule set.

**Location**:
- `regression-mcp-server/src/main/java/com/aqa/mcp/validation/ModuleBoundariesTool.java`
- `regression-mcp-server/src/main/java/com/aqa/mcp/validation/FrameworkConventionsTool.java`
- `regression-mcp-server/src/main/java/com/aqa/mcp/validation/ArchitectureTool.java`

**Why deferred**: each tool shipped independently across separate gates of
work, and consolidating the shared shape into a common helper was judged a
separate, purely-internal refactor with no effect on any tool's external
schema or behavior — not worth bundling into the gate that shipped the
third tool. See `docs/ROADMAP.md` for a concrete extraction proposal.

### B4. The run snapshot carries no progress signal while RUNNING

Module: regression-mcp-server | Cost: two separable fixes — removing the
redundant `reason` is 1 pass; genuine live progress reporting is 2-3
passes and is a change to the execution lifecycle

**What**: `regression_get_test_run`'s `stdoutBytes`/`stderrBytes` fields
are hardcoded to `0` the moment a run transitions to `RUNNING`
(`TestRunCoordinator.java:148` as of 2026-08-23,
`replace(run.snapshot, TestRunState.RUNNING, Instant.now(), null, null, 0, 0, false, false)`),
and the periodic background update that keeps a `RUNNING` run's persisted
record alive every 100ms (`TestRunCoordinator.observe`,
`TestRunCoordinator.java:281-294` as of 2026-08-23) re-persists that same
unchanged snapshot rather than recomputing byte counts from the live
`BoundedLogDrainer`s — real totals are computed only once, at terminal
persistence (`TestRunCoordinator.persistTerminal`,
`TestRunCoordinator.java:226-240` as of 2026-08-23: `long stdoutBytes =
stdout == null ? 0 : stdout.bytes();`). A client polling
`regression_get_test_run` while a run is `RUNNING` has no way to tell,
from these two fields, whether the run is progressing at all — observed
empirically in the 2026-08-22 session recording
(`regression-mcp-server/docs/SESSION_DEMO.md`): all 7 `RUNNING` polls
reported `stdoutBytes`/`stderrBytes` of `0`, with real values appearing
only on the 8th, terminal poll.

**Related characteristic**: `reason` duplicated `state` at every
observation in that same session (`RUNNING`/`RUNNING` on every non-terminal
poll, `PASSED`/`PASSED` at the terminal poll) — both values trace back to
the same helper, `TestRunCoordinator`'s internal `replace(...)`
(`TestRunCoordinator.java:345-350` as of 2026-08-23), which passes
`state.name()` as the `reason` argument unconditionally. This is
internally consistent (never wrong, just redundant with `state`).

**Location**: `regression-mcp-server/src/main/java/com/aqa/mcp/execution/TestRunCoordinator.java`
(lines 148, 226-240, 281-294, 345-350 as of 2026-08-23).

**Why deferred**: both characteristics were observed empirically during
the 2026-08-22 session recording rather than sought out. Adding genuine
live progress reporting would require recomputing byte counts from the
live drainers inside `observe()` instead of re-persisting the unchanged
snapshot — a behavior change to the execution lifecycle, not a
documentation fix.

### B5. Commerce scenarios are coupled to literal content of a third-party site

Module: regression-nextjs-commerce | Cost: 3-5 passes — this is the same
body of work as extending the commerce scenario set, and is sensibly
scheduled together with it rather than as an isolated de-brittling
exercise

**What**: both `regression-nextjs-commerce` feature files hardcode literal
content from `demo.vercel.store`, a third-party site this repository does
not control. `catalog_search.feature` asserts the search term "hoodie",
the expected product name "Acme Hoodie", and that every returned product
name contains "hoodie"; `cart_management.feature` drives the product "Acme
Circles T-Shirt" with the variant values "Black" and "S". All such
literals live in the two `.feature` files themselves; the step and page
layers receive them as parameters and hardcode nothing of their own.

**Location**:
- `regression-nextjs-commerce/src/test/resources/features/catalog_search.feature`
- `regression-nextjs-commerce/src/test/resources/features/cart_management.feature`

**Consequence**: a catalog or inventory change on `demo.vercel.store` (a
renamed product, a discontinued variant, different search results) turns
the regression suite red for a reason unrelated to the framework itself —
the failure would look identical to a genuine regression from the outside.

### B6. Direct AssertJ assertions inside `GeneralDefinitions`'s `@Then` steps

Module: regression-core | Cost: 2-3 passes

**What**: `regression-core`'s `GeneralDefinitions` record makes direct
`assertThat(...)` (AssertJ) calls inside its own `@Then`-annotated Cucumber
step methods (e.g. `varIsEqualToString`, `varIsEqualToObject`,
`varListContainsItem`), rather than delegating assertions to a dedicated
step/assertion layer.

**Location**: `regression-core/src/main/java/com/aqa/core/definitions/GeneralDefinitions.java`
(`varIsEqualToString`, `varIsEqualToObject`, `varListContainsItem`).

**Why deferred**: `GeneralDefinitions` sits at the `definitions` layer,
where CLAUDE.md's stated architecture keeps definitions thin (bind
Gherkin input, delegate) rather than asserting directly. No
`regression_validate_architecture` rule (ARCH-001..004) currently covers
this shape — it was noted as informational during the Architecture
Validator's design work but never turned into a scoped rule, since a
`definitions`-layer assertion rule needs its own design pass distinct from
ARCH-001..004's pages/components-focused scope.

### B7. `master` has no branch protection

Module: repository | Cost: 0 passes, one repository setting

**What**, verified 2026-08-23: `gh api
repos/zhabieiev/aqa-regression-framework/branches/master/protection`
returns HTTP 404, `{"message":"Branch not protected", ...}`. The
repository's `default_workflow_permissions` (`gh api
repos/zhabieiev/aqa-regression-framework/actions/permissions/workflow`) is
`"read"`.

**Consequence**: the commerce workflow
(`.github/workflows/commerce-regression.yml`) publishes the Allure report
to `gh-pages` on every push to `master` that touches
`regression-nextjs-commerce/**` or `regression-core/**`. With no branch
protection on `master`, a direct push (bypassing any pull request, review,
or required-check gate) that touches those paths still triggers that
publish — it bypasses both the build gate and the commerce gate and
publishes whatever the pushed content produces, green or not.

**Location**: repository branch protection settings (GitHub, not a
tracked file); `.github/workflows/commerce-regression.yml`'s `push:
branches: ["master"]` trigger.

## C. Accepted characteristics

A decision was made. Action: none, but every item below states the
concrete event that would reopen it.

### C1. ARCH-001 layering rule is single-hop only (no Symbol Solver)

Module: regression-mcp-server | Cost: n/a | Review trigger: a rule is
proposed that genuinely requires full type resolution.

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
(the ARCH-001 rule implementation, its class javadoc and its own doc
comment).

**Decision**: JavaParser's Symbol Solver was deliberately not added
speculatively; the project's standing rule is that a Symbol Solver is
added only once a specific rule demonstrates a genuine need for full type
resolution. Single-hop, declared-type-only detection was accepted as
adequate first-cut coverage.

### C2. Playwright traces are not captured by ReportCapture

Module: regression-jhipster | Cost: n/a | Review trigger: a
`regression-jhipster` failure whose diagnosis actually requires a trace,
which would also require an explicit review of relaxing the artifact MIME
allow-list to admit `.zip`.

**What**: this is a `regression-jhipster`-only characteristic —
`regression-nextjs-commerce` uses Selenium WebDriver, not Playwright, and
produces no traces at all. In `regression-jhipster`, `UiHooks` starts
Playwright tracing for every `@ui` scenario when enabled
(`UiHooks.startTracing`, `UiHooks.java:93-97` as of 2026-08-23), gated by
the `UI_TRACE` configuration property (`UiHooks.isTraceEnabled`,
`UiHooks.java:167-178` as of 2026-08-23), but only writes the trace `.zip`
to disk when the scenario actually fails (`UiHooks.stopTracing`,
`UiHooks.java:99-110` as of 2026-08-23: a passing scenario's trace is
discarded via the no-path `tracing().stop()` overload). The file is
written via a direct filesystem call to `target/playwright/traces/`
(`UiHooks.TRACE_DIRECTORY`, `UiHooks.java:27` as of 2026-08-23), entirely
outside `regression-mcp-server`'s capture pipeline.

Two independent barriers block serving such a trace through the MCP
server, even for a `regression-jhipster` run: (1) `ReportCapture.capture()`
only ever reads two staging roots, `layout.surefireStaging()`
(`ReportCapture.java:48` as of 2026-08-23) and `layout.allureStaging()`
(`ReportCapture.java:58` as of 2026-08-23) — `RunCaptureLayout`
(`RunCaptureLayout.java:7-8` as of 2026-08-23) has no third field for a
Playwright root, and `RunStore.layout(...)` (`RunStore.java:322-332` as of
2026-08-23) never constructs one, so a trace is never staged or published
in the first place; and (2) even if a trace file were published as an
artifact, `.zip` is not on `RunStore`'s MIME allow-list
(`ALLOWED_ARTIFACT_MIME_TYPES`, `RunStore.java:33-34` as of 2026-08-23,
enforced at `RunStore.java:248-250`) — a `.zip` resolves to
`application/octet-stream` via `RunStore.mimeTypeOf`
(`RunStore.java:301-316` as of 2026-08-23), which is not an allowed type —
and the artifact listing itself is built solely from the published Allure
result set (`RunStore.toArtifact`, `RunStore.java:287-291` as of
2026-08-23), so a trace file has no `artifactId` to be requested by,
regardless of MIME type.

**Location**:
- `regression-jhipster/src/main/java/com/aqa/jhipster/ui/hooks/UiHooks.java`
  (lines 27, 93-97, 99-110, 167-178 as of 2026-08-23)
- `regression-mcp-server/src/main/java/com/aqa/mcp/execution/ReportCapture.java`
  (lines 48, 58 as of 2026-08-23)
- `regression-mcp-server/src/main/java/com/aqa/mcp/execution/RunCaptureLayout.java`
  (lines 7-8 as of 2026-08-23)
- `regression-mcp-server/src/main/java/com/aqa/mcp/execution/RunStore.java`
  (`ALLOWED_ARTIFACT_MIME_TYPES`, lines 33-34 as of 2026-08-23; the
  allow-list enforcement point, lines 248-250 as of 2026-08-23;
  `RunStore.toArtifact`, lines 287-291 as of 2026-08-23;
  `RunStore.mimeTypeOf`, lines 301-316 as of 2026-08-23; `RunStore.layout`,
  line 322 as of 2026-08-23)

**Decision**: extending `ReportCapture` with a third staging root
(mirroring the existing Surefire-required/Allure-optional pattern) was
considered and rejected as the most invasive of the available fixes, for a
characteristic with no observed impact: no trace files exist on disk from
any recent run, `regression-nextjs-commerce` never produces one at all,
and a `regression-jhipster` fix would also require deliberately relaxing
the artifact MIME allow-list to admit `.zip` — a security-relevant change
to a hardened, closed allow-list that needs its own explicit review, not a
side effect of a UI-hooks change.

### C3. No automated check that Allure history accumulates on `gh-pages`

Module: regression-nextjs-commerce | Cost: n/a | Review trigger: a trend
reset actually occurs unnoticed.

**What**: nothing in `.github/workflows/commerce-regression.yml` or
elsewhere verifies that `regression-nextjs-commerce`'s Allure trend
history actually keeps accumulating across publishes. If the restore path
ever breaks — the `gh-pages/commerce/history` directory gets renamed, the
report's `reportDirectory` changes, or the `gh-pages` branch's layout is
restructured — the Restore step's own `if [ -d gh-pages/commerce/history
]; then ... else ... fi` guard simply falls through to its `else` branch,
`allure:report` still succeeds, the report still generates and publishes,
and the whole job still goes green. The trend silently resets to a single
data point, with no error, warning, or log line anywhere distinguishing
that outcome from a genuine first publish — the only related log lines
(`[INFO] Try to finding out allure X.Y.Z` and `[INFO] Generate Allure
report (report) with version X.Y.Z`) are identical in shape regardless of
whether any history was restored beforehand.

**Location**: `.github/workflows/commerce-regression.yml` (the "Restore
Allure history from gh-pages", "Generate Allure report", and "Publish
Allure report to gh-pages" steps).

**Decision**: detecting this automatically would require the workflow
itself to compare pre- and post-generation trend point counts — for
example, reading `history-trend.json`'s array length before restoring and
again after generating, and failing the job if the count did not grow by
exactly one. This was judged non-trivial complexity to add for a failure
mode that is recoverable rather than destructive: no test result, report
content, or job outcome is made incorrect by a silently reset trend, only
the multi-run trend graph's continuity is lost. Detection today is manual:
count the data points in `commerce/history/history-trend.json` on
`gh-pages`, or open the published report's Trends tab, and confirm the
count grows between successive publishes.

### C4. A module's `target/allure-results` accumulates across runs, and nothing resets it

Module: cross-module | Cost: n/a | Review trigger: a locally generated
report shows results that do not belong to the run just executed.

**What**, verified 2026-08-23 by two consecutive
`regression-nextjs-commerce` runs with no `clean`: `target/allure-results`
file count went 11 → 15 → 20, with every earlier file still present at
each step. No POM in the reactor configures `maven-clean-plugin` against
this directory; no code in `regression-mcp-server` deletes a module's
`target/allure-results`; no document instructs anyone to clean it before a
run.

**Why this is accepted rather than a defect**: MCP-driven runs are
unaffected. `MavenInvocationFactory` overrides
`mcp.allure.resultsDirectory` to point at a per-run staging directory that
`RunStore` creates fresh under a random nonce, which `Files.createDirectory`
refuses to let a later run reuse — so an MCP-driven run's Allure listener
never writes into a shared, accumulating directory in the first place. CI
is unaffected for the same underlying reason plus a clean environment:
each runner starts with an empty `target/`. The exposure is manual local
runs: a developer running `mvn -pl regression-nextjs-commerce test`
repeatedly without `clean`, followed by `mvn allure:report`, gets a report
whose trend and results merge every prior local run's results with the
one just executed.

**Location**: `regression-mcp-server/src/main/java/com/aqa/mcp/execution/MavenInvocationFactory.java`
(the `mcp.allure.resultsDirectory` override); `RunStore.create` (line 56
as of 2026-08-23), `RunStore.layout` (line 322 as of 2026-08-23), and
`RunStore.nonce` (line 356 as of 2026-08-23) — together, the per-run
directory creation; no `maven-clean-plugin` configuration exists anywhere
in the reactor's POMs naming `allure-results`.

### C5. The running MCP server's jar can lag `master` by days

Module: regression-mcp-server | Cost: n/a | Review trigger: a change to
how or when `regression-mcp-server.jar` is rebuilt or the live server
process is restarted.

**What**: the running MCP server process is launched from
`regression-mcp-server/target/regression-mcp-server.jar`. That jar is
deliberately not rebuilt while a live MCP client holds a lock on it (see
`regression-mcp-server/README.md`'s JAR-lock troubleshooting section), so
the jar backing the live server can lag the current `master` source by
however long that client stays connected — potentially days. Because of
this, results returned by the MCP tools (e.g.
`regression_validate_architecture`, `regression_validate_module_boundaries`,
`regression_validate_framework_conventions`) are not evidence about the
current state of the source; they reflect whatever code was compiled into
the jar at its last build.

**Location**: `regression-mcp-server/target/regression-mcp-server.jar`
(the running artifact); `regression-mcp-server/README.md` (JAR-lock
troubleshooting section).

**Decision**: rebuilding on every source change was rejected because a
live client holds an OS-level lock on the jar on Windows, making an
in-session rebuild fail outright; the accepted workaround is to stop the
live MCP client connection before rebuilding, as already documented in
`regression-mcp-server/README.md`. No automatic staleness signal exists
today — a consumer of the MCP tools' output must independently confirm the
jar was rebuilt after the source state it cares about.

## D. Open questions and unproven assumptions

Closed by observation or verification, not by work. Each item states what
to capture when its triggering event occurs.

### D1. Unreproduced `@ui` hang in `regression-jhipster` (2026-08-21)

Module: regression-jhipster | Cost: n/a

**What**: on 2026-08-21 a `regression-jhipster` `@ui`-tagged Cucumber run
hung for over five minutes with zero output and no exception, then passed
on an identical immediate retry. Root cause unidentified — no exception,
no partial output, and no known trigger to reproduce on demand.

**Location**: `regression-jhipster/src/test/java/com/aqa/jhipster/runners/RunCucumberTest.java`
(the `@ui`-tagged suite); mitigated reactor-wide by
`forkedProcessTimeoutInSeconds` in the root `pom.xml`'s Surefire
`pluginManagement` configuration (value 900, as of 2026-08-23).

**What was already tried**: a bounded reproduction probe was run the same
day — five consecutive `mvn -pl regression-jhipster -am test
-Dcucumber.filter.tags="@ui" -Denv=dev` runs against the live app
(confirmed reachable via `curl`, HTTP 200, immediately before the probe).
All five passed cleanly with no hang: 27s, 23s, 31s, 25s, and 26s wall
clock, each producing 5 Scenarios (5 passed), 24 Steps (24 passed), BUILD
SUCCESS.

**If it recurs**: before killing anything, capture Surefire's timeout
output (the forked-process-timeout error and any partial log) and a
thread dump of the forked JVM (e.g. `jstack` against the forked Surefire
process, found via its process tree) — that diagnostic evidence is exactly
what this item currently lacks.

### D2. Two unexplained CI failures on 2026-08-17

Module: regression-mcp-server | Cost: n/a

**What is established**: of 101 runs of "Java CI with Maven and Reports
(Java 21)" visible as of 2026-08-23 (dating back to 2026-07-12), exactly 2
ended in `failure`, both on 2026-08-17, on different runners:

- Run 32052449113 (`windows-latest`, commit `09adddec`, 2026-08-17T17:54:58Z,
  one attempt, confirmed via the GitHub API never re-run):
  `FailureArtifactStoreTest.readArtifactRejectsATraversalShapedRelativePathHandCraftedIntoAPublishedIndex`
  failed. Surefire: `[ERROR] Tests run: 7, Failures: 1, Errors: 0,
  Skipped: 0, Time elapsed: 1.528 s <<< FAILURE! -- in
  com.aqa.mcp.execution.FailureArtifactStoreTest`. The assertion itself:
  ```
  org.opentest4j.AssertionFailedError:
  [fixture must actually escape allureRoot]
  expected: C:\Users\RUNNER~1\AppData\Local\Temp\junit13496818424901618807\outside-secret.txt
   but was: C:\Users\runneradmin\AppData\Local\Temp\junit13496818424901618807\outside-secret.txt
      at com.aqa.mcp.execution.FailureArtifactStoreTest.readArtifactRejectsATraversalShapedRelativePathHandCraftedIntoAPublishedIndex(FailureArtifactStoreTest.java:121)
  ```
  The next run of this workflow (32053652332, a different commit,
  2026-08-17T18:10:39Z) passed.
- Run 32036451902 (`ubuntu-latest`, commit `f337b48c`,
  2026-08-17T13:43:57Z, **two attempts, both failed**, confirmed via the
  GitHub API's per-attempt records):
  `TestRunCoordinatorTest.timeoutSchedulingFailureNeverPublishesRunningAndCleansProcessAndLock`
  failed in attempt 1 and again in attempt 2, with the same assertion
  shape both times. Surefire (attempt 2): `[ERROR] Tests run: 18,
  Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 6.691 s <<< FAILURE!
  -- in com.aqa.mcp.execution.TestRunCoordinatorTest`. The assertion
  (attempt 2):
  ```
  java.lang.AssertionError:

  Expecting size of:
    [OwnedProcessIdentity[pid=2772, startInstant=2026-08-17T13:49:33.790Z, parentPid=2297, depth=8, observedAt=2026-08-17T13:49:33.844749290Z]]
  to be greater than or equal to 3 but was 1
      at com.aqa.mcp.execution.TestRunCoordinatorTest.timeoutSchedulingFailureNeverPublishesRunningAndCleansProcessAndLock(TestRunCoordinatorTest.java:150)
  ```
  Attempt 1 (2026-08-17T13:44:41Z) failed the identical method with the
  identical assertion shape (a different captured `OwnedProcessIdentity`,
  `Time elapsed: 0.029 s` versus attempt 2's `0.060 s`) — this run's own
  immediate re-run reproduced the same failure rather than passing.

**What is NOT established**: whether the two runs' failures — a temp-path
string mismatch in `FailureArtifactStoreTest`, and a process-count
assertion in `TestRunCoordinatorTest` — share a cause; they are different
tests, different runners, different assertions, roughly four hours apart.
Whether either is environmental (timing, runner load, filesystem/path
behaviour) or a genuine intermittent defect in the tested code was not
investigated at the time and is not established here — the
`FailureArtifactStoreTest` assertion's own expected/actual values show a
Windows short-name (`RUNNER~1`) versus long-name (`runneradmin`)
path-string difference, quoted above as raw evidence, not as a diagnosed
cause. Whether either failure has recurred since 2026-08-17 is not
established: no other failed run appears among the 101 visible runs as of
2026-08-23.

**What the `FailureArtifactStoreTest` failure actually verified, and did
not verify**: the failed assertion, message `[fixture must actually
escape allureRoot]`, checks that the test's own fixture is set up
correctly — that a hand-crafted "outside" file genuinely lands outside
`allureRoot` — before the traversal-rejection assertion it guards is ever
exercised. The failure occurred in that fixture-setup check, not in the
traversal-rejection assertion itself: the test never reached the code path
that verifies rejection. Nothing here shows the traversal protection was
breached, and nothing here shows it was exercised and held — the assertion
that would have shown either never ran. Recorded as raw evidence, without
diagnosis: the two compared paths differ as `RUNNER~1` versus
`runneradmin`, a Windows short-name versus long-name form of the same
directory. An unexplained failure in a security test's own setup still
deserves an explanation, not a re-run assumed to fix it — especially
since, in the other of these two runs, a re-run did not in fact fix it.

**The `TestRunCoordinatorTest` failure is not characterized as a flake**:
it failed the same method on two consecutive attempts of the same commit,
on `ubuntu-latest`, with the same assertion shape both times — a run
expecting at least 3 `OwnedProcessIdentity` records observed 1. It has not
reproduced locally. Open question, not a conclusion: whether the assertion
encodes an assumption about process-tree depth that does not hold on a
GitHub-hosted runner. What would settle it: run this test on an
`ubuntu-latest` runner with the observed process tree dumped at the point
of assertion, and compare that dump against the same test run locally.

**Recorded separately, as context and NOT as a claim about either
failure above**: `RegressionMcpServerStdioIntegrationTest` uses a fixed
`REQUEST_TIMEOUT_SECONDS = 10` and launches no Maven process and no
browser (discovery tests assert no application child process; run-
lifecycle tests substitute `ControlledMcpServerMain`/
`ControlledProcessLauncher`, which spawn only a plain `java` test
fixture). This test passed cleanly in every attempt of both failing runs:
`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0` (windows-latest, run
32052449113); `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`
(ubuntu-latest, run 32036451902, both attempt 1 and attempt 2). No
failure in the visible history is attributed to it.

**On recurrence**: capture the failing job's full test output and the
exact assertion message before any re-run, and record the runner OS
(`windows-latest` versus `ubuntu-latest`). A re-run alone is not evidence
of resolution: `TestRunCoordinatorTest`'s failure above reproduced
identically on an immediate re-run of the same commit.

**Location**: `regression-mcp-server/src/test/java/com/aqa/mcp/execution/FailureArtifactStoreTest.java`
(`readArtifactRejectsATraversalShapedRelativePathHandCraftedIntoAPublishedIndex`,
line 121 as of 2026-08-23);
`regression-mcp-server/src/test/java/com/aqa/mcp/execution/TestRunCoordinatorTest.java`
(`timeoutSchedulingFailureNeverPublishesRunningAndCleansProcessAndLock`,
line 150 as of 2026-08-23);
`regression-mcp-server/src/test/java/com/aqa/mcp/RegressionMcpServerStdioIntegrationTest.java`
(`REQUEST_TIMEOUT_SECONDS`).

### D3. Unproven assumptions about the commerce publishing path

Module: regression-nextjs-commerce | Cost: n/a

This item and `HANDOFF.md`'s "Not yet proven" section cover the same four
assumptions; `HANDOFF.md` remains the narrative source, and the two
cross-reference each other.

**What**: as of 2026-08-23, `HANDOFF.md`'s "Not yet proven" section states
that "publishing on a genuinely RED test run has never happened." That one
sentence anchors four distinct unproven assumptions: (1) a
genuinely red run has never actually exercised the publish-on-red path;
(2) the Publish step's `index.html` existence guard has never fired in
practice; (3) the concurrent-push race on `gh-pages` is accepted but
unexercised; (4) a manual job re-run's effect on the trend file has never
been observed, only reasoned about.

**Location**: `HANDOFF.md` ("Not yet proven" section);
`.github/workflows/commerce-regression.yml` (Publish step's `index.html`
guard, and the workflow's `cancel-in-progress` concurrency group).

### D4. An MCP-driven run does not rebuild `regression-core`

Module: regression-mcp-server / regression-core | Cost: n/a

**What is established**: `MavenInvocationFactory` targets the module via
`-f <module>/pom.xml` with no `-pl` and no `-am`, and the only goal passed
is `test`, so `regression-core` is resolved from the local Maven
repository (`~/.m2`) rather than built in the same invocation.

**Open question**: whether a stale `regression-core` artifact already
present in the local Maven repository can actually be used by an
MCP-driven run while newer, uncommitted `regression-core` sources sit in
the working tree — and whether anything in the current system would
signal that mismatch to a client or operator. This is not established
either way.

**Verification that would settle it**: modify a `regression-core` class
observably (e.g. change a log message or a returned value in a method a
target module's steps call), do not run `mvn install` for
`regression-core`, then trigger an MCP-driven run of a module that depends
on it, and check whether the modified behavior is observed in that run's
output. If it is not, the local `~/.m2` artifact was used silently.

**Location**: `regression-mcp-server/src/main/java/com/aqa/mcp/execution/MavenInvocationFactory.java`
(the constructed command line — `-f`, goal `test`, no `-pl`, no `-am`).

### D5. No retention policy for the `gh-pages` branch

Module: repository | Cost: n/a

**What**, verified 2026-08-23: `gh-pages` (`origin/gh-pages`) has 3
commits, 81 blobs, and a total tree size of 3,098,723 bytes. Each publish
rewrites roughly 26 files, and only `HEAD` is ever read (by GitHub Pages,
and by the Restore-history step). No policy exists anywhere in this
repository for compacting or truncating `gh-pages`'s history.

**Open question, not a problem**: whether unbounded growth of this
branch's history (as opposed to its `HEAD` tree, which is what is actually
served and read) will ever become a practical concern, and at what point,
given that only `HEAD` is read today.

**Location**: `origin/gh-pages` branch (not a tracked file in `master`).

### D6. `ArchitectureTool`'s per-module output carries no scanned-source-count signal

Module: regression-mcp-server | Cost: n/a

**What is established**: `ArchitectureTool.moduleResultOutput` reports
`module`, `profile`, `rulesApplied`, `violations`, `advisoryViolations`,
and `truncated` for each module — no field states how many source files
were actually scanned. Every rule in `ArchitectureRules`, including
`NoPackageCycles.evaluate`, simply iterates whatever
`context.moduleSources()` contains; a module for which that collection is
empty produces the same zero violations as a module that was genuinely
scanned and found clean.

**Open question**: whether a run that scanned zero source files for a
module is currently distinguishable, from the tool's own output alone,
from a run that scanned real sources and found none of that rule's
violations. As things stand, it is not: the output carries no signal that
any source file was actually scanned, so a run that scanned nothing is
indistinguishable from a clean one — and the real-reactor ARCH-002 guard
therefore cannot rule out a vacuous pass on that basis alone.

**Closed by observation**: inspect whether the tool's per-module output
could carry a scanned-source count — for example the size of
`context.moduleSources()` at evaluation time — without a schema change,
or whether adding it would require broadening `moduleResultSchema` and
every consumer that depends on its current shape.

**Location**: `ArchitectureTool.evaluate`, `ArchitectureTool.moduleResultOutput`,
`ArchitectureTool.moduleResultSchema`; `ArchitectureRules.NoPackageCycles.evaluate`.

### D7. Response key order is not stable across server restarts

Module: regression-mcp-server | Cost: n/a

**What**: `RegressionMcpServer.runOutput` (`RegressionMcpServer.java:313-323`
as of 2026-08-25) builds a `java.util.LinkedHashMap` in a deliberate field
order, then returns `Map.copyOf(data)` (line 322). `Map.copyOf` returns one
of the JDK's immutable map implementations, which do not preserve insertion
order; HotSpot randomizes an immutable map's iteration order independently
per JVM start. The `LinkedHashMap`'s deliberate ordering is therefore dead
from the moment `Map.copyOf` wraps it. This is functionally harmless — JSON
object key order carries no semantic meaning, and no test in the module
asserts an exact key order (confirmed during A2's inspection phase; see
`output.log`'s "PHASE A'' " Q6, which found no test compares against
`runOutputSchema()`'s exact field-name set) — but a
`regression_get_test_run`/`regression_start_test_run`/
`regression_cancel_test_run` response's JSON key order will differ between
separate server restarts, purely as an artifact of `Map.copyOf`'s randomized
iteration order rather than any change in the underlying data.

**Closed by observation, not work**: if a future consumer starts depending
on stable key order, replace `Map.copyOf(data)` with
`Collections.unmodifiableMap(data)`, which preserves `LinkedHashMap`'s
insertion order while remaining unmodifiable.

**Location**: `regression-mcp-server/src/main/java/com/aqa/mcp/RegressionMcpServer.java`
(`runOutput`, lines 313-323 as of 2026-08-25).

### D8. `run.json` has no reader

Module: regression-mcp-server | Cost: n/a

**What**: `RunStore.create` writes an immutable `run.json` per run
(`RunStore.java:64` as of 2026-08-25,
`writeNew(directory.resolve("run.json"), record);`) alongside the live,
atomically-replaced `status.json`, but nothing in production code ever
reads `run.json` back: every read path in `RunStore` (`get`, `persisted`,
`summary`, `failureSummary`, `artifacts`, `readArtifact`) resolves
exclusively through the private `status(String id)` method, which always
targets `status.json`. The only place anywhere in the module that reads
`run.json` at all is a test,
`RunStoreTest.runMetadataIsImmutableAndStatusReplacementLeavesNoTemporaryFiles`,
which reads it once before a status transition and once after, and asserts
the two reads are equal — i.e. it compares `run.json` to itself across
time, to prove immutability, not to any other file or any production
consumer. This was verified directly during A2's inspection phase
(`output.log`'s "PHASE A'' " Q3), including a targeted grep of the whole
module for `run\.json` that found no other match.

**Closed by observation, not work**: if a future feature needs to read a
run's original, pre-mutation request shape (module/environment/tags/etc.
as they were at creation, independent of any later state transition),
`run.json` already exists and already carries that data — the fix is
adding a reader, not adding the file. If no such need ever materializes,
`run.json` remains a write-only audit artifact and nothing needs to
change.

**Location**: `regression-mcp-server/src/main/java/com/aqa/mcp/execution/RunStore.java`
(`create`, line 64 as of 2026-08-25; `status`, the sole read-path target
for every other `RunStore` reader);
`regression-mcp-server/src/test/java/com/aqa/mcp/execution/RunStoreTest.java`
(`runMetadataIsImmutableAndStatusReplacementLeavesNoTemporaryFiles`).

### D9. `SurefireSummaryStoreTest`'s legacy fixture is not frozen

Module: regression-mcp-server | Cost: n/a

**What**: `ReportCaptureTest.executionRecordsRemainReadableAndAreNotUpgradedWhenTheirStatusChanges`
proves backward compatibility with a hand-written, hardcoded literal JSON
string that encodes the pre-A2 `RunSnapshot` shape and stays frozen in
source text regardless of what `RunSnapshot` looks like today.
`SurefireSummaryStoreTest.readsOnlyPublishedIndexAndRejectsMissingDigestMismatchWrongRunAndActiveOrLegacyRuns`
appears to test the same kind of thing, but its `old` fixture
(`SurefireSummaryStoreTest.java:35` as of 2026-08-25) is not a literal — it
is built at test-run time by serializing an actual `RunSnapshot` (`legacy`,
constructed via this same file's own local `snapshot(TestRunState)`
builder, line 61 as of 2026-08-25) through a live `JsonMapper` with no
`NON_NULL` inclusion configured. Because that builder must track
`RunSnapshot`'s current arity (it gained a trailing `null` for
`skippedTests` during A2), this fixture's serialized JSON now includes
`"skippedTests":null` — a field that did not exist in the actual pre-A2
persisted shape it is meant to represent. It tracks the CURRENT record
shape, not the historical one, and will continue doing so for every future
`RunSnapshot` field addition.

This does not currently affect the test's own pass/fail outcome: the test
only exercises this fixture to prove `store.summary(legacy.runId())`
throws `NOT_FOUND` for a `schemaVersion: 2` (pre-capture-schema) record, a
check that short-circuits on `record.capture() == null` before ever
inspecting the embedded snapshot's field count or names. But the
backward-compatibility proof this repository's own review process expected
here — that an ALREADY-STORED, pre-migration-shaped file still
deserializes under a grown `RunSnapshot` — is carried by
`ReportCaptureTest`'s literal alone; this fixture does not independently
corroborate it, despite reading as if it does.

**Closed by observation, not work**: if this ever needs to become a
genuine second proof of the same claim, replace the
dynamically-serialized `old` string with a hand-written literal (mirroring
`ReportCaptureTest`'s), frozen at whatever `RunSnapshot` shape is current
at the time it is written.

**Location**: `regression-mcp-server/src/test/java/com/aqa/mcp/execution/SurefireSummaryStoreTest.java`
(`readsOnlyPublishedIndexAndRejectsMissingDigestMismatchWrongRunAndActiveOrLegacyRuns`,
line 35 as of 2026-08-25; `snapshot(TestRunState)`, line 61 as of
2026-08-25); `regression-mcp-server/src/test/java/com/aqa/mcp/execution/ReportCaptureTest.java`
(`executionRecordsRemainReadableAndAreNotUpgradedWhenTheirStatusChanges`,
the genuine frozen-literal fixture, for comparison).

### D10. `recoverIfUnowned`'s skipped-count guard is untested

Module: regression-mcp-server | Cost: n/a

**What**: `TestRunCoordinator.recoverIfUnowned` carries a restart-recovered
run's freshly captured skipped-test count through with the same overwrite
guard used in `execute()`
(`TestRunCoordinator.java`, `recoverIfUnowned`):
```
Integer captured = capture(snapshot.runId());
store.update(replaceWithReason(snapshot, TestRunState.ERROR, "SERVER_RESTART_RECOVERY", snapshot.startedAt(), Instant.now(),
        snapshot.exitCode(), snapshot.stdoutBytes(), snapshot.stderrBytes(), snapshot.stdoutTruncated(), snapshot.stderrTruncated(),
        captured != null ? captured : snapshot.skippedTests()), stale.ownedProcesses());
```
No test asserts on `skippedTests` for this path: a targeted check of
`StaleRunRecoveryTest.java` (as of 2026-08-25) found zero occurrences of
`skippedTests` anywhere in the file, even though one of its existing tests,
`restartPublishesValidatedStagedCaptureBeforeItsTerminalRecoveryState`,
already writes a genuine, parseable Surefire XML into the run's staging
directory before triggering recovery — meaning `captured` is almost
certainly non-null when that test's `TestRunCoordinator` is constructed,
yet nothing checks what value ends up on the resulting `RunSnapshot`. The
branch executes under existing coverage, but its *value* is unverified.

The equivalent guard in `execute()`
(`TestRunCoordinator.java:119-120,161-162,168-169,174-175` as of
2026-08-25: `Integer captured = capture(run); if (captured != null)
skippedTests = captured;`, at all four call sites) does have a dedicated
test —
`TestRunCoordinatorTest.secondCaptureCallInTheRuntimeExceptionPathDoesNotOverwriteTheFirstCallsSkippedCount`
— which forces a real capture to succeed once, forces a second (necessarily
null-returning) capture call, and asserts the final persisted
`skippedTests` still reflects the first call's value.

**Open question**: whether `recoverIfUnowned`'s branch is reachable with a
genuinely non-null `captured` value in practice at all. For a run orphaned
by a server restart, the Maven process was killed (by the crash or restart
itself, not gracefully) before recovery ever runs — whether
`ReportCapture` can parse anything meaningful from whatever partial state
the staging directory was left in at that moment is unverified here. It may
be that `captured` is null in every realistic restart-recovery scenario, in
which case this guard's `captured != null` branch is dead in production
even though `restartPublishesValidatedStagedCaptureBeforeItsTerminalRecoveryState`
can make it non-null under a controlled, hand-written fixture.

**Closed by observation, not work**: add an assertion on `skippedTests` to
`restartPublishesValidatedStagedCaptureBeforeItsTerminalRecoveryState` (or a
new test alongside it) mirroring the `TestRunCoordinatorTest` assertion
above, and separately, when a genuine server-restart recovery is next
observed against a run that was actually mid-Surefire-execution, record
whether the staging directory left behind anything `ReportCapture` could
parse.

**Location**: `regression-mcp-server/src/main/java/com/aqa/mcp/execution/TestRunCoordinator.java`
(`recoverIfUnowned`, lines 272 and 275 as of 2026-08-25; `execute`'s four
guarded call sites, lines 119-120, 161-162, 168-169, 174-175 as of
2026-08-25);
`regression-mcp-server/src/test/java/com/aqa/mcp/execution/StaleRunRecoveryTest.java`
(`restartPublishesValidatedStagedCaptureBeforeItsTerminalRecoveryState`);
`regression-mcp-server/src/test/java/com/aqa/mcp/execution/TestRunCoordinatorTest.java`
(`secondCaptureCallInTheRuntimeExceptionPathDoesNotOverwriteTheFirstCallsSkippedCount`,
the comparable, existing execute()-side test).

### D11. Four `StaticJavaParser`-based test fixtures may depend on another test class's `ThreadLocal` mutation

Module: regression-mcp-server | Cost: n/a

**What**: `ArchitectureRulesTest` and `FrameworkConventionRulesTest` both call
`StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)`,
mutating `StaticJavaParser`'s configuration, which is stored in a single
`ThreadLocal<ParserConfiguration>` shared by every caller running on the same
thread — not test-instance-scoped state. Four other test classes —
`EvaluationContextTest`, `BasePackagesTest`, `ModuleBoundaryRulesTest`, and
`SourceUnitTest` — parse fixtures through `StaticJavaParser.parse(...)`
without configuring any language level of their own.

**Open question**: whether those four classes are green because their own
fixtures genuinely need nothing above the parser library's default language
level, or because one of the two mutating classes happened to already run on
the same Surefire thread first, leaving that thread's `ThreadLocal` slot
configured to `JAVA_21` for whichever test runs after it. Test execution
order within `regression-mcp-server` is not pinned by the POM, so this is not
settled by the module's tests currently passing. `JavaSourceScanner` formerly
performed the same kind of global, per-thread mutation from a `static {}`
initializer and no longer does, so any such cross-class dependency among
these six test classes is now one contributor short of whatever might have
configured the thread before — this item does not claim the six classes are
affected by that specific removal, only that it eliminated one possible
source of the same shared-state pattern.

**Closed by observation, not work**: parse each of the four non-configuring
classes' fixtures on a thread where no other class in the module has
previously configured `StaticJavaParser`'s language level (for example, run
each of the four test classes alone via `-Dtest=<ClassName>`, or on a freshly
forked JVM with no prior `StaticJavaParser` use in that fork), and confirm
each fixture still parses successfully. This is closed by observing the four
in isolation, not by rewriting any of the six classes.

**Location**: `regression-mcp-server/src/test/java/com/aqa/mcp/validation/ArchitectureRulesTest.java`,
`regression-mcp-server/src/test/java/com/aqa/mcp/validation/FrameworkConventionRulesTest.java`
(the two `setLanguageLevel` calls);
`regression-mcp-server/src/test/java/com/aqa/mcp/validation/EvaluationContextTest.java`,
`regression-mcp-server/src/test/java/com/aqa/mcp/validation/BasePackagesTest.java`,
`regression-mcp-server/src/test/java/com/aqa/mcp/validation/ModuleBoundaryRulesTest.java`,
`regression-mcp-server/src/test/java/com/aqa/mcp/validation/SourceUnitTest.java` (the four
`StaticJavaParser.parse(...)` calls with no language-level configuration of their own).

## Where module-level debt lives

This file covers cross-module, repository-level, and
`regression-mcp-server` debt. Per-module limitations are maintained with
their own modules, not duplicated here:

- `regression-petstore-api/README.md`, "Current Limitations and
  Trade-offs" (10 items)
- `regression-jhipster/README.md`, "Current Limitations and Trade-offs"
  (10 rows)
- `regression-mcp-server/README.md`, "v1.0 limitations", which restates
  items from this file and cites it
- `docs/ROADMAP.md`, "Decisions" section: three formal decisions
  (`regression-petstore-api` not registered for MCP execution;
  `regression-jhipster` not run in CI; `regression-petstore-api` not run
  in CI)
