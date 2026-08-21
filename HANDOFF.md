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
  in `regression-mcp-server/docs/TOOLS.md`.
- Known, accepted debt: see [`docs/TECHNICAL_DEBT.md`](docs/TECHNICAL_DEBT.md)
  (4 items, verified individually against that file rather than assumed:
  1 in `regression-nextjs-commerce`, 2 in `regression-mcp-server`, and 1 in
  `regression-core` — not all four are `regression-mcp-server`/
  `regression-nextjs-commerce` as an earlier version of this line claimed).
- Known gap, not yet logged in `docs/TECHNICAL_DEBT.md`: `regression-jhipster`'s
  Playwright trace `.zip` files (`UiHooks.java`, written via a raw
  filesystem call to `target/playwright/traces/`) are not captured by
  `ReportCapture`'s Allure/Surefire wiring (`ReportCapture.capture()` only
  reads `layout.surefireStaging()`/`layout.allureStaging()`) — an
  MCP-driven run's published artifacts never include a trace file, even
  when a UI scenario fails and one was written.
- Forward-looking roadmap: see [`docs/ROADMAP.md`](docs/ROADMAP.md)
  (reactor-wide, grouped by module).

## Most recent session

2026-08-20 to 2026-08-21 — closed `regression-jhipster`'s MCP-execution gap
end to end, then corrected the documentation drift that work exposed.
Commit range: `6dc8700..e0eed32` (`6dc8700` is the last commit of the
previously-documented 2026-08-19 README-gaps session above it; everything
from `6049a89` onward is this arc):

- Added `regression-jhipster`'s Maven-discoverable Cucumber suite runner
  and POM wiring, which had never existed (`6049a89`), then registered
  `regression-jhipster` as a second `ExecutionProfileRegistry` entry
  alongside `regression-nextjs-commerce` (`9529e1a`).
- Diagnosed and fixed a glue-path defect: `regression-jhipster`'s Cucumber
  glue list omitted `com.aqa.core.definitions`, the package holding the
  shared `GeneralDefinitions`/`StepArgumentConverters` step and converter
  classes feature files rely on for assertions and `@{...}`/`${...}`
  placeholder resolution. Missing it made 16 `@api` scenarios fail or come
  back undefined (`657f849`). The same latent gap was found and closed
  pre-emptively in `regression-nextjs-commerce` before it caused any real
  failure there — that module's own feature files never happened to
  exercise the missing package (`d8a40e7`).
- Untangled headless configuration for both UI-driving modules:
  `regression-jhipster`'s `pom.xml` originally forced `ui.headless=false`
  via Surefire `systemPropertyVariables`, which always pre-empted its own
  `dev.properties` value regardless of what that file said — a first pass
  made `true` the forced default instead (`2ed13ad`), then a follow-up
  pass removed the pom property entirely (`ef88a51`). `dev.properties` is
  now the single source of truth for headless mode in both
  `regression-jhipster` and `regression-nextjs-commerce`, with
  `-Dui.headless=<bool>` still working as a command-line override in
  either module via Maven Surefire's own default system-property
  forwarding to the forked test JVM — no per-module
  `systemPropertyVariables` entry required.
- Audited the shipped documentation (`docs/TOOLS.md`, both `README.md`
  files) against the code and fixed what had drifted: claims that
  `regression_start_test_run` only accepted `regression-nextjs-commerce`
  (stale the moment jhipster was registered), a report/artifact error-code
  note that collapsed three distinct codes (`RUN_NOT_FOUND`,
  `RUN_NOT_TERMINAL`, `NOT_FOUND`) into one incorrect blanket claim, and a
  security-model claim that overstated which of the three execution tools
  actually launch a process (`3c7ec68`).
- Corrected two client-facing strings an MCP client reads directly: the
  server-level `INSTRUCTIONS` text (previously claimed the server exposed
  "only deterministic inspection tools," despite three execution tools
  with real side effects) and `regression_get_test_run`/
  `regression_cancel_test_run`'s previously-shared, indistinguishable
  description (`f1159db`) — then added test coverage for both, since
  nothing had asserted on either string before (`ad6b6a8`).
- Merged as PR #12 (`eddcfe0`: the runner, registration, glue fix, and
  headless-default work) and PR #13 (`e0eed32`: the documentation audit,
  the client-facing string fixes, and the pom-property removal).

What this means for a newcomer: `regression-jhipster` is now a fully
working second MCP-executable module, on equal footing with
`regression-nextjs-commerce` — both are registered, both pass their full
Cucumber suites, both default to headless with a working `-D` override,
and the shipped documentation no longer singles out Commerce as the only
executable module. See "Next step" below for what's still gated (only
`regression-petstore-api` now) and "Current state" above for today's
freshly re-verified numbers.

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

Extending MCP-driven test execution to a third module
(`regression-petstore-api`) remains blocked on two things:

1. A user policy decision: `regression-mcp-server/README.md`'s "v1.0
   limitations" section states, as current shipped policy, that live API
   calls (`regression-petstore-api`) are manual-only by design and
   "should not be added to MCP execution without separate, explicit
   authorization." No implementation work on `regression-petstore-api`'s
   execution support should start without that authorization first.
2. A verified model/launch-path gap: `regression-petstore-api`'s POM does
   not wire the `mcp.surefire.reportsDirectory`/`mcp.allure.resultsDirectory`
   system properties (verified by direct grep of every module POM;
   re-checked 2026-08-21, still true — only `regression-jhipster` and
   `regression-nextjs-commerce` wire them today), so report capture would
   not work for it without additional POM or server-side work — not just a
   new `ExecutionProfileRegistry` entry.

If neither of those is being pursued next, the smallest independent
starting point remains `regression-petstore-api`'s "Add a failure-safe
fallback cleanup path for the Petstore delete scenario" (see
`docs/ROADMAP.md`'s "regression-petstore-api" section and
`regression-petstore-api/README.md`'s "Current Limitations and
Trade-offs" for the current gap).
