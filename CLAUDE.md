# Repository Instructions for AI Coding Agents

This file applies to any AI coding agent working in this repository — Claude
Code, Codex, Copilot, Cursor, Windsurf, Gemini, or any other — regardless of
whether that tool auto-discovers it by filename. If your tooling does not
load this file automatically, the user will point you at it explicitly;
treat it as binding regardless of how you were pointed at it.

## Authority and scope

- Treat the current Maven POM files and current implementation as authoritative.
- Re-verify factual claims (file contents, test results, tool behavior) against the current source or a live tool call before relying on them, even when they come from a prior session's summary, memory, or report.
- Preserve unrelated user changes. Do not use destructive Git commands such as `reset --hard` or `checkout --` unless the user explicitly requests them.
- Do not create commits, push, or otherwise write to git history unless the user explicitly authorizes it for the current change; a prior authorization does not carry forward to unrelated changes.
- Do not change generated files, source code, POM files, configuration, or module documentation unless the task authorizes those changes.
- When asked to provide changed code in chat, output the complete contents of every changed file; do not provide patches or archives in place of file contents.

## Maven and module boundaries

- Keep all code Java 21-compatible.
- Use dependency and plugin versions managed by the root `pom.xml`; do not introduce unmanaged versions when a managed dependency exists.
- The `regression` root POM is the parent and reactor aggregator. Product modules inherit from it.
- Do not create dependencies between sibling product modules: `regression-petstore-api`, `regression-jhipster`, and `regression-nextjs-commerce` must remain independent of one another.
- Reuse `regression-core` mechanisms when they apply. Keep product endpoints, product DTOs, product workflows, product cleanup, UI locators, and one-module-only reporting out of core unless the same abstraction is confirmed by a second module, not just proposed for one.
- Do not add API clients, API services, or API scenarios to `regression-nextjs-commerce`.
- Do not edit OpenAPI- or Swagger-generated sources manually. Treat contract and generator changes as explicit, reviewable changes.
- Do not change the root OpenAPI Generator version for one module. Verify affected generation and compilation first, and use a module-scoped override only when it is required.

## Architecture

- Preserve the established direction: `Gherkin → definitions → steps → pages/services → components/clients`.
- Choose JUnit for technical checks and Cucumber when Gherkin is a useful executable specification; do not force one runner model onto every module.
- Keep Cucumber definitions thin: bind Gherkin input, convert it when necessary, and delegate. Do not place browser calls, locators, assertions, services, or business branching in definitions.
- Keep API transport, request construction, and response deserialization in services/clients. Keep scenario and business orchestration in steps.
- Keep assertions in the appropriate steps or test layer. Do not put assertions in page objects.
- Use constructor injection for collaborators. Avoid service locators and hidden mutable dependencies.
- Use records for small immutable value objects, scenario input, snapshots, and expected state. Use mutable Beans only when framework binding, mapping, staged construction, or nested mutable data requires them.
- Keep `BasePage` methods genuinely universal. Do not turn `BasePage` into a general utility class.

## Selenium and UI safety

- For Selenium pages and components, declare static locators as `private static final By` fields at the top of the class. Use dedicated factory methods for dynamic locator templates.
- Use the shared explicit-wait abstraction (`WaitManager`) for Selenium synchronization. Do not introduce `Thread.sleep`; explicit wait duration is framework configuration used by Selenium `WebDriverWait`, not a substitute for Selenium itself.
- Scope complex component searches to their component root. Prefer `SearchContext`-compatible abstractions and root scoping for reusable components and open Shadow DOM.
- Preserve the scenario-scoped `DriverSession` lifecycle and safe browser cleanup in Cucumber hooks. DriverSession and page objects must remain scenario-local so concurrent scenarios cannot share WebDriver state.
- Do not introduce static mutable `WebDriver`, browser, page-object, or scenario-state instances.
- Preserve parallel-execution safety: identify ownership of mutable state, test-data uniqueness, external shared resources, and cleanup behaviour before enabling or changing parallel execution.
- Keep `CommerceScenarioContext` scenario-scoped. Standardize its usage if it is refactored, but keep it limited to data that must cross step-class boundaries.
- `PageContext` and component root scoping are possible future improvements only where a concrete need exists and their impact has been verified; do not add or broaden them speculatively.

