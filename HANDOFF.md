# Handoff

A living file so a fresh agent session — any vendor, no conversation
history — can resume work immediately. Read this after `CLAUDE.md` at the
start of a session; update it at the end of one, per `CLAUDE.md`'s
"Verification and handoff" rules.

## Current state

- `regression-mcp-server` shipped as v1.0.0, tag `regression-mcp-server-v1.0.0`
  (commit `367fe27`).
- Build: green. Each figure below carries the date it was actually
  measured; they are point-in-time, not a standing guarantee — re-run
  rather than trust a figure once its date is more than a few sessions
  old:
  - `mvn -pl regression-mcp-server -am test` — Tests run: 280, Failures: 0,
    Errors: 0, Skipped: 5 (measured 2026-08-31 on `master` at `18064cf`,
    identical before and after the `ToolSchemas` extraction; the 278
    figure measured 2026-08-28 predates the
    `fix/list-scenarios-repository-error` merge, which added two contract
    assertions, and 276/272/275 are older still). All 5 skips are
    environment-conditional:
    each is a symlink-escape defence test
    (`ReportCaptureTest.rejectsSymlinkEscapesAndCleansOwnedStaging`,
    `RunStoreTest.symlinkedStatusTargetIsRejectedWithoutFollowingIt`,
    `FeatureDiscoveryTest.rejectsSymlinkedFeatureFilesThatEscapeTheFeatureRoot`,
    `ModuleListTest.rejectsSymlinkedModulePathsEscapingTheRepositoryRoot`,
    `JavaSourceScannerTest.rejectsSymlinkedSourceFilesThatEscapeTheSourceRoot`)
    that aborts via a JUnit assumption because the local Windows account
    cannot create symbolic links. All 5 run on the Linux CI runner, where
    `main.yml`'s "Require all MCP security tests to execute" step fails the
    build if any is skipped.
  - `mvn -pl regression-jhipster -am test -Dcucumber.filter.tags="@api" -Denv=dev`
    (measured 2026-08-21) — 16 Scenarios (16 passed), 37 Steps (37 passed);
    Surefire Tests run: 21, Failures: 0, Errors: 0, Skipped: 5.
  - `mvn -pl regression-jhipster -am test -Dcucumber.filter.tags="@ui" -Denv=dev`
    (measured 2026-08-21) — 5 Scenarios (5 passed), 24 Steps (24 passed).
  - `mvn -pl regression-jhipster -am test -Dcucumber.filter.tags="@hybrid" -Denv=dev`
    (measured 2026-08-21) — 2 Scenarios (2 passed), 15 Steps (15 passed).
  - `regression-nextjs-commerce` has 3 Cucumber scenarios on disk since
    commit `a691978` (two in
    `regression-nextjs-commerce/src/test/resources/features/catalog_search.feature`,
    one in `.../cart_management.feature`) — not the 2 recorded here on
    2026-08-21. The suite was not re-run on 2026-08-27, so the step count
    and pass/fail are not re-measured; the last recorded run predates the
    third scenario.
  - `mvn validate` (measured 2026-08-27) — BUILD SUCCESS. The reactor
    summary shows six rows: the
    aggregator/parent POM `regression` plus its five modules
    (`regression-core`, `regression-petstore-api`, `regression-jhipster`,
    `regression-nextjs-commerce`, `regression-mcp-server`).
  The `regression-jhipster` runs require its live app at `localhost:8080`
  to be reachable; confirmed via `curl` (HTTP 200) immediately before
  running.
- All 14 `regression-mcp-server` MCP tools (4 discovery, 3 execution, 4
  report/artifact, 3 architecture-validator) are implemented and documented
  in `regression-mcp-server/docs/TOOLS.md`. A real, verbatim client session
  against `regression-nextjs-commerce` was recorded 2026-08-22 and published
  as `regression-mcp-server/docs/SESSION_DEMO.md`. That recording is a
  2026-08-22 snapshot and is now stale in two confirmed respects: the
  `RunSnapshot.skippedTests` field did not exist yet, so it is absent from
  every run-status response shown, and the commerce scenario count has since
  changed (see the build bullet above). Its
  `regression_get_failure_artifacts` count has not been re-verified.
- `regression-nextjs-commerce` runs in CI on every push and pull request to
  `master` that touches it (`.github/workflows/commerce-regression.yml`),
  including a reachability pre-flight against the public demo store and
  `if: always()` artifact upload of Surefire/Allure output.
- `regression-nextjs-commerce`'s Allure report is published to GitHub Pages
  on every push to `master` that touches the module (the same workflow's
  Restore/Generate/Publish steps), landing in the `/commerce/` subdirectory
  of the `gh-pages` branch via explicit `git` commands, no third-party
  publishing action. Live at
  https://zhabieiev.github.io/aqa-regression-framework/commerce/. Allure
  trend history is restored from `gh-pages` before each generation and
  republished with the report, so the trend accumulates across runs instead
  of resetting each time — proven in CI, not only locally, by a second real
  publish (`history-trend.json` went from 1 to 2 data points, with two
  genuinely distinct CI runs' data merged, not one run duplicated). See the
  2026-08-23 session entry below for the full verification trail.
- Commerce's `allure-maven` plugin uses `reportVersion` 2.39.0, not
  `allure.version`'s 2.35.3: `allure-bom` (the test adapters) and
  `allure-commandline` (the report renderer the plugin downloads) are
  independently published artifacts whose version sets do not coincide, and
  `allure-commandline:2.35.3` does not exist on Maven Central at all. Do not
  set `allure.report.version` to whatever `allure.version` happens to be —
  see `docs/TECHNICAL_DEBT.md` item B2.
- No dedicated Allure-distribution cache exists in CI for commerce, and none
  is currently justified: `actions/setup-java`'s existing `cache: maven`
  already caches `~/.m2/repository`, where the distribution installs, so
  only the first run after any `pom.xml` change pays the cold cost (9.2s
  Maven-reported for install+generate; 3.9s once the cache is warm).
- `main.yml`'s `build-and-test` job's Maven step no longer runs with
  `continue-on-error: true` — it is now a real gate that fails the job when
  `regression-core` breaks.
- `regression-nextjs-commerce`'s dead Allure attachment fixture
  (`attachment.feature`, `AllureAttachmentFixtureSuite`,
  `AllureAttachmentFixtureSteps`, and the Surefire property that existed
  solely to feed it) was removed; it never actually ran under any real
  invocation.
- Known debt and open questions: see [`docs/TECHNICAL_DEBT.md`](docs/TECHNICAL_DEBT.md)
  (the item count and its per-section breakdown are stated in that file's
  own introductory prose now, counted from its own `###` headers; this
  bullet no longer carries a figure, because a count kept here — in a
  different file from the thing it counts — has drifted before: this
  bullet had drifted to "20 items as of 2026-08-25" when the file actually
  held 23 at that date, a staleness caught and corrected during the
  2026-08-27 session below; its "32 as of 2026-08-28" figure held until
  **D15** and **A4** were both added on the `refactor/extract-tool-schemas`
  branch (2026-08-31), reaching 34. Item **B8** — the two untested
  `TestRunCoordinator.execute()` terminal paths — was retired on 2026-08-28
  once characterization tests covered both (its identifier is not reused);
  item **A4** — the unused `import java.nio.file.Path;` — was retired on
  2026-09-01 when the import was removed on the
  `refactor/merge-error-result` branch (its identifier is not reused
  either).
  Grouped into four sections by the
  action each calls for — A. Defects: fix, or accept with a stated reason;
  B. Debt: schedule; C. Accepted characteristics: no action, each with a
  stated review trigger; D. Open questions: closed by observation, not
  work). Items are identified as a section letter plus number (e.g. `B3`),
  not a flat list; position within a section carries no priority meaning —
  priority is read from each item's Cost field. Per-module limitations are
  maintained with their own modules' READMEs, not duplicated in that file.
- `regression-mcp-server` now has a committed architecture map and test
  map: [`regression-mcp-server/docs/ARCHITECTURE.md`](regression-mcp-server/docs/ARCHITECTURE.md)
  (layer map, a class inventory — its count and per-package breakdown
  stated in `ARCHITECTURE.md` itself — with tier/fan-in/contract-exposure
  bucket per class, three flow walkthroughs, lifecycle/ownership, data
  model, boundary/trust surface, extension points, and a
  leaves-first/hubs-last review order) and
  [`regression-mcp-server/docs/TEST_MAP.md`](regression-mcp-server/docs/TEST_MAP.md)
  (every test file's type, what it pins, and — the load-bearing column —
  what change would pass the whole suite unnoticed), both maintained
  against `master` — see `ARCHITECTURE.md`'s baseline note for the commit
  they were last fully verified against. The per-class dossier
  directory (`regression-mcp-server/docs/classes/`) is now started: the
  first dossier is
  [`regression-mcp-server/docs/classes/TestRunCoordinator.md`](regression-mcp-server/docs/classes/TestRunCoordinator.md)
  (merged, PR #35). The review order in `ARCHITECTURE.md` puts
  `TestRunCoordinator` last-but-one deliberately — it was taken first this
  time by explicit instruction, ahead of the classes it depends on. Its
  three follow-up PRs (all merged) — #36 (the `InterruptedException`
  characterization test, which refuted the dossier's concern O2), #37 (the
  injectable worker `ExecutorService` seam), #38 (the early-cause
  characterization test) — mean all four of `execute()`'s terminal paths are
  covered; the dossier, `ARCHITECTURE.md` and `TEST_MAP.md` were reconciled
  to match.
- `regression-jhipster`'s Playwright trace-capture gap (traces written to
  `target/playwright/traces/` are never surfaced through the MCP server) is
  now logged as item C2 in `docs/TECHNICAL_DEBT.md`, rather than only noted
  here as an earlier version of this file did.
- Forward-looking roadmap: see [`docs/ROADMAP.md`](docs/ROADMAP.md)
  (reactor-wide, grouped by module).

## Most recent session

2026-09-09 — fixed the execute() skipped-count guard test so it reaches the
interleaving it was written to prove, branch
`fix/b11-skipped-count-guard-test`, PR #53. Three commits:
`regression-mcp-server/src/test/java/com/aqa/mcp/execution/TestRunCoordinatorTest.java`;
then `docs/TECHNICAL_DEBT.md`; then `docs/ROADMAP.md` with `HANDOFF.md`. No
production source, POM, or CI file touched.

