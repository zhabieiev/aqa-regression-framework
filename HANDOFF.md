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

The smallest independent starting point remains `regression-petstore-api`'s
"Add a failure-safe fallback cleanup path for the Petstore delete
scenario" (see `docs/ROADMAP.md`'s "regression-petstore-api" section and
`regression-petstore-api/README.md`'s "Current Limitations and
Trade-offs" for the current gap).
