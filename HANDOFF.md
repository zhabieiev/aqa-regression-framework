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
- `main.yml`'s `build-and-test` job's Maven step no longer runs with
  `continue-on-error: true` — it is now a real gate that fails the job when
  `regression-core` breaks.
- `regression-nextjs-commerce`'s dead Allure attachment fixture
  (`attachment.feature`, `AllureAttachmentFixtureSuite`,
  `AllureAttachmentFixtureSteps`, and the Surefire property that existed
  solely to feed it) was removed; it never actually ran under any real
  invocation.
- Known, accepted debt: see [`docs/TECHNICAL_DEBT.md`](docs/TECHNICAL_DEBT.md)
  (8 items, verified individually against that file rather than assumed:
  1 in `regression-nextjs-commerce`, 4 in `regression-mcp-server`, 1 in
  `regression-core`, and 2 in `regression-jhipster`).
- `regression-jhipster`'s Playwright trace-capture gap (traces written to
  `target/playwright/traces/` are never surfaced through the MCP server) is
  now logged as item 6 in `docs/TECHNICAL_DEBT.md`, rather than only noted
  here as an earlier version of this file did.
- Forward-looking roadmap: see [`docs/ROADMAP.md`](docs/ROADMAP.md)
  (reactor-wide, grouped by module).

## Most recent session

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
that recording: item 6 (`regression-jhipster`'s Playwright traces are
written only on scenario failure and are never captured by `ReportCapture`;
two independent barriers — the MIME allow-list and the Allure-only artifact
listing — would block serving one through the MCP server even if a third
staging root existed), item 7 (`regression_get_test_run`'s
`stdoutBytes`/`stderrBytes` are hardcoded to zero for the entire `RUNNING`
state and only populate at terminal persistence, so they carry no live
progress signal; `reason` also duplicated `state` at every observation in
the same recording), and item 8 (`regression_get_test_summary`'s
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
`docs/TECHNICAL_DEBT.md` as item 5, including a same-day bounded
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