**The test change.** `secondCaptureCallInTheRuntimeExceptionPathDoesNotOverwriteTheFirstCallsSkippedCount`'s
fixture `ExitValueFailsOnceProcess` now throws on the second `exitValue()`
call — an `AtomicInteger` counter replaces the `AtomicBoolean` once-flag —
instead of the first. The second call is `persistTerminal`'s `exitCode`
computation on the normal-completion tail; throwing there, after the
try-block `capture(run)` has already returned a real skipped count, produces
the interleaving: `catch (RuntimeException)` re-runs `capture(run)`, which
returns null because the first capture moved the persisted capture status off
`PENDING`, and the guard `if (captured != null) skippedTests = captured;`
keeps the first count. The old fixture threw on the first `exitValue()` call,
which is `execute()`'s `terminal = process.exitValue() == 0 ? PASSED :
FAILED` — before the try-block capture — so the catch's capture was the only
one and the guard ran as a plain assignment.

**Why `terminal.state()` is now PASSED, not ERROR.** The throw is downstream
of `persistTerminal`'s first statement `firstCause(run, PASSED)`, which
CAS-latches `run.cause = PASSED`. When `catch (RuntimeException)` retries
`persistTerminal` with `firstCause(run, ERROR)`, that call reads the
already-latched PASSED and persists PASSED. The state assertion is updated to
PASSED with an inline comment; the two `skippedTests()` assertions are
unchanged and are now load-bearing — with the guard replaced by a plain
`skippedTests = capture(run)`, the test fails on `terminal.skippedTests()`
(null vs 1), not on the state assertion, verified by corrupting the guard and
running the test in isolation. Full module suite unchanged.

**Debt catalogue.** `docs/TECHNICAL_DEBT.md` item B11 is retired; the
identifier is not reused. The item count and per-section breakdown in the
introductory prose were recomputed from the file's own `###` headers (the
count lives only in that file). D10's three references to B11 — the deferral
paragraph, the "Closed by observation" note, and the Location line — are
reworded to be self-contained; D10 stays, tracking the separate
`recoverIfUnowned` guard, and B14's illustrative list drops the retired B11.
`docs/ROADMAP.md`'s capture-guard-extraction candidate no longer names B11 as
an open precondition — the extraction's merge-vs-overwrite behaviour is now
pinned by the fixed test.

**Correcting the prior inspection.** The read-only inspection that preceded
this pass concluded the fixture change could keep the existing `state ==
ERROR` assertion. That was wrong: it missed that `persistTerminal`'s first
statement latches `run.cause` before the throw, so any throw inside
`persistTerminal` makes the catch-path retry persist the latched (non-ERROR)
cause. Reaching the interleaving necessarily makes the state PASSED.

This entry follows `CLAUDE.md`'s `## Documentation upkeep`: PR #53 is named by
number only, no live marker, the debt count is left to its owning file, and
references are structural.

`mvn -pl regression-mcp-server -am test`: 280 / 0 / 0 / 5.

2026-09-09 — corrected four `regression-mcp-server/docs/TOOLS.md` claims and
retired the two debt items that tracked them, branch
`docs/tools-error-and-bounds-corrections`, PR #52. Four commits:
`regression-mcp-server/docs/TOOLS.md`; then `docs/TECHNICAL_DEBT.md` with
`regression-mcp-server/docs/TEST_MAP.md` and
`regression-mcp-server/docs/classes/TestRunCoordinator.md`; then
`docs/ROADMAP.md`; and `HANDOFF.md` in this one. No production source, test,
POM, or CI file touched.

**The four `TOOLS.md` corrections.** (1) `regression_start_test_run`'s
"Read-only" line said "not open-world"; the code builds it with the
open-world annotation `true` (`RegressionMcpServer.startTestRunTool` via
`executionAnnotations`) and
`RegressionMcpServerStdioIntegrationTest.assertExecutionToolContracts`
asserts that — corrected to "open-world". (2) A `runId` that is a string but
not `run-<32 hex>` returns `INVALID_ARGUMENTS` from the four report/artifact
tools and `RUN_NOT_FOUND` from `regression_get_test_run` /
`regression_cancel_test_run`; a well-formed-but-unknown `runId` returns
`RUN_NOT_FOUND` from all six. The doc documented only `RUN_NOT_FOUND` for the
report/artifact tools — added to the report/artifact preamble and the
"Common error codes" list. (3) `ARTIFACT_TOO_LARGE` is also returned when the
artifact payload cannot be serialized at all, not only when it exceeds the
2 MiB cap. (4) The 96 KiB and 2 MiB caps are checked against the serialized
`{status,data}` payload alone, not the full wire response, which carries that
payload again as `structuredContent` plus JSON-RPC framing — the "total
serialized response" wording was replaced with what the check measures. No
derived raw-artifact-size figure was introduced.

**Debt items retired.** `docs/TECHNICAL_DEBT.md` items A3 (the openWorldHint
doc mismatch) and D14 (the malformed-`runId` code divergence, undocumented)
were both closed by the `TOOLS.md` corrections and removed; their identifiers
are retired, not reused, and the identifier-example enumeration in the intro
no longer lists A3, as it already omits the retired A2. The item count and
per-section breakdown in that file's introductory prose were recomputed from
its own `###` headers; the count lives only in that file. Inbound references
were made self-contained rather than left pointing at a gap: B13's
"Relationship to B12 and D14" section and its malformed-`runId` sentence, the
`RegressionMcpServerStdioIntegrationTest` row in
`regression-mcp-server/docs/TEST_MAP.md`, and O6 / the H4 row / the §7
cross-reference in
`regression-mcp-server/docs/classes/TestRunCoordinator.md`. Historical dated
`HANDOFF.md` entries that mention A3 or D14 are left as the record of the
sessions that logged them, as retired A2 / B1 / B8 are treated there.
`docs/ROADMAP.md`'s ranked entry for the openWorldHint fix is marked done in
place with its number kept, following the already-done characterization-tests
entry and avoiding disturbance to the list's and `HANDOFF.md`'s position
references.

**Read-only inspection finding, not scheduled or rejected work.** The
terminal-state guard for the four report/artifact tools is not implemented in
`RegressionMcpServer` — its handlers delegate it; the authoritative
"is this run terminal" decision is made in `RunStore.readSummary` and
`RunStore.terminalRecordForArtifacts`, with in-memory pre-checks in
`TestRunCoordinator.summary` / `failureSummary` / `artifacts` / `readArtifact`
— so no `requireTerminal`-style extraction at the server layer is available.
Separately, a read-only cross-check noted that `docs/ROADMAP.md`'s ranked
entry for collapsing `TestRunCoordinator`'s four `execute()` paths calls
itself de-gated because its characterization-test prerequisite is done, while
`docs/TECHNICAL_DEBT.md` item B11 (the `execute()` skipped-count overwrite
guard, still unproven by any test) and the dossier's §12 both name B11 as a
precondition a collapse must not break; the two are not strictly contradictory
— they concern different things, path coverage versus one guard — but the
"de-gated" phrasing omits B11 as a residual precondition. Left for a later
authorized pass; no document was changed for it.

This entry follows `CLAUDE.md`'s `## Documentation upkeep` rules: it names
PR #52 by number without asserting its status, carries no live marker, points
at `docs/TECHNICAL_DEBT.md` as the count's owner without restating the figure,
and uses structural references throughout.

`mvn validate`: BUILD SUCCESS.

2026-09-08 — closed the documentation arc: added a `## Documentation
upkeep` section to `CLAUDE.md` and recorded the historical-trailer decision
in `docs/ROADMAP.md`'s "## Decisions", branch
`docs/upkeep-rule-and-history-decision`, PR #51. Two files in the first
commit (`CLAUDE.md`, `docs/ROADMAP.md`) and `HANDOFF.md` in this one; no
production source, test, POM, CI, or `docs/TECHNICAL_DEBT.md` file touched,
and no debt item added or removed.

**`CLAUDE.md` `## Documentation upkeep`.** Seven rules, each codifying a
failure seen in this repo's own doc set during the arc: one fact lives in
one file and others reference it; reference a PR by number only and never
its status; no live marker inside a dated entry; cite structurally, not by
line number; write paths in full from the repository root; a stated
baseline or "last verified" line is moved forward by whatever pass
re-verifies the document; grep before finishing to prove a touched count
is asserted once. A closing sentence states the section does not widen any
task's authorized file scope. Placed after "Verification and handoff",
before "Regression MCP server"; overlap with the existing HANDOFF-update,
`output.log`, structural-citation and inspection-scope rules was checked
and the new text extends or references them rather than restating them.

**`docs/ROADMAP.md` decision.** 35 commits reachable from `master`
(2026-08-17 to 2026-08-29) carry a `Co-Authored-By` trailer, all predating
`d37c919`, the commit that added the attribution rule; no committed file
carries one (`git grep` over `git ls-files`). They will not be rewritten:
they are merged into `master`, the committed docs cite 37 distinct commit
hashes that a rewrite would dangle, the `regression-mcp-server-v1.0.0` tag
points at one of the affected commits, and the trailers predate the rule
and mislead nobody. The prohibition stands for new commits and files.

**Housekeeping.** The stray empty local branch
`docs/strip-attribution-strings`, left by the prior no-findings
attribution pass and never pushed, was deleted with `git branch -d`.

This entry is the first written under the new `## Documentation upkeep`
rule and follows it: it names PR #51 by number without asserting its
status, carries no `(latest)` marker, points at the debt catalogue's owner
file without restating its count, and uses structural references
throughout.

`mvn validate`: BUILD SUCCESS.

2026-09-08 — refreshed mechanical staleness in the `regression-mcp-server`
doc set and logged the deferred structural revision, branch
`docs/dossier-number-refresh`, PR #50. Four files in the first commit
(`docs/TECHNICAL_DEBT.md`, `regression-mcp-server/docs/TEST_MAP.md`, and
the two class dossiers `TestRunCoordinator.md` and `ToolSchemas.md`) and
`HANDOFF.md` in this one; no production source, test, POM, or CI file
touched. `regression-mcp-server/docs/ARCHITECTURE.md` needed no change.

**Numbers checked against the tree first, then corrected only where
stale.** Corrected: `ToolSchemas.md`'s `RegressionMcpServer.java` line
count (427 → "427 when this dossier was written; 428 now", after the
two-argument `successResult` overload); `TestRunCoordinator.md`'s suite
figure (278/0/0/5 → 280/0/0/5). Left as already-correct: `TEST_MAP.md`'s
48/42/6 file split and the 448/457 rule-set-test line counts;
`TestRunCoordinator.md`'s own 425-line and 21-`@Test` counts;
`ToolSchemas.md`'s 139-line count; `ARCHITECTURE.md`'s 67-class count, its
11/35/21 per-package counts, and its review-order groups
(24/19/10/6/3/3/1/1, which sum to 67).

**Retired-identifier citation made self-contained.** `TestRunCoordinator.md`'s
commit-walk entry for `75adf49` quoted that commit's subject, which names
the retired item A2; the passage now glosses A2 in the dossier's own words
(a run whose Cucumber tag expression matched nothing terminating as
`PASSED` with no visible signal, since retired) so a reader need not look
it up. The surrounding structural claim about that commit does not depend
on A2 and was left as-is.

