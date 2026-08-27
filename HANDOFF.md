# Handoff

A living file so a fresh agent session — any vendor, no conversation
history — can resume work immediately. Read this after `CLAUDE.md` at the
start of a session; update it at the end of one, per `CLAUDE.md`'s
"Verification and handoff" rules.

## Current state

- `regression-mcp-server` shipped as v1.0.0, tag `regression-mcp-server-v1.0.0`
  (commit `367fe27`).
- Build: green, re-verified 2026-08-21 by running each suite fresh (figures
  below are point-in-time, not a standing guarantee — re-run rather than
  trust the date if it's more than a few sessions old):
  - `mvn -pl regression-mcp-server -am test` — Tests run: 272, Failures: 0,
    Errors: 0, Skipped: 5.
  - `mvn -pl regression-jhipster -am test -Dcucumber.filter.tags="@api" -Denv=dev`
    — 16 Scenarios (16 passed), 37 Steps (37 passed); Surefire Tests run:
    21, Failures: 0, Errors: 0, Skipped: 5.
  - `mvn -pl regression-jhipster -am test -Dcucumber.filter.tags="@ui" -Denv=dev`
    — 5 Scenarios (5 passed), 24 Steps (24 passed).
  - `mvn -pl regression-jhipster -am test -Dcucumber.filter.tags="@hybrid" -Denv=dev`
    — 2 Scenarios (2 passed), 15 Steps (15 passed).
  - `mvn -pl regression-nextjs-commerce -am test -Denv=dev` — 2 Scenarios
    (2 passed), 15 Steps (15 passed).
  - `mvn validate` — BUILD SUCCESS across all 6 reactor modules.
  The `regression-jhipster` runs require its live app at `localhost:8080`
  to be reachable; confirmed via `curl` (HTTP 200) immediately before
  running.
- All 14 `regression-mcp-server` MCP tools (4 discovery, 3 execution, 4
  report/artifact, 3 architecture-validator) are implemented and documented
  in `regression-mcp-server/docs/TOOLS.md`. A real, verbatim client session
  against `regression-nextjs-commerce` was recorded 2026-08-22 and published
  as `regression-mcp-server/docs/SESSION_DEMO.md`.
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
  (30 items as of 2026-08-27, counted directly from the file's own `###`
  headers rather than trusted from this bullet's own prior figure — this
  bullet had drifted to "20 items as of 2026-08-25" when the file actually
  held 23 at that date, a staleness caught and corrected during the
  2026-08-27 session below; grouped into four sections by the
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
  commit `7107c49fa305dde53ac3d6d0e009da67d773d859`. A per-class dossier
  directory (`regression-mcp-server/docs/classes/`) is planned but not yet
  started; see "Next step" below for the order it should follow.
- `regression-jhipster`'s Playwright trace-capture gap (traces written to
  `target/playwright/traces/` are never surfaced through the MCP server) is
  now logged as item C2 in `docs/TECHNICAL_DEBT.md`, rather than only noted
  here as an earlier version of this file did.
- Forward-looking roadmap: see [`docs/ROADMAP.md`](docs/ROADMAP.md)
  (reactor-wide, grouped by module).

## Most recent session

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

The concrete next step for `regression-mcp-server` is starting the
per-class dossier directory (`regression-mcp-server/docs/classes/`),
following [`regression-mcp-server/docs/ARCHITECTURE.md`](regression-mcp-server/docs/ARCHITECTURE.md)'s
own review order (Group 1's tier-0 leaves first — e.g. `RepositoryRoot`,
`ModuleType`, `FailureArtifact` — through `RegressionMcpServer` last).
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
