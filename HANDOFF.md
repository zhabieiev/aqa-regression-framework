# Handoff

A living file so a fresh agent session — any vendor, no conversation
history — can resume work immediately. Read this after `CLAUDE.md` at the
start of a session; update it at the end of one, per `CLAUDE.md`'s
"Verification and handoff" rules.

## Current state

- `regression-mcp-server` shipped as v1.0.0, tag `regression-mcp-server-v1.0.0`
  (commit `367fe27`).
- Build: green. `mvn -pl regression-mcp-server -am clean verify` — 269 tests
  run, 0 failures, 0 errors, 5 skipped (confirmed 2026-08-19). `mvn validate`
  passes across all 6 reactor modules.
- All 14 `regression-mcp-server` MCP tools (4 discovery, 3 execution, 4
  report/artifact, 3 architecture-validator) are implemented and documented
  in `regression-mcp-server/docs/TOOLS.md`.
- Known, accepted debt: see [`docs/TECHNICAL_DEBT.md`](docs/TECHNICAL_DEBT.md)
  (4 items, all `regression-mcp-server`/`regression-nextjs-commerce`-related).
- Forward-looking roadmap: see [`docs/ROADMAP.md`](docs/ROADMAP.md)
  (reactor-wide, grouped by module).

## Most recent session

2026-08-19 (later same day) — a two-phase pass on top-level README gaps and
naming consistency, following up on the multi-LLM/multi-agent audit above:

- Phase A (inspect/report only): researched whether MCP-driven test
  execution could extend beyond `regression-nextjs-commerce`, and gathered
  the runtime-prerequisites facts the top-level README lacked. Findings
  are folded into "Next step" below.
- Phase B (implement, same day, explicitly authorized): closed the
  documentation-only gaps from that report — added a top-level `README.md`
  "## Prerequisites" section, a `regression-mcp-server` row to the modules
  table, a specific (not generic) warning before the `mvn test` command
  list naming which module needs what, a concrete `-Denv=dev` usage
  example, and a one-line Prerequisites pointer in
  `regression-nextjs-commerce/README.md` (the one module README with no
  prerequisites callout of its own). Also normalized
  `regression-nextjs-commerce/pom.xml` by removing its one-off `<name>`
  element (no other module POM declares one, so the reactor summary now
  shows `regression-nextjs-commerce` consistently instead of "Regression -
  Next.js Commerce UI"); `artifactId` was not touched. Checked the
  repository for references to the old GitHub repository name
  (`myJavaTests`) — none found; `git remote -v` already points at
  `zhabieiev/aqa-regression-framework`. Committed and pushed directly to
  `master` (explicitly authorized).

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
   system properties (confirmed by direct grep of every module POM this
   session — only `regression-jhipster` and `regression-nextjs-commerce`
   wire them today), so report capture would not work for it without
   additional POM or server-side work — not just a new
   `ExecutionProfileRegistry` entry.

If neither of those is being pursued next, the smallest independent
starting point remains `regression-petstore-api`'s "Add a failure-safe
fallback cleanup path for the Petstore delete scenario" (see
`docs/ROADMAP.md`'s "regression-petstore-api" section and
`regression-petstore-api/README.md`'s "Current Limitations and
Trade-offs" for the current gap).