**B13 cross-reference added** to `TEST_MAP.md`'s
`RegressionMcpServerStdioIntegrationTest` row — `regression_get_failure_summary`'s
`catch (ExecutionPlanningException)` is entered by no test — in the same
form the row already uses for item A3.

**The substantive revision was deferred**, and is logged as new
`docs/TECHNICAL_DEBT.md` section-B item **B14**: `ARCHITECTURE.md`,
`TEST_MAP.md` and the two dossiers have had their numbers reconciled
piecemeal but their substance — dependency map, tier/fan-in/bucket
assignments, "what would pass unnoticed" judgements, the dossiers'
structural claims — never re-verified against `master` since the
2026-08-27 baseline `ARCHITECTURE.md` still names. B14 records the drift
already visible (bare `execute()` line numbers in `TestRunCoordinator.md`
shifted since the class grew from 418 to 425 lines; the bare
`docs/TOOLS.md` path in `TEST_MAP.md` and `TestRunCoordinator.md`),
requires the revision to move `ARCHITECTURE.md`'s baseline note forward,
and carries a 3-4-pass cost estimate.

`mvn validate`: BUILD SUCCESS.

2026-09-08 — corrected drifting live claims across the doc set, branch
`docs/live-claims-cleanup`, PR #49. Four files in the first commit
(`HANDOFF.md`, `docs/ROADMAP.md`, `docs/TECHNICAL_DEBT.md`,
`regression-mcp-server/docs/ARCHITECTURE.md`) and `HANDOFF.md` again in
this one; no production source, test, POM, or CI file touched.

**Class count.** The "## Current state" architecture-map bullet said
"66-class inventory"; `find regression-mcp-server/src/main/java -name
'*.java'` returns 67 (root 11, execution 35, validation 21), and
`ARCHITECTURE.md`'s "Class inventory" section already says 67. The number
was removed rather than corrected in place — a count kept away from the
file that owns it is what drifted — and the bullet now points at
`ARCHITECTURE.md`'s own figure.

**Anchor claim.** `ARCHITECTURE.md`'s "Anchor commit" line implied the
whole document was frozen at `7107c49f`. Verified stale: `ToolSchemas.java`
did not exist at that commit and the tree held 66 `.java` files there, not
the 67 the document now describes. One line was reworded to state what it
means — maintained against `master`, last fully verified at that commit
(2026-08-27), reconciled piecemeal since — with no other `ARCHITECTURE.md`
line touched (its substantive re-verification is a later pass's). The
hard-coded `7107c49f` hash in the live sections of `HANDOFF.md` and
`docs/ROADMAP.md` was replaced with a structural pointer to that baseline
note; the two occurrences inside dated `HANDOFF.md` entries were left as
history.

**Candidate 2 body.** `docs/ROADMAP.md`'s capture-guard-extraction
candidate said the block is "duplicated at all four call sites inside
`execute()`/`recoverIfUnowned()`". Re-checked: all four sites are in
`execute()`; `recoverIfUnowned` has a fifth, differently-shaped occurrence
(a `captured != null ? captured : snapshot.skippedTests()` ternary passed
inline to `replaceWithReason`, over a `String runId` and a `RunSnapshot`,
with no `Active run` and no `Integer` local), which the proposed
`captureOrKeep(Active run, Integer current)` signature does not fit. The
body now records the execute()-only-versus-generalised choice as open; the
Risk field is unchanged.

**D10 Location.** `docs/TECHNICAL_DEBT.md` item D10's Location field cited
`execute()` line numbers that had rotted; they were replaced with a
structural citation (the method plus the statement form), consistent with
the file's citation rule. Nothing else in D10, and no item counter,
changed.

**"(latest)" marker.** Removed from the one dated entry that carried it.
The newest entry is already the topmost, so the marker was a live claim
inside a dated entry that every later pass had to reach back and maintain.

`mvn validate`: BUILD SUCCESS.

2026-09-08 — reconciled `docs/ROADMAP.md` against `docs/TECHNICAL_DEBT.md`
item B11 and logged a rejected refactor, branch
`docs/roadmap-risk-and-decisions`, PR #48. Only `docs/ROADMAP.md` changed
in the first commit and `HANDOFF.md` in this one; no production source,
test, POM, or CI file touched.

**Candidate-2 Risk field corrected.** The `regression-mcp-server` ranked
candidate for extracting `TestRunCoordinator`'s skipped-count capture guard
rated its risk "low" on the strength of
`TestRunCoordinatorTest.secondCaptureCallInTheRuntimeExceptionPathDoesNotOverwriteTheFirstCallsSkippedCount`
pinning the merge-vs-overwrite behaviour. Re-traced against the current
`TestRunCoordinator.execute` and the
`SingleSkippedTestReportThenExitValueFailureOnceLauncher` /
`ExitValueFailsOnceProcess` fixture: the fixture throws from the first
`process.exitValue()` call, which computes `terminal` before the try-block
`capture(run)`, so the `catch (RuntimeException)` block's `capture(run)` is
the first and only capture and the guard runs as a plain assignment — the
interleaving is never exercised, as `docs/TECHNICAL_DEBT.md` item B11
establishes. The whole test tree was searched for any other test pinning
the behaviour; none does. The Risk field now states that the refactor is
currently unprotected and that B11's fix is a precondition.

**Rejected refactor recorded.** `docs/ROADMAP.md`'s "## Decisions" section
gained a record for a shared helper for `RegressionMcpServer`'s six
`catch (ExecutionPlanningException)` blocks — considered and rejected. No
ROADMAP candidate had proposed it, so it is written as a
considered-and-rejected refactor, not the closing of a listed candidate.
The reasoning, verified this pass: the six blocks are identical apart from
the exception variable name; extraction needs a `Supplier`-based
higher-order method, not a plain Extract Method; the net line-count effect
is roughly nil; the three `com.aqa.mcp.validation` tool classes carry the
same shape for `ValidationException`, and the two exception types share no
`code()`-bearing supertype across their two packages, so a helper would be
half-done; it is disjoint from `docs/TECHNICAL_DEBT.md` item B3 (which
covers duplicated methods in the validator classes only); and `RunStore`'s
four rethrow-filter catches and `recoverIfUnowned`'s `code()`-branching
catch are unrelated.

**Duplicated-bodies claim fixed.** ROADMAP's "not proposed as candidates"
paragraph asserted a per-tool decomposition found no B3-style duplicated
method bodies; `RegressionMcpServer.failureSummaryResult` and
`readArtifactResult` are in fact a duplicated bounded-response pair,
structurally identical apart from the method name, the size-limit constant,
and the two error code/message strings. The sentence now names the pair and
its differences, kept as an exception too small to change the "not
proposed" verdict.

**Path shorthand.** The ranked list's bare `docs/TOOLS.md` was corrected to
`regression-mcp-server/docs/TOOLS.md`. The `docs/TOOLS.md` inside the
"Where things live" ASCII tree is tree-relative (the tree is rooted at
`regression-mcp-server/` and every sibling entry is bare) and was left
unchanged.

`mvn validate`: BUILD SUCCESS.

2026-09-08 — reconciled `docs/TECHNICAL_DEBT.md`'s D10/B11
contradiction, added section-B item **B13**, and corrected the
`docs/TOOLS.md` path shorthand, branch `docs/debt-d10-b13-paths`, PR #47.
Only `docs/TECHNICAL_DEBT.md` changed in the first commit and `HANDOFF.md`
in this one; no production source, test, POM, or CI file touched.

**D10 / B11 reconciled.** D10 (`recoverIfUnowned`'s skipped-count guard is
untested) had carried a passage asserting that the analogous
`execute()`-side guard "does have a dedicated test", and describing what
that test does. Item B11 already establishes the opposite: the test named
for it,
`TestRunCoordinatorTest.secondCaptureCallInTheRuntimeExceptionPathDoesNotOverwriteTheFirstCallsSkippedCount`,
throws from the first `process.exitValue()` call — which computes
`terminal` before the try-block `capture(run)` runs — so the
`catch (RuntimeException)` block's `capture(run)` is the first and only
capture and `if (captured != null)` executes as a plain assignment; the
guard interleaving is never reached. Re-traced against the current
`TestRunCoordinator.execute` and the
`SingleSkippedTestReportThenExitValueFailureOnceLauncher` /
`ExitValueFailsOnceProcess` fixture this pass. D10's passage was removed;
D10 now defers to B11 for the `execute()`-side guard and keeps only its
own `recoverIfUnowned` question, and B11's "Relationship to D10" was
reworded to read correctly against the trimmed D10.

**B13 added** to section B: no test drives `regression_get_failure_summary`
into its `catch (ExecutionPlanningException)`. `regression-mcp-server/src/test`
was searched for the tool name, the `failureSummaryTool` handler method,
the `GET_FAILURE_SUMMARY_TOOL_NAME` constant and the bare `failureSummary`
token; the only handler invocation is one success-envelope assertion in
`RegressionMcpServerStdioIntegrationTest.servesFailureArtifactToolsForARealFailingRunAndRejectsForeignRequests`,
alongside a contract-only spec test
(`RegressionMcpServerContractTest.exposesTheClosedReadOnlyFailureSummaryContract`)
that never calls the handler. The five sibling report / run-status /
execution handlers each have their catch reached by that STDIO test. The
catch is reachable through the closed schema — a schema-valid `runId` that
is not `run-<32 hex>` raises `INVALID_ARGUMENTS` (the malformed-`runId`
path item D14 describes) and a well-formed but unknown `runId` raises
`RUN_NOT_FOUND`, both already exercised against sibling tools — so B13's
Cost is 1 pass.

**Path shorthand.** Six bare `docs/TOOLS.md` references in items A3, D12
and D14 were corrected to `regression-mcp-server/docs/TOOLS.md`; there is
no repository-root `docs/TOOLS.md`. The same shorthand remains in
`docs/ROADMAP.md` (two occurrences, one inside a `regression-mcp-server/`-
rooted tree) and in three dated `HANDOFF.md` entries, left for later
passes.

The introductory item counter in `docs/TECHNICAL_DEBT.md` was updated for
the new section-B item; that count and its per-section breakdown continue
to live only in that file's introductory prose.

`mvn validate`: BUILD SUCCESS.

2026-09-08 — logged `docs/TECHNICAL_DEBT.md` section-B item
**B12**, branch `docs/text-representation-untested`. Only
`docs/TECHNICAL_DEBT.md` and `HANDOFF.md` changed, across the branch's
commits; no production source, test, POM, or CI file touched.

**B12: no test asserts anything about a tool response's text
representation.** Every MCP tool response `RegressionMcpServer` builds
carries its payload twice — as the `structuredContent` object and as a
JSON string in the `content` text block (`successResult` for a success,
`errorResult` for the error envelope) — and no `regression-mcp-server`
test makes any assertion about the text block. Every response assertion in
the module reads `structuredContent` or `isError`
(`RegressionMcpServerStdioIntegrationTest` via
`.path("result").path("structuredContent")…`,
`RegressionMcpServerContractTest` and the validator `*ToolTest` classes
via `result.structuredContent()`). The evidence is the 2026-09-08
single-serialization pass: corrupting the shared two-argument
`successResult` overload so the text block disagreed with
`structuredContent` left the full suite green at 280 / 0 / 0 / 5, and that
overload is the one helper every successful response passes through.
Before that pass `successResult` built both representations from the same
map so they could not diverge; the two-argument overload takes the text as
a caller-supplied parameter, so their agreement is now a caller
responsibility. Both current callers pass text from the same map they hand
to `structuredContent`, so B12 is a missing guard, not a defect. Cost:
1 pass — a tree-compare assertion on both a successful and an error
response, added to the existing STDIO integration test. The identifier is
the next free one in section B (B1 and B8 are retired and not reused). The
introductory item count went 33 → 34 (B 9 → 10).

A follow-up commit on the same branch sharpened B12's Evidence field to
separate the two grounds the item rests on. The success-path claim is
backed by an executed experiment — the corrupted shared two-argument
`successResult` overload, full suite green. The error-envelope claim is
not: `RegressionMcpServer.errorResult` was never part of that experiment,
and the ground there is only the absence of any test-tree reference to a
response's text representation (`CallToolResult.content()`, `TextContent`,
a `content[]` text node, a `"text"` field), which is weaker evidence than
an executed experiment. The Fix field now notes that closing B12 likely
needs the tree-compare on both a successful and an error response, since
the two envelopes are built by different helpers (`successResult` versus
`errorResult`); the Cost estimate is unchanged at 1 pass, and What, Why,
Location and the item count were not touched.