## Verification and handoff

- At the start of a new session, read `CLAUDE.md` and `HANDOFF.md` first, then run the narrowest relevant Maven verification to confirm the environment is working and the documented state is accurate before starting new work.
- Before editing, inspect the relevant POM, implementation, resource configuration, and module documentation.
- When the regression-framework MCP tools are connected, prefer them over shell commands or grep for module/feature/scenario discovery and for checking the architecture/convention/boundary rules in this file — they are deterministic and schema-defined where ad hoc shell exploration is not.
- When a task is framed as inspection, audit, or proposal work, do not make edits or commits even if a fix looks obvious — deliver findings and let the user authorize a separate implementation pass.
- If a required tool, MCP server, or connection is unavailable, stop and report that fact rather than fabricating output as if it succeeded.
- Before finishing, run the narrowest relevant Maven verification. For documentation-only work, use `mvn validate` unless a narrower task-specific Maven command is sufficient; for code changes, verify the affected module and its required reactor dependencies.
- Report only files actually changed and the verification result. State any runtime prerequisite or unverified external assumption explicitly.
- Distinguish current implementation from roadmap intent in any documentation you write or update; do not describe planned or proposed safeguards as already available.
- Update `HANDOFF.md` at the end of a working session so the next session (any agent, any vendor) can resume without conversation history.
- `output.log` at the repository root is the single local, gitignored working log. Agents append their reports to it; it is not repository content. It is cleared periodically by the user, so it is not an archive and nothing should depend on its history. It must never be cited as the location of findings in `HANDOFF.md`, `CLAUDE.md`, or any other committed document, because a fresh clone will not have it — substantive findings go directly into the committed document.
- Do not record which AI tool produced a change anywhere in the project's own record. Keep out of commit messages, PR titles, PR bodies, and every committed file: co-author trailers naming a model (such as a `Co-Authored-By:` line for an assistant), links or identifiers pointing at an agent or tool session, and "generated with" footers or emoji badges. Repository history and pull requests exist to record what changed and why, not which tool typed it. Some agent harnesses append these lines by default, so actively suppress them and re-check your own commit messages and PR text before finishing rather than assuming none were added. This does not restrict ordinary prose that names an AI tool where the tool is the subject — for example `regression-mcp-server/README.md` listing MCP clients, or this file addressing AI coding agents.

## Regression MCP server

- Keep `regression-mcp-server` isolated from `regression-core` and every product module.
- Keep its transport local synchronous STDIO only: stdout is exclusively MCP JSON-RPC and diagnostics go only to stderr. Only the server-owned direct Java/Classworlds launcher in the `execution` package may launch a process; it must never launch a shell or accept command, path, or argument input from MCP clients.
- Accept repository scope only from `REGRESSION_ROOT`; do not introduce arbitrary-path tool inputs. Normalize the configured directory with `Path.toRealPath()` and require its root `pom.xml`.
- Keep inspection tools closed-world, read-only, deterministic, schema-defined, and process-test their STDIO behavior when changing the server. The three explicitly authorized execution tools are the sole exception: their client schemas remain closed, they accept no command/path/runtime input, and their lifecycle must be process-tested through STDIO.
- Gherkin discovery tools must resolve only root-POM-declared modules and inspect only `<module>/src/test/resources/features`. Use the official Cucumber parser and Tag Expressions library; preserve UTF-8 input, Pickle-based scenario expansion, bounded file count/size, deterministic order, and repository/module/feature-root path and symlink containment.
- Preserve the module's security evidence: all tool schemas and error envelopes remain closed and structured; production sources must not add repository writes, process/shell execution, or network access. Keep representative STDIO tests for JSON-only stdout, recovery after errors, fixture immutability, bounded cleanup, EOF shutdown, and no application child process. The Ubuntu MCP CI job must fail if any MCP security test is skipped, including the symlink-escape test.
- Before rebuilding `regression-mcp-server`, stop any live MCP client connection to it first — see `regression-mcp-server/README.md`'s JAR-lock troubleshooting section for why a running server process blocks the rebuild on Windows.
