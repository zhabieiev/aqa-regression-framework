# Roadmap

This file is a forward-looking handoff for whoever (human or AI agent)
picks up this project next. It orients a fresh session to where things
stand and where the concrete opportunities are across the whole `regression`
reactor — `regression-core` and all four product/tooling modules — without
requiring conversation history that no longer exists. This is the single
reactor-wide roadmap; `README.md`'s own "Further reading" section points
here rather than keeping a separate list.

For known, accepted debt — what's imperfect today and why it was left that
way — see [`docs/TECHNICAL_DEBT.md`](TECHNICAL_DEBT.md). This file does not
repeat that list; read it first for context on what's already known and
deliberately deferred.

## Possible next steps

None of the following are authorized or scheduled work — they are
concrete, code-pointer-backed opportunities for a future task, each
requiring its own scoping and explicit authorization before any change is
made (per `CLAUDE.md`'s repository instructions). They are grouped by
module/area; each module's own README carries more detail where noted.

### regression-petstore-api

- Add a failure-safe fallback cleanup path for the Petstore delete scenario:
  today the tested delete flow has no independent fallback cleanup if the
  delete request itself fails. See `regression-petstore-api/README.md`'s
  "Current Limitations and Trade-offs" for the full context.

### regression-jhipster

- Add a dedicated UI assertion timeout, an ID-based scenario cleanup
  registry, and pagination-aware API cleanup. This is one item drawn from a
  larger, already-detailed list — see `regression-jhipster/README.md`'s
  "Next Steps and Improvements" (Priority 1 and 2) for the complete,
  code-pointer-backed breakdown; that list is not repeated here.

### Reactor-wide / cross-module

- **Done for `regression-nextjs-commerce`** (PRs #23, #24): a non-interactive
  CI reporting workflow now exists — `.github/workflows/commerce-regression.yml`
  generates the module's Allure report and publishes it to the `/commerce/`
  subdirectory of the `gh-pages` branch on every push to `master`, restoring
  and republishing trend history so it accumulates across runs instead of
  resetting each time. Verified live at
  `https://zhabieiev.github.io/aqa-regression-framework/commerce/` and
  confirmed accumulating by two real CI publishes — see `HANDOFF.md`'s
  2026-08-23 session entry for the full trail. `regression-petstore-api`'s
  `run-tests.sh` remains an intentionally local, interactive workflow,
  unaffected by this — see `README.md`'s "Configuration and reporting". A
  durable retention/pruning policy for the accumulating `gh-pages` history
  itself was not part of this work and remains open if the branch's size
  becomes a concern — tracked as `docs/TECHNICAL_DEBT.md` item D5.
- Document a common module-execution convention for local and CI use
  (local execution is already documented in root `README.md`'s "Running
  Maven" section; no CI convention exists yet — `.github/workflows/main.yml`
  only runs `regression-core` and `regression-mcp-server` jobs today).
- Maintain an OpenAPI Generator compatibility matrix for modules that
  generate models (`regression-jhipster`, `regression-petstore-api`).
- Add schema and contract validation as a layer separate from DTO mapping.

### regression-mcp-server

An inspection pass produced a full architecture map
(`regression-mcp-server/docs/ARCHITECTURE.md`) and test-suite map
(`regression-mcp-server/docs/TEST_MAP.md`); see `ARCHITECTURE.md`'s
baseline note for the commit they were last fully verified against. The
candidates below are that pass's prioritized output — planned work, not
yet authorized. Corresponding
debt items are catalogued in `docs/TECHNICAL_DEBT.md` (A3, B9-B10, C6,
D12-D13); this list is refactoring/improvement candidates, kept separate
from that debt catalogue per this file's own scope.

**Ranked by cost (ascending):**

1. **Fix `regression-mcp-server/docs/TOOLS.md`'s `openWorldHint` claim for `regression_start_test_run`**
   (closes `docs/TECHNICAL_DEBT.md` item A3). Cost: 1 pass. Risk: none —
   documentation-only.
2. **`TestRunCoordinator` capture-guard extraction**: the two-statement
   "capture, then keep-if-non-null" block (`Integer captured =
   capture(run); if (captured != null) skippedTests = captured;`) appears
   at four sites, all inside `execute()` — the early-cause branch, the
   normal-completion path, and the `InterruptedException` and
   `RuntimeException` catches. Extract a private helper
   (`captureOrKeep(Active run, Integer current)`); each site collapses to
   one line. `recoverIfUnowned` has a fifth, analogous occurrence that
   differs in both shape and inputs — a `captured != null ? captured :
   snapshot.skippedTests()` ternary passed inline as a `replaceWithReason`
   argument, over a `String runId` and a `RunSnapshot`, with no `Active
   run` and no `Integer` local — so the proposed signature does not fit it
   as written. Open choice for the implementing pass: scope the helper to
   `execute()` only, or generalise it (over a `runId` plus a fallback
   `Integer`) to cover `recoverIfUnowned` too.
   Cost: 1 pass. Risk: currently unprotected — no
   test pins the merge-vs-overwrite behaviour this extraction must preserve.
   `secondCaptureCallInTheRuntimeExceptionPathDoesNotOverwriteTheFirstCallsSkippedCount`
   is named for it but, as `docs/TECHNICAL_DEBT.md` item B11 establishes,
   never reaches that interleaving — its fixture throws from `exitValue()`
   before the first `capture(run)`, so the guard there runs only as a plain
   assignment. B11's fix (a fixture forcing `persistTerminal`'s first
   `RunStore.update` to throw once) is a precondition: until it exists,
   replacing the guard with `skippedTests = capture(run)` would pass the
   whole suite unnoticed.
3. **Characterization tests for `TestRunCoordinator`'s `execute()` terminal
   paths — DONE (2026-08-28).** All four are now covered: normal
   completion, the `RuntimeException` catch, the `InterruptedException`
   catch (`interruptedWaitInWaitForPersistsCancelledTerminalRecordAndReleasesLockAndSlot`,
   which also refuted the dossier's O2 concern), and the early-cause return
   (`causeLatchedBeforeWorkerStartsReturnsCancelledWithNothingLaunched`, via
   the injectable worker `ExecutorService` added on branch
   `refactor/coordinator-worker-seam`). The formerly-tracking debt item was
   retired. This was the **hard prerequisite** for item 6 below, which is
   now de-gated.
4. **Skip or cache `JavaSourceScanner.scan()` per module across validator
   calls** (`docs/TECHNICAL_DEBT.md` item B10). Cost: 2-3 passes. Risk:
   medium — needs a design decision (a new per-rule capability flag, or a
   documented process-lifetime cache) before implementation.
5. **Extract the shared validator `Tool` helper** (below) — cost 3-4
   passes, scoped to also close the two safety-net gaps a coverage
   audit found: neither the contract tests nor the functional tests
   assert `ModuleValidationResult.truncated`'s value (`docs/TECHNICAL_DEBT.md`
   item D12), and the contract tests never assert the full
   `moduleResultSchema()` property/type map, only `oneOf` branch count and
   required-field presence. Add one exact-schema-equality assertion per
   tool and one "`ModuleBoundariesTool` never emits `advisoryViolations`"
   assertion as part of this same pass.
6. **Collapse `TestRunCoordinator`'s four `execute()` paths into one
   terminal transition.** Cost: 3-4 passes. Item 3 above (characterization
   tests for all four terminal paths) was its hard prerequisite and is now
   done, so this is de-gated. Risk: high — this is the module's largest and
   most structurally central class; a subtle behavioral change here affects
   run-state correctness for every terminal run.

**Design questions, not scheduled work** (see `docs/TECHNICAL_DEBT.md` for
the full record): whether `ModuleValidationResult.truncated` should be
wired to something real or documented as currently-inert (D12); whether
`execution`/`validation` sibling independence should get its own enforced
rule beyond ARCH-002's cycle-only coverage (C6); whether
`request.environment()`'s unescaped pass-through needs independent
defense regardless of registry contents (D13).

**Not proposed as candidates**, and why: splitting `RegressionMcpServer.java`
for cohesion (a per-tool decomposition found the file's size to be
11 tools' worth of breadth — 3-50 lines each of genuinely tool-specific
code once the shared envelope helpers are reused, not repetition — with one
B3-style exception: `failureSummaryResult` and `readArtifactResult` have
structurally identical bounded-response bodies differing only in the method
name, the size-limit constant (`MAX_FAILURE_SUMMARY_RESPONSE_BYTES` vs
`MAX_ARTIFACT_READ_RESPONSE_BYTES`) and the paired error code and message
(`REPORT_MALFORMED` vs `ARTIFACT_TOO_LARGE`), and collapse to one
parameterised helper — but at roughly nine lines each that pair is too
small to carry a decomposition of the file on its own); anything in
`PublicDiagnosticSanitizer`
(security-critical, no defect found, changing it needs its own
negative-case-test justification pass, not a drive-by); merging the three
rule-*set* files (`ArchitectureRules`/`FrameworkConventionRules`/
`ModuleBoundaryRules` — confirmed to be genuinely distinct rule logic, not
duplication; only a 3-4-line `violation(...)` helper is triplicated
across them, too small to justify a shared utility on its own).

#### Extract the shared validator `Tool` helper

`docs/TECHNICAL_DEBT.md` item B3 names the duplicated methods across
`ModuleBoundariesTool`, `FrameworkConventionsTool`, and `ArchitectureTool`:
`evaluate`, `parseRequest`, `reportOutput`, `moduleResultOutput`,
`violationOutput`, `inputSchema`, `violationSchema`, `moduleResultSchema`,
`outputSchema`, `readOnlyAnnotations`, `successResult`, `errorResult`,
`serialize`. A future refactor could extract a shared
`ValidationToolSupport`-style helper (static methods or a small
package-private class in `com.aqa.mcp.validation`) that each `Tool` class
calls into, leaving only the per-tool rule set and tool name/description
in each `Tool` class itself. Scope carefully: the three classes' `evaluate`
methods differ in which `ValidationRule` list they evaluate against, and
their `outputSchema`/`violationSchema` differ slightly in field sets
(e.g. `FrameworkConventionsTool` and `ArchitectureTool` both support an
`advisoryViolations` bucket that `ModuleBoundariesTool` does not) — a
faithful extraction needs to preserve those differences exactly, verified
against each tool's existing contract tests
(`ModuleBoundariesToolContractTest`, `FrameworkConventionsToolContractTest`,
`ArchitectureToolContractTest`) which lock in current output shape.

#### Scope an ARCH rule for `definitions`-layer assertions

`docs/TECHNICAL_DEBT.md` item B6 notes that `GeneralDefinitions.java`'s
direct AssertJ assertions inside `@Then` methods are real CLAUDE.md debt
with no current validator rule covering it. A future ARCH-005 (or similar)
rule in `ArchitectureRules.java` could flag assertion calls
(`assertThat`/`Assertions.assert*`, matched the same way ARCH-003 already
matches them via each file's own `ImportDeclaration`s) inside classes whose
package's last segment is `definitions`. This would need its own Phase A
design pass: unlike ARCH-003's `pages`/`components` scope, a
`definitions`-layer assertion rule needs to decide whether *any* assertion
in a `definitions` class is a violation, or only ones not delegated from a
`steps`-layer call — that distinction was never designed, only noted as
informational.

## Decisions

### MCP execution scope — regression-petstore-api will not be registered

**Current state.** `regression_start_test_run` supports two modules —
`regression-nextjs-commerce` and `regression-jhipster` — both against the
`dev` environment only. `ExecutionProfileRegistry`
(`regression-mcp-server/src/main/java/com/aqa/mcp/execution/ExecutionProfileRegistry.java`)
holds a two-entry `PROFILES` map (`COMMERCE`, `JHIPSTER`), both with
`environments = List.of("dev")` and `supportsHeadless = true`.
`regression-petstore-api` is the only product module not registered.

**Decision.** `regression-petstore-api` will not be registered as a third
`ExecutionProfile`. This is a recorded decision, not pending work. It
supersedes the earlier framing of "extend execution beyond
`regression-nextjs-commerce`" as an open task.

**Reasons:**
- Its tests run against a shared third-party public sandbox
  (https://petstore.swagger.io/v2), and the module's own documented
  limitation is that the delete flow has no independent fallback cleanup
  if the delete request fails. An MCP-triggered, unattended run is
  exactly the mode in which orphaned data would be left on a system
  nobody here owns. `regression-jhipster` targets a locally owned
  application; `regression-nextjs-commerce` targets a public demo
  storefront without that cleanup gap.
- Registration would force two unresolved design decisions in shared code
  for the sake of one module:
  (i) `supportsHeadless` has no meaningful value for a module with no
  browser, and its real semantics are worse than "not applicable" —
  `TestRunRequestValidator` rejects every request for a profile whose
  `supportsHeadless` is false, so the flag would have to be set `true`
  for a module that has no headless concept at all;
  (ii) `MavenInvocationFactory` appends `-Dcucumber.filter.tags` on every
  invocation, so a tags value supplied by an MCP client would be silently
  ignored and the full suite would run while the client believed it had
  filtered. The module does support filtering, but through JUnit tags via
  `-Dgroups`, a mechanism the MCP tool has no concept of.
- Low payoff. The module already runs correctly under plain Maven (5
  tests, 0 failures, 0 errors, 0 skipped as of 2026-08-21), needs no
  runner class because Surefire auto-discovers plain JUnit 5, and
  registration would add only a remote trigger.
- `regression-mcp-server/README.md`'s v1.0 limitations section already
  records this module as manual-only pending separate explicit
  authorization.

**Remaining mechanical gap**, recorded as a fact rather than a task: the
module's POM declares neither `mcp.surefire.reportsDirectory` nor
`mcp.allure.resultsDirectory`, so its Surefire XML and Allure JSON land in
its own `target/` directories rather than the per-run staging paths
`ReportCapture` reads. This is mechanical and already solved twice — both
properties, plus their Surefire `reportsDirectory` and Allure
`allure.results.directory` usage elements, in `regression-jhipster/pom.xml`
(lines 17-18 and 144/147 as of 2026-08-24) and
`regression-nextjs-commerce/pom.xml` (lines 23-24 and 113/118 as of
2026-08-24). It is not the reason for the decision.

**Conditions for revisiting**, so a future reader knows what would flip
the answer: the module targeting an owned Petstore instance instead of
the public sandbox; the delete-failure fallback cleanup being
implemented, which is already tracked as its own item elsewhere in this
same file (see the "regression-petstore-api" section above); and the
tags semantics being resolved, either by mapping tags to `-Dgroups` for
non-Cucumber profiles or by rejecting tags for them outright.

### Decision: regression-jhipster is not run in CI

**Current state.** `regression-nextjs-commerce` runs in CI as of PR #18
(`.github/workflows/commerce-regression.yml`); `regression-jhipster` does
not.

**Decision.** `regression-jhipster` will not be added to CI for now. This
is a recorded decision, not pending work.

**Reasons:**
- Its suite requires a live application at `localhost:8080`, and no
  compose file, Dockerfile, or image reference exists anywhere in this
  repository — the app under test is raised by a project outside this
  repository, so a CI runner has no way to produce one.
- Building the upstream `jhipster-sample-app` from source on every run was
  considered and rejected: it is an Angular + webpack + Spring Boot build,
  orders of magnitude slower than the test suites it would support, and
  pinning CI to a third-party moving branch would turn unrelated upstream
  changes into red PRs here.

**Note as a fact, not a task:** the module is otherwise CI-ready — its
build does not need the app running, because
`models.jhipster.api.skip.generate` defaults to `true` and the generated
sources are already committed under
`regression-jhipster/src/main/java/com/aqa/jhipster/api/models/generated`.

**Conditions for revisiting**, so a future reader knows what would flip
the answer: an image of the application under test published to a
registry this repository can pull from (a private GHCR package is
sufficient and avoids redistribution questions), pinned by tag or digest,
and verified to match the committed generated models in
`regression-jhipster/src/main/java/com/aqa/jhipster/api/models/generated`.

### Decision: regression-petstore-api is not run in CI

**Decision.** `regression-petstore-api` will not be added to CI. This is
a recorded decision, not pending work.

**Reasons:** the same first reason that already governs this module's
MCP-registration decision (see "MCP execution scope —
regression-petstore-api will not be registered" above) applies here with
more force: its tests hit a shared third-party public sandbox, and the
module documents that its delete flow has no independent fallback cleanup
if the delete request fails. An automatic, unattended CI trigger — on
every push or pull request — is exactly the mode that leaves orphaned
data on a system nobody here owns. See that record for the full
reasoning; it is not repeated here.

**Conditions for revisiting**: the same as that record's — the module
targeting an owned Petstore instance instead of the public sandbox, and
the delete-failure fallback cleanup being implemented.

### The repeated `catch (ExecutionPlanningException)` blocks get no shared helper

**Current state.** `RegressionMcpServer` has six
`catch (ExecutionPlanningException)` blocks — in `startTestRunTool`,
`testSummaryTool`, `failureSummaryTool`, `failureArtifactsTool`,
`readFailureArtifactTool`, and the shared `runActionTool` (which backs both
`regression_get_test_run` and `regression_cancel_test_run`). All six are
identical apart from the exception variable name (`e` in two, `exception`
in four): each body is
`return errorResult(<var>.code(), <var>.getMessage());`. This was
considered as a decomposition candidate; it was never written into the
ranked list above.

**Decision.** No shared catch helper will be extracted. This is a recorded
decision, not pending work.

**Reasons:**
- It cannot be a plain Extract Method. Each catch body is already one
  minimal call to `errorResult`; the repetition is the surrounding
  `try { <tool-specific body> } catch (ExecutionPlanningException e) { … }`
  scaffold, and the try bodies all differ (a different coordinator call and
  a different success wrapping per tool). Factoring the scaffold out needs a
  higher-order method — `guarded(Supplier<CallToolResult> body)` — with each
  handler's try body wrapped in a `Supplier` lambda and passed in: a new
  functional-interface indirection at every call site, not a mechanical
  extraction.
- The line-count payoff is roughly nil. Six one-line catch clauses would be
  replaced by a ~4-line helper plus a `guarded(() -> { … })` wrapper at each
  of the six sites — a net change of a line or two either way, with the
  handlers made slightly less direct to read.
- The deduplication would be half-done. The three `com.aqa.mcp.validation`
  tool classes carry the identical catch shape for `ValidationException`
  (`catch (ValidationException exception) { return errorResult(exception.code(), exception.getMessage()); }`
  in each of `ModuleBoundariesTool`, `FrameworkConventionsTool`,
  `ArchitectureTool`). `ExecutionPlanningException` and `ValidationException`
  are both `final`, both extend `IllegalArgumentException` directly, and
  each declares its own `code()` with no shared supertype or interface; and
  `errorResult` is a different method in `com.aqa.mcp` than in
  `com.aqa.mcp.validation`. A helper could not span both packages without
  either adding a common `code()`-bearing interface to two exception
  classes or passing the accessors in as function arguments, so the result
  would be two near-identical helpers in two packages.
- It is disjoint from `docs/TECHNICAL_DEBT.md` item B3. B3 covers the
  thirteen near-identical *methods* the three `com.aqa.mcp.validation` tool
  classes each reimplement (`evaluate`, `parseRequest`, `reportOutput`,
  `moduleResultOutput`, `violationOutput`, `inputSchema`, `violationSchema`,
  `moduleResultSchema`, `outputSchema`, `readOnlyAnnotations`,
  `successResult`, `errorResult`, `serialize`); it names only those three
  classes and says nothing about `RegressionMcpServer` or its catch blocks.
  Closing B3 would leave `RegressionMcpServer`'s six blocks untouched.
- Elsewhere in the module the same exception is caught for unrelated
  purposes, so there is no larger pattern to unify: `RunStore` has four
  `catch (ExecutionPlanningException exception) { throw exception; }`
  rethrow filters (each sits ahead of a broader `catch (IOException …)` so
  an already-classified exception passes through unwrapped), and
  `TestRunCoordinator.recoverIfUnowned` has one that branches on
  `exception.code()` and sets a recovery flag.

**Conditions for revisiting.** A seventh or eighth
`catch (ExecutionPlanningException)` handler added to `RegressionMcpServer`
with the same one-line body would move the balance; so would introducing a
shared `code()`-bearing interface for the two exception types for an
unrelated reason, which would remove the cross-package obstacle and let one
helper serve both the execution and validation call sites. Absent either,
the blocks stay inline.

## Where things live

**Scope: this section documents only `regression-mcp-server`'s internal
module layout.** The other four reactor modules (`regression-core`,
`regression-petstore-api`, `regression-jhipster`,
`regression-nextjs-commerce`) each document their own structure in their
own README; see `README.md`'s "Maven structure" table for the module list
and "Further reading" for links.

```
regression-mcp-server/
  pom.xml
  README.md                          - install, client config, security model,
                                        execution lifecycle, run store, limits
  docs/TOOLS.md                       - full input/output schema per MCP tool
  src/main/java/com/aqa/mcp/
    RegressionMcpServer.java          - server entry point: builds the MCP
                                         server, registers every tool, wires
                                         each tool's Supplier<Map<String,String>>
                                         module-data dependency
    RepositoryRoot.java,
    RepositoryRootResolver.java       - resolves REGRESSION_ROOT, requires a
                                         root pom.xml, normalizes via
                                         Path.toRealPath()
    ModuleList.java,
    ModuleType.java,
    ModuleTypeClassifier.java         - reads declared reactor modules and
                                         classifies each by CLAUDE.md role
    FeatureDiscovery.java             - Gherkin feature/scenario discovery
                                         (Cucumber parser + Pickle expansion)
    FrameworkOverview.java            - the overview tool's data assembly
    ExecutionPlanningFactory.java     - assembles execution-planning
                                         collaborators for the server

    execution/                        - the three execution tools' supporting
                                         code: request validation
                                         (TestRunRequestValidator,
                                         ExecutionProfile,
                                         ExecutionProfileRegistry), the
                                         direct-launcher process path
                                         (DirectMavenProcessLauncher,
                                         MavenInvocationFactory,
                                         MavenRuntimeConfiguration[Loader]),
                                         process ownership/cleanup
                                         (ProcessOwnershipTracker,
                                         OwnedProcessIdentity,
                                         ObservedProcess/SystemProcessView),
                                         the run store and lifecycle
                                         (RunStore, RunId, RunSnapshot,
                                         TestRunState, TestRunCoordinator),
                                         and result capture/reporting
                                         (ReportCapture, SurefireSummaryParser,
                                         AllureResultParser,
                                         PublishedReportIndex, ArtifactContent,
                                         PublicDiagnosticSanitizer)

    validation/                       - the three architecture/convention
                                         validator tools: rule profiles and
                                         resolution (RuleProfile,
                                         ModuleProfile, RuleProfileResolver),
                                         AST scanning (JavaSourceScanner,
                                         SourceUnit, BasePackages), the shared
                                         rule/report model (ValidationRule,
                                         EvaluationContext, Violation,
                                         ModuleValidationResult,
                                         ValidationReport), the three fixed
                                         rule lists (ModuleBoundaryRules,
                                         FrameworkConventionRules,
                                         ArchitectureRules — each
                                         package-private, consumed only by
                                         its own Tool class), and the three
                                         public Tool classes
                                         (ModuleBoundariesTool,
                                         FrameworkConventionsTool,
                                         ArchitectureTool)

  src/test/java/com/aqa/mcp/
    execution/, validation/           - mirror the main-source packages
                                         above; includes STDIO
                                         integration/contract tests
                                         (e.g. RegressionMcpServerStdioIntegrationTest)
                                         that process-test the server's
                                         actual JSON-RPC stdout behavior
```

New tools are registered in `RegressionMcpServer.java` following the
existing additive pattern: build a `SyncToolSpecification` via the tool
class's own static `tool(...)` factory method, passing in whatever
`Supplier<Map<String,String>>` or `Path repositoryRoot` the tool needs
resolved fresh per request (never cached at server-startup time — see the
"per-request, not eager" pattern already used by every existing tool).