`mvn validate`: BUILD SUCCESS.

2026-09-08 — removed the redundant response serialization in
`RegressionMcpServer.failureSummaryResult` and
`RegressionMcpServer.readArtifactResult`, branch
`refactor/single-serialization-bounded-results`. One source file changed
(`regression-mcp-server/src/main/java/com/aqa/mcp/RegressionMcpServer.java`)
in the first commit, `HANDOFF.md` in the second; no POM, test, or CI file
touched:

**What changed and why.** Each of the two bounded-response methods
serialized the response map twice on every successful call: once in the
method itself to measure the payload's UTF-8 byte length against its limit
constant, then again inside `successResult` to build the `TextContent`
string. A new two-argument `successResult(Map, String)` overload now takes
the already-serialized text; the one-argument `successResult` delegates to
it by calling `serialize` itself, so every other caller is unaffected.
Each method now hoists `serialize(output)` into a local, checks the byte
length of that local, and passes the same local to the overload — one
`serialize` call per successful call instead of two.

**Scope of the redundancy.** It was exactly one extra `serialize` per
method per successful call, not the "triple serialization" an earlier
inspection note had floated. Within `RegressionMcpServer` the count goes
from two to one; a further serialization of `structuredContent` still
happens inside the MCP SDK when it writes the JSON-RPC frame, and that one
is the actual wire write, untouched here.

**No behaviour change.** No error code, error message, limit constant,
catch structure, or success/error envelope shape changes. The
`structuredContent(output)` argument is unchanged, so a successful
response still carries the payload in both `content[0].text` and
`structuredContent` — this pass does not deduplicate that.

**Test-coverage finding (STEP 5, stated as it came out).** The change
makes it newly possible for `content[0].text` to disagree with
`structuredContent`. To check whether the suite would catch that, the new
overload was temporarily edited to prefix a character to the `TextContent`
string while leaving `structuredContent` correct, and
`mvn -pl regression-mcp-server -am test` was re-run: **no test failed**
(280 / 0 / 0 / 5, BUILD SUCCESS). `RegressionMcpServerContractTest` and
`RegressionMcpServerStdioIntegrationTest` both assert only against the
`structuredContent` view of a response; nothing parses `content[0].text`
and compares the two. No test was added in this pass — the finding is
recorded, not acted on.

**Verification.** `mvn -pl regression-mcp-server -am test`: 280 / 0 / 0 / 5,
BUILD SUCCESS, before and after the change, with the same five
environment-conditional Windows symlink-permission skips by name. The
temporary corruption was reverted and `git diff` confirmed only the
intended change remained before committing.

2026-09-08 — two documentation corrections left open by the
error-result merge arc, branch `docs/d15-fourth-occasion-and-item-count`.
Two files changed, `docs/TECHNICAL_DEBT.md` and `HANDOFF.md`; no source,
POM, test, or CI file touched:

**D15's fourth occasion, catalogued.** The 2026-09-01 under-count of
`TestRunCoordinatorTest.retainedChildIsRemovedWhenParentExitsBeforeCoordinatorCleanup`
(asserted `>= 2` owned processes, observed 1; local Windows, branch
`refactor/merge-error-result`) had been recorded only in that session's
entry below, with a note that a later pass might add it to the catalogue.
It is now the fourth bullet in item **D15**, in the same shape as the
other three, and every occasion count in the item was updated to match
(four occasions; the one method now three of the four). The change under
test that day was a pure identifier rename plus an unused-import deletion
with no path to process-tree observation, and the failure cleared without
intervention across two subsequent green full-suite runs — so this adds an
observation without re-diagnosing: D15's characterization, its Cost line,
and its "cause unestablished, no fix scheduled" conclusion are unchanged.

**The catalogue's item count moved into the catalogue.** That count had
lived only in this file's "Known debt and open questions" bullet, a
different file from the one it counts, which let the two drift silently —
and it had, twice before, as that bullet's own drift history records. A
single dateless statement of the total and the per-section breakdown
(33 items: 2 A, 9 B, 7 C, 15 D), said to be counted from the file's own
`###` headers, now sits in `docs/TECHNICAL_DEBT.md`'s introductory prose.
The HANDOFF bullet now points at the catalogue as the place the count is
stated and carries no figure of its own; its retirement notes for B8 and
A4, its four-section explanation, and its drift history are kept.

`mvn validate`: BUILD SUCCESS.

2026-09-01 — the two private static error-envelope helpers in
`RegressionMcpServer` merged into one, branch `refactor/merge-error-result`,
PR #43. One source file changed in the first commit, two documentation
files in the second; no POM or CI file touched:

**Provenance.** A read-only inspection pass preceded the change. It
established that `moduleErrorResult` (declared with the real body) and
`errorResult` (a three-line alias whose entire body was
`return moduleErrorResult(code, message);`) were identical in behaviour;
that the module had 19 `errorResult(` and 9 `moduleErrorResult(` grep
matches, reconciling exactly (12 + 7 call statements, plus the two
declarations and the one alias-body call); that no test in
`regression-mcp-server/src/test` names either method; and that the
`{status:"error", error:{code, message}}` envelope shape is declared once,
in `ToolSchemas.structuredOutputSchema`'s `failure` branch, and attached
as the `outputSchema` of every read-only tool.

**The change.** `moduleErrorResult` was renamed to `errorResult` (body,
position, visibility, modifiers, signature otherwise untouched) and the
old three-line `errorResult` alias deleted; the seven call sites that
named `moduleErrorResult` (three in `listModulesTool`, two in
`featureListTool`, two in `scenarioListTool`) now call `errorResult`. The
twelve pre-existing `errorResult` call sites were untouched. `git diff`:
one file, +8 / −13, fully mechanical (7 renamed call sites, 1 renamed
declaration signature, the 4-line alias-plus-blank, and the import below).

**Why the name `errorResult`.** It is the name the three
`com.aqa.mcp.validation` tool classes (`ArchitectureTool`,
`FrameworkConventionsTool`, `ModuleBoundariesTool`) already use for the
same envelope, and it keeps the catch-clause quoted verbatim in
`regression-mcp-server/docs/classes/TestRunCoordinator.md`
(`catch (ExecutionPlanningException e) { return errorResult(e.code(), e.getMessage()); }`)
accurate with no doc edit. `HANDOFF.md`'s 2026-08-29 entry below still
quotes `moduleErrorResult(...)` in its dated account of that session and
is deliberately left as-is — it records what the code was at that time.

**A4 closed and retired.** The unused `import java.nio.file.Path;` in
`RegressionMcpServer.java` (the `Path` type is referenced nowhere in the
file; the only other `Path` tokens are the string literal `"relativePath"`
and the `artifact.relativePath()` call beside it on the same line) was
removed in the same first commit — the opportunistic removal that
item A4 called for. A4 was deleted from `docs/TECHNICAL_DEBT.md` and its
identifier retired, not reused; the file now holds 33 items (A 2, B 9,
C 7, D 15), and the item-count bullet under "Current state" above was
updated 34 → 33.

**Verification.** `mvn -pl regression-mcp-server -am test`: the first
full-suite run failed
`TestRunCoordinatorTest.retainedChildIsRemovedWhenParentExitsBeforeCoordinatorCleanup`
(asserted `>= 2` owned processes, observed 1) — a fourth occurrence of the
D15 pattern (`docs/TECHNICAL_DEBT.md`), Windows, and like the 2026-08-29
and 2026-08-31 occasions it cleared without intervention: the same test
passed in isolation on the pre-edit tree, and two subsequent full-suite
runs with the change applied were both 280 / 0 / 0 / 5, BUILD SUCCESS,
with the same five environment-conditional symlink skips by name. The
failing test touches neither `errorResult`/`moduleErrorResult` nor the
removed import. D15 itself was not edited this session (inspection scope);
a future pass may add this as its fourth catalogued occasion. `mvn
validate` green after the documentation commit.

2026-08-31 — the 18 JSON Schema builder methods extracted from
`RegressionMcpServer` into a new class `ToolSchemas`, branch
`refactor/extract-tool-schemas` (PR open, not merged as of this entry).
Two source files plus three documentation files touched; no POM or CI
file touched:

**Provenance.** Two read-only inspection passes preceded the extraction.
They established that no code outside
`regression-mcp-server/src/main/java/com/aqa/mcp/RegressionMcpServer.java`
calls any of the 18 builders, by grepping the builder names in six forms
across the whole repository: qualified call (`RegressionMcpServer.<name>`),
bare call or declaration (`<name>(`), method reference
(`RegressionMcpServer::`), static import
(`import static com.aqa.mcp.RegressionMcpServer`), string literal
(`"<name>"`), and reflection (`getDeclaredMethod`, `getMethod(`,
`.class.getDeclared`). Every genuine call site is inside
`RegressionMcpServer`; the apparent `inputSchema` / `outputSchema` hits
elsewhere are the three `com.aqa.mcp.validation` tool classes' own
same-named private methods and the MCP SDK `Tool` accessors the contract
tests read.

