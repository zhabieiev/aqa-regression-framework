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

2026-08-19 — a two-phase documentation audit and implementation pass across
`CLAUDE.md`, `README.md`, `docs/TECHNICAL_DEBT.md`, `docs/ROADMAP.md`,
`regression-mcp-server/README.md`, `regression-mcp-server/docs/TOOLS.md`,
and `regression-jhipster/README.md`, aimed at multi-LLM/multi-agent
usability:

- Phase A (inspect/propose only): produced a prioritized findings report,
  appended to `output.log`.
- Phase B (implement, same day, explicitly authorized): applied the
  approved changes — a vendor-neutral MCP client configuration section, a
  vendor-neutral opening statement in `CLAUDE.md`, six new `CLAUDE.md`
  rules (prefer MCP tools over shell/grep, Phase A/B discipline,
  per-change git-write authorization, stop-and-report on unavailable
  tools, re-verify claims against source, JAR-lock pointer), a
  reactor-wide `docs/ROADMAP.md` folding in items that previously lived
  only in `README.md`, removal of unexplained Stage/Gate-number shorthand
  repository-wide (including several `regression-mcp-server` source-code
  comments), consolidation of `regression-jhipster`'s AI-agent guidance
  into its "Extension Guidelines" section, and this file. Committed and
  pushed directly to `master` (explicitly authorized — see the commit this
  file was introduced in). Full report appended to `output.log`.

## Next step

None of the items in `docs/ROADMAP.md` are authorized or scheduled — agree
one with the user before starting any of them. If nothing else is raised,
the smallest, most concrete starting point is `regression-petstore-api`'s
"Add a failure-safe fallback cleanup path for the Petstore delete
scenario" (see `docs/ROADMAP.md`'s "regression-petstore-api" section and
`regression-petstore-api/README.md`'s "Current Limitations and
Trade-offs" for the current gap).
