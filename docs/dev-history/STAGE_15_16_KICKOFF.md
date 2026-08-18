# Stage 15 + 16 Kickoff — Regression MCP Server v1.0

This document consolidates the agreed scope for Stage 15 (Architecture
Validator) and Stage 16 (final v1.0 acceptance) of `regression-mcp-server`,
plus the v1.0 Definition of Done and the process discipline that should
carry forward from Stages 13-14 into this work. It is a planning document
only: no Stage 15 tool, test, or production code exists yet as a result of
this document.

## Stage 15 — Architecture Validator

### Tools to add

- `regression_validate_module_boundaries`
- `regression_validate_framework_conventions`
- `regression_validate_architecture`

### Technical approach

- JavaParser for AST parsing.
- A Symbol Solver is added only if genuinely needed for a specific rule —
  do not add it speculatively ahead of a demonstrated requirement.

### Rule profiles

Rules are not applied globally; they are scoped per module using these
profiles:

- `CORE`
- `API`
- `UI`
- `API_UI`
- `MCP`
- `test-only`

### Module boundaries and scan scope

- Maven module boundaries are read from the POM, not hardcoded.
- `target/` and generated-sources directories are excluded from analysis.

## Stage 16 — Final v1.0 Acceptance

### Checks required

- `mvn -pl regression-mcp-server -am clean verify`
- `mvn validate`
- `git diff --check`
- `git status`

### CI matrix

- `ubuntu-latest`
- `windows-latest`

### Smoke test policy

External UI/API smoke tests must **not** run automatically in normal CI
without explicit authorization. The final real smoke run is performed
manually via MCP.

### Full user journey (manual validation before v1.0 sign-off)

overview -> modules -> features -> scenarios with filter -> start run ->
polling -> terminal state -> summary -> artifacts -> architecture
validation -> no orphan processes remaining.

### Documentation required for v1.0

- Installation
- IDEA/Codex configuration
- Tools and schemas
- Security model
- Execution lifecycle
- Run store
- Artifact limits
- JAR-lock troubleshooting
- v1.0 limitations

### Final steps

- README and `CLAUDE.md` (renamed from `AGENTS.md`) must be current.
- Git must be clean.
- Final PR.
- Merge to master.
- Version `1.0.0`.
- Optional tag `regression-mcp-server-v1.0.0`.

## Definition of Done (v1.0)

Regression MCP Server v1.0 is done when Codex, through a single MCP
server, without a shell and without direct repository reads, can:

- find tests in the Maven reactor,
- filter scenarios,
- safely start an authorized test,
- wait for the result,
- explain a failure from Surefire/Allure, and
- validate architecture rules.

Constraints that must hold throughout:

- No arbitrary command is accepted.
- Parameters are capability-validated.
- Paths are normalized.
- Results are isolated by `runId`.
- Stdout remains JSON-RPC-only.
- There are no orphan processes.
- Security tests pass on both Windows and Linux.
- Documentation matches the implementation.
- Git is clean.

## Process checklist carried forward from Stages 13-14

The following process discipline was proven across the original Stage
13/14 gates and again across the Stage 13-14 CI-fix work (the
`fix/ci-timeout-scheduling-failure-test-race` and
`fix/failure-artifact-store-test-windows-path-form` branches). Whoever
picks up Gate 15.0 should keep applying it rather than rediscovering it:

- Split each gate into a read-only Phase A (inspect, report, propose)
  before any Phase B (make the change) — do not interleave them.
- Give explicit examples at every gate boundary so it's unambiguous what
  is and isn't in scope for that step.
- Use unambiguous tense for status claims: distinguish what was already
  done (before this session) from what was just done (in this session) —
  never blur the two.
- Bundle related confirmation requests into one message rather than
  trickling them one at a time.
- On a genuine bug, stop and report it rather than silently fixing it
  in-flight — let the user decide.
- Never commit, push, branch, PR, or merge without fresh, per-action
  authorization; a prior approval does not carry forward to a new action.
- Keep verification commands bounded and run once (e.g. a scoped
  `mvn -pl ... -am clean verify` rather than an unbounded loop) and report
  the exact numbers produced.
- Every agent instruction that runs commands should include an
  `output.log`-appending step so the work is reviewable after the fact.