**The change.** `regression-mcp-server/src/main/java/com/aqa/mcp/ToolSchemas.java`
(new, 139 lines) — `final class ToolSchemas` in package `com.aqa.mcp` (not
a sub-package: `moduleListOutputSchema` needs the package-private
`ModuleType.schemaValues()`), private constructor, all 18 method bodies
transplanted byte-for-byte. Eleven methods widened `private` →
package-private because `RegressionMcpServer` now calls them across the
class boundary; four stay `private` (the internal helpers
`structuredOutputSchema`, `stringArray`, `artifactSchema`,
`artifactSchemaProperties`); three (`inputSchema`, `outputSchema`,
`moduleListOutputSchema`) were already package-private with no caller that
required it and keep that visibility. `RegressionMcpServer.java` lost the
119-line block (547 → 427 lines) and gained an explicit `ToolSchemas.`
qualifier at each of 20 call sites — a static import was avoided so the
call sites stay greppable, which the two inspection passes depended on.

**No behaviour and no schema body changed.** The builders produce the same
maps; `Tool.Builder` serialises them into the same `tools/list` response.

**Verification.** `mvn -pl regression-mcp-server -am test` on `master` at
`18064cf` before any edit: 280 / 0 / 0 / 5, BUILD SUCCESS (first run
failed `TestRunCoordinatorTest.retainedChildIsRemovedWhenParentExitsBeforeCoordinatorCleanup`
— the D15 recurrence, `docs/TECHNICAL_DEBT.md` — green on a single
re-run). After the extraction, identical: 280 / 0 / 0 / 5, BUILD SUCCESS,
same five environment-conditional skips by name. `mvn validate` green.
`grep -F "ToolSchemas."` over `RegressionMcpServer.java` returns exactly
20 lines.

**New dossier.** `regression-mcp-server/docs/classes/ToolSchemas.md`,
matching the `TestRunCoordinator.md` conventions — it carries the
visibility analysis, the accepted-cost note that package-private no longer
implies a test seam for these eleven methods, the `ModuleType` package
constraint, the name-collision false positives, and the search-form
boundary from the inspection passes.

**Third commit, review cleanup.** A follow-up commit on the same branch
closed three loose ends found reviewing PR #42. `regression-mcp-server/docs/ARCHITECTURE.md`
became inaccurate on merge — its class inventory omitted `ToolSchemas`;
the `com.aqa.mcp` (root) count (10 → 11), the module total (66 → 67), and
the tier-1 review group (18 → 19) were corrected and a `ToolSchemas` row
added (tier 1, fan-in 1, schema-visible). `docs/TECHNICAL_DEBT.md` gained
item **A4**: `import java.nio.file.Path;` in `RegressionMcpServer.java` has
no non-import use and was already dead at `18064cf` — not caused by the
extraction — and no Checkstyle/PMD/Spotless is configured to catch it;
accepted as tolerable, remove opportunistically, not fixed here. The
item-count bullet above was updated (32 → 34) and `HANDOFF.md`'s duplicate
`(latest)` marker (the stale one on the 2026-08-28 entry) removed. The two
assertion lines quoted in D15's Location field were re-verified against
`TestRunCoordinatorTest.java` — both match — so D15 was not touched.
`mvn validate` green.

2026-08-29 — `regression_list_scenarios`' error code for a broken
`REGRESSION_ROOT` corrected, branch `fix/list-scenarios-repository-error`
(PR open, not merged as of this entry). One production line changed, plus
two pinned test assertions; no POM or CI file touched:

**What was wrong.** `RegressionMcpServer.scenarioListTool`'s call handler
reported `INVALID_ARGUMENTS` when `RepositoryRootResolver.resolve` failed
at call time — i.e. for a broken `REGRESSION_ROOT` — while the sibling
`RegressionMcpServer.featureListTool` reported `REPOSITORY_ERROR` for the
identical condition. A client whose arguments were fine but whose
repository root was misconfigured was told its arguments were invalid.

**Mechanism.** `scenarioListTool`'s handler has two catch clauses on one
try: `catch (RepositoryInspectionException)` then
`catch (IllegalArgumentException)`. The second clause chose its code with
`exception instanceof RepositoryInspectionException inspection ?
inspection.code() : "INVALID_ARGUMENTS"`. That `instanceof` was dead:
`RepositoryInspectionException` is `final`, extends
`IllegalArgumentException`, and is already taken by the preceding clause,
so it can never reach the second — the ternary always yielded
`"INVALID_ARGUMENTS"`. The only throwable that actually reaches the second
clause is a plain `IllegalArgumentException` from
`RepositoryRootResolver.resolve`. The fix replaces the ternary with a
plain `moduleErrorResult("REPOSITORY_ERROR", exception.getMessage())`,
identical to `featureListTool`'s second clause.

**Pre-fix codes were recorded before the change.** Driven through both
handlers against a root that was valid when the tool specification was
built and then made invalid at call time (its `pom.xml` deleted),
`regression_list_features` returned `REPOSITORY_ERROR` and
`regression_list_scenarios` returned `INVALID_ARGUMENTS`, both carrying
the identical message `REGRESSION_ROOT must contain the root pom.xml.`.

**Now pinned by test**, both in `RegressionMcpServerContractTest`:
`bothListToolsReportABrokenRepositoryRootAsRepositoryError` asserts both
tools return `REPOSITORY_ERROR` for a broken root;
`listScenariosStillReportsABadArgumentAsInvalidArguments` asserts
`scenarioListTool` still returns `INVALID_ARGUMENTS` for a genuinely bad
argument set (an unknown key alongside a valid `module`), so a later fold
of the two catch clauses into one cannot silently reclassify a bad
argument as a repository failure. Each assertion was shown non-vacuous
against a deliberately regressed handler before being kept.

**`regression-mcp-server/docs/TOOLS.md` needed no edit.** It documents no
per-tool error codes for `regression_list_features` or
`regression_list_scenarios`, and its "Common error codes" section already
defines `REPOSITORY_ERROR` as "a discovery tool's underlying
`pom.xml`/module resolution failed for the current request only, not at
server startup" — exactly this condition — and `INVALID_ARGUMENTS` as
"schema-level input rejection", which a broken root is not.

**Verification.** `mvn -pl regression-mcp-server -am test` on the branch:
280 / 0 / 0 / 5 (was 278 / 0 / 0 / 5 on `master`; the +2 are this pass's
new assertion plus the broken-root assertion added on the same branch a
pass earlier). During this pass
`TestRunCoordinatorTest.retainedChildIsRemovedWhenParentExitsBeforeCoordinatorCleanup`
failed on two consecutive back-to-back full-suite runs — its assertion
that the persisted run owns at least 2 processes (`ownedProcesses()`)
observed 1 — then passed on a third full-suite run with this branch's
changes present, and passed on every run in isolation; the stashed clean
`master` tree ran 279 / 0 / 0 / 5 green in between. The one conclusion
that follows is that the failure is independent of this branch's change,
which touches `RegressionMcpServer` and not the coordinator or its tests.
No cause was established. This is the second recorded occasion on which a
`TestRunCoordinatorTest` process-tree ownership assertion has
under-counted; the first is the 2026-08-17 CI failure catalogued as
`docs/TECHNICAL_DEBT.md` item D2, which was explicitly judged at the time
not to be a flake. The pair is now tracked as the open question
`docs/TECHNICAL_DEBT.md` item D15.

2026-08-28 — documentation reconciliation after the
`TestRunCoordinator` terminal-path work, branch
`docs/coordinator-arc-reconciliation` (PR open, not merged as of this
entry). Documentation only — no production, test, POM or CI file touched:

Reconciled every committed document against the tree after PRs #36-#38.
The correction list was re-derived from source, not trusted from the
working-log P1-P11 list. Corrected: `docs/classes/TestRunCoordinator.md`
(§2 non-injectable-executors claim; §3 5-arg and 7-arg caller lists and the
zero-caller finding; §4 `worker` "not injectable" and "pool size 3
hard-coded"; §5 "not yet tested"; §8 `@Test` count 19→21 and the "two
paths zero coverage" cell and the "two of four ... material gaps"
sentence; §9/§10/§13a O2 caveats; §11 O2 rewritten as raised-then-refuted;
§12 verdict premises; §13b blockers; §13c heading and the Path A/C
"reachable but not tested" bullets; hypotheses H2; the "what this dossier
did NOT verify" O2 and test-count bullets; §1 line count 418→425), plus
`regression-mcp-server/docs/ARCHITECTURE.md` Group 7,
`regression-mcp-server/docs/TEST_MAP.md`, `docs/ROADMAP.md`,
`docs/TECHNICAL_DEBT.md` (item D9's "pre-A2" markers; the numbering-scheme
example list) and `regression-mcp-server/README.md` (an "item A2"
reference). Zero citations of retired B8/A2/B1 remain in live documentation.
`mvn validate` from the root: BUILD SUCCESS. Suite figure unchanged at
278/0/0/5 (no code touched). Dossier internal line numbers in §13 shifted
+7 from the 7-arg constructor and were left as accepted rot per the
dossier's own line-numbers-are-secondary policy.

2026-08-28 (earlier) — characterization test for `TestRunCoordinator`'s
early-cause return terminal path, branch `test/coordinator-early-cause-path`
(merged, PR #38). Stacked on PR #37 (the injectable worker `ExecutorService`
seam), merged to `master` earlier this session:

Added one test to `TestRunCoordinatorTest`,
`causeLatchedBeforeWorkerStartsReturnsCancelledWithNothingLaunched`, plus
two nested fixtures: `GatedWorkerExecutor` (an `ExecutorService` passed
through the 7-arg constructor that holds the first submitted task in a real
`CountDownLatch` barrier — no sleep, no timeout — and records, clock-free
via happens-before, whether `cancel()` had returned when the task was
released) and `NeverLaunchingLauncher` (throws if `execute()` ever reaches
the launch). The test latches `CANCELLED` while the worker task is parked,
then releases. Test sources only — no production, POM, or CI change.

Observed current behaviour of the early-cause path: terminal state
`CANCELLED`; in-memory and on-disk snapshots agree; `startedAt`,
`exitCode`, `skippedTests` all absent; `finishedAt` set; capture published
as `UNAVAILABLE`; **no process ever launched** (`launches()==0`, zero owned
processes); lock released, active slot cleared; no exception on the worker
thread. Not a defect — the path behaves coherently. 20+ consecutive runs
green.

With this, all four `execute()` terminal paths are covered (normal, the
`RuntimeException` catch, the `InterruptedException` catch since PR #36,
and now the early-cause return). **`docs/TECHNICAL_DEBT.md` item B8 was
retired** — both gaps it tracked are closed; its identifier is not reused.
Made B8 citations self-contained in `docs/TECHNICAL_DEBT.md` (C7),
`regression-mcp-server/docs/TEST_MAP.md`, and `docs/ROADMAP.md` (items 3
and 6). The dossier and `ARCHITECTURE.md` still cite B8 and the "two
untested paths" framing; those, plus the seam-induced staleness from
PR #37, are appended to the recorded pending-dossier-corrections list in
`output.log` (P1-P8 from the prior pass, now extended) for the separate
documentation pass. `mvn -pl regression-mcp-server -am test` on this
branch: 277/0/0/5 before, 278/0/0/5 after (delta is exactly this test).
`mvn validate` from the root re-run clean.

2026-08-28 (later still) — injectable worker `ExecutorService` seam on
`TestRunCoordinator`, branch `refactor/coordinator-worker-seam` (merged,
PR #37). PRs #35 (dossier) and #36 (the `InterruptedException`
characterization test) both merged to `master` earlier this session — that
branch was stacked on that merged state:

Made `TestRunCoordinator`'s internal worker `ExecutorService` injectable
for tests, and nothing else — the one authorized production change.
Added a 7-arg package-private constructor
`TestRunCoordinator(Path, Supplier, MavenProcessLauncher, TimeoutScheduler, Function, ProcessView, ExecutorService)`
as the new field-initialising base ctor; the former base (6-arg) now
delegates to it, supplying the unchanged default
`Executors.newFixedThreadPool(3, …"regression-mcp-run-worker")`. This
follows the exact widening-constructor chain the existing
`launcher`/`timeouts`/`runtimeLoader`/`processView` seams use. The public
2-arg constructor's signature and body are untouched; `recoverIfUnowned()`
still runs last in construction, after the worker field is set. `close()`
is unchanged and shuts down whatever executor the field holds, injected or
default — **the seam's contract is that a caller passing an executor hands
over its lifetime, so a shared or reused pool must not be injected.** No
test was added this pass (that is a separate pass — the path-A
characterization test B8 still needs). No POM or CI file touched.
`mvn -pl regression-mcp-server -am test` measured on this branch: 277/0/0/5
before the edit and 277/0/0/5 after — every existing caller (2-arg, 5-arg,
6-arg) reaches the identical effective collaborators via the new chain.
`mvn validate` from the root re-run clean. Updated
`docs/classes/TestRunCoordinator.md` (§3 constructor list, §5 seam
inventory, §13c path-A reachability), `docs/TECHNICAL_DEBT.md` B8 (seam
exists, only the test remains), and `docs/ROADMAP.md` item 3 for
consistency with B8. Two pre-existing dossier inaccuracies found during
the re-read but out of this pass's scope are recorded verbatim in the
session report appended to `output.log` for a later documentation pass.

2026-08-28 (later) — characterization test for `TestRunCoordinator`'s
`catch (InterruptedException)` terminal path, branch
`test/coordinator-interrupted-path` (merged, PR #36):

Added one test to `TestRunCoordinatorTest`,
`interruptedWaitInWaitForPersistsCancelledTerminalRecordAndReleasesLockAndSlot`,
plus two nested `Process`/`MavenProcessLauncher` fixtures
(`WaitForThrowsInterruptedLauncher`, `WaitForThrowsInterruptedProcess`)
that mirror the suite's existing `SingleSkippedTestReportThenExitValueFailureOnceLauncher`
/ `ExitValueFailsOnceProcess` pair. The launcher delegates to a real
`ControlledProcessLauncher("WAIT")` (so `execute()`'s process-identity
lookup succeeds against the real `SystemProcessView`) and wraps the
returned process so its blocking `waitFor()` throws
`InterruptedException` — driving the worker into the catch with **no
production change** and no new seam. No production source, POM, or CI
file was touched.

**The dossier's O2 concern is refuted.** O2 (PLAUSIBLE, unverified) held
that the branch's `Thread.currentThread().interrupt()` re-assertion would
make the subsequent `RunStore` filesystem I/O throw a
`ClosedByInterruptException`-derived error and leave no terminal record
on disk. Observed, with the worker's interrupt flag set: `status.json`
reaches `CANCELLED`, `finishedAt` set, capture published as
`UNAVAILABLE`, lock released, active slot cleared, no exception escapes
the catch. The JDK bulk helpers `Files.readString`/`writeString` run on
uninterruptible channels. The interrupt flag also does not leak to the
next task on the pooled worker thread. `docs/TECHNICAL_DEBT.md` item B8
was narrowed to the early-cause return only (still uncovered — it needs
an injectable worker `ExecutorService`), with the O2 refutation and the
new test recorded there; `docs/ROADMAP.md` item 3 and
`regression-mcp-server/docs/TEST_MAP.md` were updated to match.
`mvn -pl regression-mcp-server -am test` = 277/0/0/5 (was 276; delta is
exactly this test). `mvn validate` from the root re-run clean.

2026-08-28 — first per-class dossier: `TestRunCoordinator`, branch
`docs/dossier-testruncoordinator` (merged, PR #35):

Wrote `regression-mcp-server/docs/classes/TestRunCoordinator.md` from the
tree (the full class plus every cited collaborator, test and doc), covering
the standard sections 1-12 plus a section 13 that enumerates all four
`execute()` terminal paths, their ordered side effects, a
common-vs-unique side-effect table, and — for the two untested paths — what
a test must control and which seams exist. Verdict: **TEST FIRST**. Ten
hypotheses were checked against the source; the notable results: the
`InterruptedException` path is reachable from a test with no production
change (a `Process` whose `waitFor()` throws), but the early-cause path is
not without an injectable worker `ExecutorService`; the
`secondCaptureCallInTheRuntimeExceptionPath…` test does not exercise the
guard it is named for (its fixture throws one call site too early); and a
malformed `runId` returns `INVALID_ARGUMENTS` from the four report/artifact
tools but `RUN_NOT_FOUND` from `regression_get_test_run`/`_cancel_test_run`.

`docs/TECHNICAL_DEBT.md` gained three items — **B11** (the misfiring guard
test), **C7** (the per-run monitor held across capture's filesystem I/O,
accepted with a review trigger), **D14** (the malformed-`runId` error-code
divergence vs `docs/TOOLS.md`, judged not a section-A defect) — and item
**B8** was sharpened with a PLAUSIBLE note that the `InterruptedException`
path may be unable to persist a terminal snapshot at all, because it
re-asserts the interrupt flag before doing interruptible `FileChannel`
I/O. No production source, test, POM or CI file was touched;
`mvn validate` was re-run clean.

2026-08-27 (later) — cleanup and baseline pass, branch
`docs/debt-evidence-selfcontained` (PR open, not merged as of this entry):

Measured the current `regression-mcp-server` suite: `mvn -pl
regression-mcp-server -am test` (run with the live MCP server still up —
the `test` phase does not repackage the jar, so the Windows file lock does
not apply) reported 276 tests, 0 failures, 0 errors, 5 skipped. 276 is the
current figure; the 272 and 275 numbers in older notes are superseded. The
5 skips are all the symlink-escape defence tests, which abort on this
Windows account and run on Linux CI.

Corrected the documents against that measurement and the current tree,
across three commits on this branch, all stacked on `1b98248`:
  - finished the in-flight D7/D8 rewrite — both items now cite named
    files, classes and methods instead of the cleared local working log —
    and committed it;
  - added a paragraph to `docs/TECHNICAL_DEBT.md` item B10 recording that
    its earlier scoped-vs-unscoped validator timing measurement did not
    survive into any committed document, and that authorizing the cache or
    skip work needs a fresh measurement taken with the JVM warmed and with
    the two calls each run first in a separate ordering (B10's Cost field
    was left at "2-3 passes" — the benchmark folds into the first
    implementation pass);
  - corrected four stale statements in this file: the mcp-server test
    count (272 -> 276), the commerce scenario count (2 -> 3 on disk since
    `a691978`), the missing staleness caveat on
    `regression-mcp-server/docs/SESSION_DEMO.md`, and the "6 reactor
    modules" wording (five modules plus the aggregator POM).

Branch state: commit `1b98248` (the architecture map and test map) is on
two local branches, `docs/mcp-architecture-map` and
`docs/debt-evidence-selfcontained`. This pass's three commits are on
`docs/debt-evidence-selfcontained` only. Both branches are pushed and each
has its own PR to `master`; the map PR must merge first, and until it does
the debt-evidence PR's diff will also show the map's contents.

2026-08-27 — `regression-mcp-server` inspection formalized into committed
documentation, branch `docs/mcp-architecture-map` (not merged as of this
entry):

A prior inspection pass (this same session, before this entry) built a
full architecture map and test-suite map for `regression-mcp-server`
against commit `4d7c12148330e532aa0a68e076ab6bbcd69af3cc`, before any of it
was committed to a tracked document. A drift check at the start of this
pass found
the tree had moved to `7107c49fa305dde53ac3d6d0e009da67d773d859` in the
interim (two commits, `.github/workflows/main.yml` and
`.github/workflows/commerce-regression.yml` gaining `workflow_dispatch`
triggers only — confirmed additive by reading the diff directly, no
`regression-mcp-server` file touched, no gate weakened) and that the
original anchor, `4d7c1214`, had merged with **zero CI runs recorded
against it** (`gh run list --commit`/the Checks API both empty), due to a
GitHub Actions platform incident on 2026-08-26 that dropped its push
event. The map's substance was unaffected (zero module files changed
between the two commits) but its own arithmetic had two independent
errors, both caught and corrected during the drift-check pass: the class
count was stated as 67 rather than 66, and tier 0/1 were mis-totaled
(27/16 stated vs. 24/17 actually listed) with one class,
`MavenProcessLauncher`, omitted from the tier list and review order
entirely. `docs/TECHNICAL_DEBT.md` item B7 (no branch protection) was
updated to record the `4d7c1214` gap as a demonstrated occurrence rather
than only a theoretical cost, rather than logging a separate item for it.

The corrected map was then committed as
[`regression-mcp-server/docs/ARCHITECTURE.md`](regression-mcp-server/docs/ARCHITECTURE.md)
and [`regression-mcp-server/docs/TEST_MAP.md`](regression-mcp-server/docs/TEST_MAP.md)
(new files), anchored to `7107c49f`. Seven new `docs/TECHNICAL_DEBT.md`
items were logged from findings surfaced during the inspection: A3 (
`docs/TOOLS.md` documents `regression_start_test_run` as "not open-world"
while the code sets `openWorldHint(true)`, confirmed passing in
`RegressionMcpServerStdioIntegrationTest`), B8 (`TestRunCoordinator`'s
early-cause-return and `InterruptedException` terminal paths have zero
test coverage), B9 (`MavenRuntimeConfigurationLoader.load` has no direct
test), B10 (all three validator tools re-scan every declared module on
every call regardless of request scope, with no cache), C6
(`execution`/`validation` sibling independence is enforced only by
ARCH-002's cycle detection, which does not catch one-way coupling), D12
(`ModuleValidationResult.truncated` is a hardcoded `false` literal in all
three validator tools; no code path can produce `true`), and D13
(`request.environment()` is validated only by set membership, then
reaches the Maven command line unescaped — inert today only because both
registered profiles declare exactly `"dev"`). `docs/ROADMAP.md`'s
`regression-mcp-server` section was rewritten with a cost-ranked candidate
list (6 items, from the 1-pass documentation fix through the 3-4-pass
four-path terminal-transition consolidation, the latter explicitly gated
on characterization tests for the two newly-identified untested paths
existing first — not merely recommended first). No production source,
test, POM, or CI file was touched this pass; `mvn validate` was re-run
clean after the documentation changes. No commit or push has been made
without further authorization.

**What remains open**: a per-class dossier directory
(`regression-mcp-server/docs/classes/`) has not been started. The
architecture map's own review order (Group 1 through Group 8, leaves
first, hubs last) is the intended sequence for that work; see "Next step"
below.

2026-08-25 — `docs/TECHNICAL_DEBT.md` item A2 (a run whose tag expression
matches nothing was reported as `PASSED` with no visible signal) closed via
branch `a2-skipped-test-count` (PR opened, not merged as of this entry): the
decision that item's Cost line had left open — surfacing the skipped count
directly in the run snapshot, rather than changing the terminal-state
contract — was made and implemented. `RunSnapshot` gained a new boxed
`Integer skippedTests` component (appended last, no null-guard, since it is
legitimately absent for any run with no parsed Surefire report).
`ReportCapture.capture` now returns a `CaptureOutcome(CaptureMetadata,
Integer skippedTests)` record instead of a bare `CaptureMetadata`, taking
the count directly from the `SurefireSummary` it already parses in memory
— no extra disk read. `TestRunCoordinator` threads that value through all
four `execute()` paths that reach `persistTerminal` (including an overwrite
guard so a redundant second capture call inside the
`catch (RuntimeException)` path cannot wipe a count the first call already
produced) and through `recoverIfUnowned()`'s restart-recovery path the same
way. `RegressionMcpServer.runOutput`/`runOutputSchema` expose it following
the same guarded-omit pattern as the four pre-existing nullable run-snapshot
fields: present only when non-null, never emitted as a JSON `null`.
`docs/TECHNICAL_DEBT.md` item A2 was removed (its identifier retired, not
reused) and three new items were logged from observations made while
implementing this: D7 (`Map.copyOf` discards `runOutput`'s deliberate
`LinkedHashMap` key order, so response key order is not stable across
server restarts — functionally harmless, closed by observation), D8
(`run.json` is written by `RunStore.create` but never read back by any
production code — the only reader anywhere is a test comparing the file to
itself), and D9 (`SurefireSummaryStoreTest`'s "legacy" fixture is not
actually a frozen literal the way `ReportCaptureTest`'s is — it serializes
a live `RunSnapshot` through Jackson at test-run time, so it silently
tracks the current record shape rather than the historical one it appears
to prove). `docs/TECHNICAL_DEBT.md` now holds 20 items (was 18). Build
re-verified green after a forced recompile (deleting `target/classes` and
`target/test-classes`, not a full `clean`, since a running MCP server holds
`target/regression-mcp-server.jar` open on Windows): 275 tests, 0 failures,
0 errors, 5 pre-existing/environment-conditional skips, same total as the
implementation pass before this one. `recoverIfUnowned()`'s own capture
call (`TestRunCoordinator.java`) was also fixed in this pass to stop
discarding the count it computes for a server-restart-recovered run: it now
carries the freshly captured value through when non-null, falling back to
the run's existing (always-null, for a recovered run) value only when the
capture attempt itself returns null.

2026-08-24 — `docs/TECHNICAL_DEBT.md` restructured into four action-typed
sections, branch `docs/technical-debt-restructure`, PR #26 (open, not
merged as of this entry):

An inspection pass first verified all ten pre-existing
`docs/TECHNICAL_DEBT.md` items individually against the current tree —
every cited path, line number, and quoted line of code confirmed accurate,
with zero drift found. The file was then restructured from a flat numbered
list into four sections grouped by the action each item calls for — A.
Defects (fix, or accept with a stated reason), B. Debt (schedule), C.
Accepted characteristics (no action, each with a stated review trigger),
D. Open questions and unproven assumptions (closed by observation, not
work) — with items identified as a section letter plus number (e.g. `B3`)
rather than a flat number, and a Cost estimate (in agent passes) added to
every item so priority is read from that field rather than from position
in the file. Eight items are new: A2 (a Cucumber tag expression matching
nothing is reported as `PASSED`), B5 (commerce scenarios are coupled to
literal third-party site content), B7 (`master` has no branch protection),
C4 (`target/allure-results` accumulates across local runs with nothing to
reset it), D2, D3 (mirrors this file's own "Not yet proven" section below,
which is intact and remains the narrative source; the two
cross-reference each other), D4 (an MCP-driven run does not
rebuild `regression-core`), and D5 (no retention policy for the `gh-pages`
branch's growing history). Ten inbound cross-references to old item
numbers — across this file, `docs/ROADMAP.md`, and
`regression-mcp-server/docs/SESSION_DEMO.md` — were updated to the new
identifiers.

Item D2 replaces an earlier belief, held briefly during this same arc, that
`RegressionMcpServerStdioIntegrationTest` was the notable intermittent-flake
risk worth documenting. Checking actual `gh run` history disproved that
belief: of 101 visible CI runs, exactly 2 failed, both on 2026-08-17, and
that specific test passed cleanly in both of them (`Tests run: 7,
Failures: 0`) — the two real failures were in `FailureArtifactStoreTest`
and `TestRunCoordinatorTest`, unrelated tests. D2 now documents those two
actual failures directly, with the STDIO test's timeout configuration
recorded only as unrelated context, not as a claim about either failure.

2026-08-23 — Allure report publishing for `regression-nextjs-commerce`
implemented, merged, and verified live in CI across PR #23, PR #24, and this
pass:

PR #23 (merge commit `cd8e6af`, branch `ci/commerce-allure-gh-pages`): wired
`allure-maven` 3.0.2 into `regression-nextjs-commerce/pom.xml` with no
`<executions>` block (so `mvn test` is unaffected — the `report` goal is
on-demand only) and `reportVersion` 2.39.0. That value was not the first
one tried: `reportVersion` 2.35.3, matching `allure.version`, was tested
locally first and fails outright, because `allure-commandline:2.35.3` does
not exist on Maven Central at all — 2.39.0 (petstore's already-working
value) was used instead once that was confirmed. Extended
`.github/workflows/commerce-regression.yml` to check out `gh-pages`,
restore any prior trend history into `target/allure-results/history/`,
generate the report, and publish it to `gh-pages`'s `/commerce/`
subdirectory via explicit `git` commands (no third-party publishing
action), gated to push-to-master only. The same commit was amended before
merging to fix a gating gap in its own first draft: Publish was gated on
the test step's outcome rather than on Generate's, so a failed report
generation could have `rm -rf`'d the live report; each step now gates on
its immediate predecessor's outcome instead. The merge triggered the first
real publish: 79 files, both Pages URLs returned HTTP 200, the page body
carried `allureVersion: 2.39.0`, and root `index.html`/`.nojekyll` blob
hashes were confirmed byte-identical before and after.

PR #24 (merge commit `e752705`, branch `ci/commerce-allure-gate-checkout`):
closed a second gating gap — Restore ran even if the preceding `gh-pages`
checkout step itself had failed, since Restore's `if:` used `!cancelled()`
rather than an implicit `success()`, so a failed checkout would silently
fall through to Restore's "no history found, first publish" branch and
reset the trend with no error anywhere in the run. Added
`steps.gh-pages-checkout.outcome == 'success'` to Restore's condition.
Merging this PR — the workflow file is itself inside the trigger's `paths`
filter — produced the second publish needed to prove the history mechanism
in real CI, not only locally: `history-trend.json` went from 1 to 2 data
points, and `history.json`'s two test-case keys each gained a second
`items` entry with genuinely different `uid`/`duration` values (two
distinct CI runs' data merged, not one run duplicated). That second publish
changed only 26 files, versus 79 for the first, confirming the report's
static assets are byte-identical between runs at a pinned `reportVersion`.
Root files were re-confirmed byte-identical again afterward.

This pass (branch `docs/allure-publishing-handoff`): folded the above into
`## Current state` above, marked `docs/ROADMAP.md`'s matching "non-interactive
CI reporting workflow" roadmap item as done for `regression-nextjs-commerce`,
added a link to the live report from `README.md`, and logged
`docs/TECHNICAL_DEBT.md` item C3 (nothing currently verifies that history
accumulation keeps working going forward — a broken restore path would still
leave generation and publish green, silently resetting the trend).

**Not yet proven, recorded here rather than quietly assumed**: publishing on
a genuinely RED test run has never happened — the gating in PR #23/#24 is
designed and reasoned for it, but no failing `commerce-regression` run has
occurred since this was built; when one does, confirm the report still
publishes and the job still goes red. The Publish step's `index.html`
existence guard has never actually fired. A concurrent-push race on
`gh-pages` (two `master` pushes landing close together, given the
workflow's `cancel-in-progress` concurrency group) is an accepted,
unexercised risk. A manual re-run of a publishing job (e.g. the Actions UI's
"re-run failed jobs") would add a duplicate trend data point, since
generation is not idempotent with respect to the trend file. These four
assumptions are also tracked as item D3 in `docs/TECHNICAL_DEBT.md`; this
file remains the narrative source for them.

2026-08-22 — MCP session demo published, current state and technical debt
reconciled:

Recorded a real MCP client session against `regression-nextjs-commerce`
(`initialize` → deliberately invalid `start` call → real `start` → poll to
terminal `PASSED` → `regression_get_test_summary`/
`regression_get_failure_summary`/`regression_get_failure_artifacts`) and
published the full, verbatim recording as
`regression-mcp-server/docs/SESSION_DEMO.md`, plus a short abridged excerpt
in `regression-mcp-server/README.md`'s new "Worked example" section and a
one-sentence pointer from the root `README.md`. This is also the first
place the module's actual end-to-end run duration was measured and recorded
anywhere in the repository: 22.6 seconds, from the server's own
`finishedAt` − `startedAt` timestamps. Logged three new
`docs/TECHNICAL_DEBT.md` items from characteristics observed directly in
that recording: item C2 (`regression-jhipster`'s Playwright traces are
written only on scenario failure and are never captured by `ReportCapture`;
two independent barriers — the MIME allow-list and the Allure-only artifact
listing — would block serving one through the MCP server even if a third
staging root existed), item B4 (`regression_get_test_run`'s
`stdoutBytes`/`stderrBytes` are hardcoded to zero for the entire `RUNNING`
state and only populate at terminal persistence, so they carry no live
progress signal; `reason` also duplicated `state` at every observation in
the same recording), and item A1 (`regression_get_test_summary`'s
`detailsTruncated` flag is true for essentially any real run regardless of
whether anything was truncated, and means something different from the
same-named field on `regression_get_failure_summary`, which was observed
directly in the same recording). Folded `regression-nextjs-commerce`'s CI
coverage, `build-and-test`'s now-real Maven gate, and the removed dead
Allure fixture into `## Current state` above, since that section had not
been updated for them despite each already being recorded under the PR
#18/#19 entries below.

2026-08-21 — four merged PRs, CI and cleanup:

PR #16 (`134a569`, branch `docs/petstore-decision-record`): the corrective
pass described in the entry below this one.

PR #17 (`a35d04d`, branch `docs/log-convention-and-cleanup`): `9d14a28`
switched `.gitignore` from an exact `output.log` filename to an
`output*.log` pattern; `c2e0630` documented `output.log` as the
gitignored working-log convention in `CLAUDE.md`, naming it robustly
against that pattern change; `af6ccf7` moved the MCP execution-scope
decision record out of the regular roadmap flow into its own new
"## Decisions" section in `docs/ROADMAP.md`; `73d00ef` recorded that
corrective pass in this file.

PR #18 (`f010553`, branch `ci/commerce-regression`): added
`.github/workflows/commerce-regression.yml`, `regression-nextjs-commerce`'s
first CI coverage — path-filtered on the module plus `regression-core`
and the root POM, no browser-provisioning step needed
(`DriverFactory` constructs `ChromeDriver` directly), a reachability
pre-flight against the public demo store distinguishing a third-party
outage from a test failure, and `if: always()` artifact upload. Also
added a reactor-wide Surefire `forkedProcessTimeoutInSeconds` of 900 to
the root `pom.xml` (root cause: an unexplained 5+ minute `@ui` hang,
see below), verified via `mvn help:effective-pom` against all five
product/tooling modules rather than inferred from a passing run, plus
`timeout-minutes` and a `cancel-in-progress` concurrency group on both
jobs in the existing `main.yml` workflow.

PR #19 (`76c7fd0`, branch `chore/remove-allure-fixture`): removed
`regression-nextjs-commerce`'s `attachment.feature`,
`AllureAttachmentFixtureSuite.java`, and `AllureAttachmentFixtureSteps.java`,
plus the `fixture.expected.allure.resultsDirectory` Surefire property that
existed solely to feed the deleted step class. An inspection pass first
proved the fixture dead: its class name never matched Surefire's default
test-discovery patterns, so no invocation anywhere in the repository ever
ran it — confirmed by a plain `mvn test` showing no trace of it alongside
an explicit `-Dtest=` run showing it passing cleanly when forced.

This pass (branch `docs/ci-decisions-and-handoff`): recorded two CI
decisions in `docs/ROADMAP.md`'s "## Decisions" section —
`regression-jhipster` and `regression-petstore-api` will not be added to
CI for now, both for reasons specific to each module (no way to raise
jhipster's app-under-test on a CI runner; petstore-api's shared
third-party sandbox with no delete-failure fallback). Recorded the
unreproduced `@ui` hang mentioned under PR #18 above in
`docs/TECHNICAL_DEBT.md` as item D1, including a same-day bounded
five-run reproduction probe that did not reproduce it. Removed
`continue-on-error: true` from `build-and-test`'s Maven step in
`main.yml` after confirming `regression-core` genuinely passes both in
the latest CI run's step-level conclusion and in a local
`mvn clean verify` — that job can now actually fail when
`regression-core` breaks.

2026-08-21 — corrective pass on branch `docs/petstore-decision-record`,
merged as PR #16 (`134a569`): turned the prior same-day pass's "Extend test
execution" rewrite into an explicit decision record. `57ccc07` rewrote
`docs/ROADMAP.md`'s regression-mcp-server section into "MCP execution scope
— regression-petstore-api will not be registered," stating plainly that the
module will not become a third `ExecutionProfile` and recording why (shared
public sandbox with no delete-fallback cleanup, an unresolved
`supportsHeadless` semantics gap, `MavenInvocationFactory` silently ignoring
any tags an MCP client supplied, and low payoff against the module's
existing plain-Maven execution), plus the conditions that would revisit it.
`5ca2b7b` then replaced this file's "blocked on three things" framing in
"Next step" below with a short pointer to that decision record, instead of
duplicating the reasoning here. No source, POM, or test file was touched.

2026-08-21 — documentation-only pass on branch `docs/roadmap-reconciliation`:
reconciled `docs/ROADMAP.md` against current master after the prior session's
`regression-jhipster` registration work. `docs/ROADMAP.md`'s "Extend test
execution" section still described `ExecutionProfileRegistry` as a
single-entry `PROFILES` map containing only Commerce — stale since commit
`9529e1a` registered `regression-jhipster` as a second entry. Rewrote that
section to reflect the current two-entry registry, to name
`regression-petstore-api` as the only remaining unregistered module, and to
record the specific gaps a third profile would need to resolve: a
`TestRunRequestValidator.validateHeadless` design question for a module with
no browser (verified live: it rejects any request when
`profile.supportsHeadless()` is false, and `regression-petstore-api` has no
`ui.headless` concept at all), and the module's POM not wiring
`mcp.surefire.reportsDirectory`/`mcp.allure.resultsDirectory` (confirmed by
actually running `mvn -pl regression-petstore-api -am test -Denv=dev` — it
passes, 5/5, but writes its Surefire/Allure output to the module's own
default `target/` paths, not to `ReportCapture`'s per-run staging
directories). No `regression-petstore-api` MCP-execution decision changed as
part of this pass — it remains manual-only, exactly as
`regression-mcp-server/README.md`'s "v1.0 limitations" already stated; this
session only made the roadmap accurately reflect why. No source, POM, or test
file was touched; see "Next step" below for the current, corrected picture.

The 2026-08-20 to 2026-08-21 session before this one closed
`regression-jhipster`'s MCP-execution gap end to end (commit range
`6dc8700..e0eed32`, merged as PR #12 `eddcfe0` and PR #13 `e0eed32`): added
its Maven-discoverable Cucumber suite runner and POM wiring (`6049a89`),
registered it as a second `ExecutionProfileRegistry` entry (`9529e1a`), fixed
a Cucumber glue-path defect that was failing 16 `@api` scenarios (`657f849`),
untangled headless configuration for both UI-driving modules (`2ed13ad`,
`ef88a51`), and audited `docs/TOOLS.md`/both `README.md` files plus two
client-facing MCP strings against the resulting code (`3c7ec68`, `f1159db`,
`ad6b6a8`). `regression-jhipster` is now a fully working second
MCP-executable module, on equal footing with `regression-nextjs-commerce`.

## Next step

The per-class dossier directory (`regression-mcp-server/docs/classes/`) is
started — `TestRunCoordinator.md` is the first (merged, PR #35), with three
merged follow-ups (PR #36 the `InterruptedException` characterization test,
which refuted concern O2; PR #37 the injectable worker `ExecutorService`
seam; PR #38 the early-cause characterization test) and this
documentation-reconciliation pass. All four of
`TestRunCoordinator.execute()`'s terminal paths are now covered, the debt
item that tracked the gap (B8) is retired, and the dossier / `ARCHITECTURE.md`
/ `TEST_MAP.md` are reconciled to the tree.

The real remaining `TestRunCoordinator` work, none of it yet scheduled:
- **`docs/TECHNICAL_DEBT.md` item B11** — the `execute()`-side
  `skippedTests` preservation guard is unproven by any test;
  `secondCaptureCallInTheRuntimeExceptionPath…` is named for it but does not
  reach the interleaving. Needs a fixture that makes `persistTerminal`'s
  first `RunStore.update` throw once after a successful try-block `capture`.
- **The `requireTerminal(id)` extraction** (dossier §11 O10 / H5) — the
  four report/artifact methods share a character-identical guard prologue
  (`RunId.valid` + in-memory-`Active` non-terminal check); extract it.
- **The four-`execute()`-path collapse into one terminal transition**
  (dossier §13b, `docs/ROADMAP.md` item 6) — now unblocked by the two new
  path tests; still gated on B11 (the guard test a collapse must preserve)
  and remains high-risk given the class's centrality.

The other concrete next step is to continue the dossier work following
[`regression-mcp-server/docs/ARCHITECTURE.md`](regression-mcp-server/docs/ARCHITECTURE.md)'s
own review order (Group 1's tier-0 leaves first — e.g. `RepositoryRoot`,
`ModuleType`, `FailureArtifact` — through `RegressionMcpServer` last);
`TestRunCoordinator` was taken out of order by explicit instruction, so the
classes it depends on still need their own dossiers and its own
verdict (TEST FIRST) should be revisited once they exist.
Each dossier is a separate, single-class pass; do not batch more than one
class per pass. `docs/ROADMAP.md`'s `regression-mcp-server` section holds
the cost-ranked refactoring candidates this inspection surfaced — none is
authorized yet, and authorization of one item does not extend to any
other.

`regression-jhipster` is now MCP-executable, as the second module
alongside `regression-nextjs-commerce`: it has an `ExecutionProfileRegistry`
entry, its POM wires the `mcp.surefire.reportsDirectory`/
`mcp.allure.resultsDirectory` system properties `MavenInvocationFactory`/
`ReportCapture` depend on, its Cucumber glue path and headless
configuration were fixed and simplified, and `@api`/`@ui`/`@hybrid` all
pass against a live app. See `regression-mcp-server/docs/TOOLS.md` and
`regression-mcp-server/README.md`'s "v1.0 limitations" for current,
authoritative wiring detail — consult `ExecutionProfileRegistry` and each
module's own POM directly rather than trusting a hardcoded module list
here, since a further module may be registered later.

`regression-petstore-api` will not be registered as a third MCP
`ExecutionProfile`. This is a recorded decision, not pending work — see
`docs/ROADMAP.md`'s "MCP execution scope — regression-petstore-api will
not be registered" section for the full reasoning and the conditions that
would revisit it.

As of PR #18, `regression-nextjs-commerce` also runs in CI
(`.github/workflows/commerce-regression.yml`), alongside a reactor-wide
Surefire fork timeout and a now-meaningful `build-and-test` job (its
`continue-on-error` was removed this session). `regression-jhipster` and
`regression-petstore-api` will not be added to CI; see
`docs/ROADMAP.md`'s "Decision: regression-jhipster is not run in CI" and
"Decision: regression-petstore-api is not run in CI" sections for the
reasoning and the conditions that would revisit either.

The smallest independent starting point remains `regression-petstore-api`'s
"Add a failure-safe fallback cleanup path for the Petstore delete
scenario" (see `docs/ROADMAP.md`'s "regression-petstore-api" section and
`regression-petstore-api/README.md`'s "Current Limitations and
Trade-offs" for the current gap).

`docs/TECHNICAL_DEBT.md` now carries a Cost estimate per item; among items
with a filled-in estimate, B7 (no branch protection on `master`; Cost: 0
passes, one repository setting) is the cheapest open item, and its
scheduling is not asserted here.
