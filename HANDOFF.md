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
  - `mvn -pl regression-mcp-server -am test` — Tests run: 278, Failures: 0,
    Errors: 0, Skipped: 5 (measured 2026-08-28, after adding two
    characterization tests to `TestRunCoordinatorTest` — one for the
    `InterruptedException` terminal path, one for the early-cause return;
    was 276 on 2026-08-27, which superseded the earlier 272 and 275
    figures). All 5 skips are environment-conditional:
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
  (32 items as of 2026-08-28, counted directly from the file's own `###`
  headers rather than trusted from this bullet's own prior figure — this
  bullet had drifted to "20 items as of 2026-08-25" when the file actually
  held 23 at that date, a staleness caught and corrected during the
  2026-08-27 session below. Item **B8** — the two untested
  `TestRunCoordinator.execute()` terminal paths — was retired on 2026-08-28
  once characterization tests covered both (its identifier is not reused).
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
  (layer map, 66-class inventory with tier/fan-in/contract-exposure bucket
  per class, three flow walkthroughs, lifecycle/ownership, data model,
  boundary/trust surface, extension points, and a leaves-first/hubs-last
  review order) and [`regression-mcp-server/docs/TEST_MAP.md`](regression-mcp-server/docs/TEST_MAP.md)
  (every test file's type, what it pins, and — the load-bearing column —
  what change would pass the whole suite unnoticed), both anchored to
  commit `7107c49fa305dde53ac3d6d0e009da67d773d859`. The per-class dossier
  directory (`regression-mcp-server/docs/classes/`) is now started: the
  first dossier is
  [`regression-mcp-server/docs/classes/TestRunCoordinator.md`](regression-mcp-server/docs/classes/TestRunCoordinator.md)
  (merged, PR #35). The review order in `ARCHITECTURE.md` puts
  `TestRunCoordinator` last-but-one deliberately — it was taken first this
  time by explicit instruction, ahead of the classes it depends on.
- `regression-jhipster`'s Playwright trace-capture gap (traces written to
  `target/playwright/traces/` are never surfaced through the MCP server) is
  now logged as item C2 in `docs/TECHNICAL_DEBT.md`, rather than only noted
  here as an earlier version of this file did.
- Forward-looking roadmap: see [`docs/ROADMAP.md`](docs/ROADMAP.md)
  (reactor-wide, grouped by module).

## Most recent session

2026-08-28 (latest) — characterization test for `TestRunCoordinator`'s
early-cause return terminal path, branch `test/coordinator-early-cause-path`
(PR open, not merged as of this entry). Stacked on PR #37 (the injectable
worker `ExecutorService` seam), merged to `master` earlier this session:

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
started — `TestRunCoordinator.md` is the first (merged, PR #35). Two
follow-ups are merged (PR #36 the `InterruptedException` characterization
test, which refuted concern O2; PR #37 the injectable worker
`ExecutorService` seam) and one is open on branch
`test/coordinator-early-cause-path` (the early-cause characterization test).
All four of `TestRunCoordinator.execute()`'s terminal paths are now
covered and the debt item that tracked the gap (B8) is retired. The
dossier itself still carries pending corrections (recorded in `output.log`,
extended each pass) — a separate documentation pass, not yet scheduled.
The concrete next step is to continue the dossier work following
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
with a filled-in estimate, B1 was the cheapest alongside B7 and was fixed
and removed from the catalogue — B7 remains the cheapest open item, and
its scheduling is not asserted here.
