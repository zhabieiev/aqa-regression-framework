# Repository Instructions for AI Coding Agents

## Authority and scope

- Treat the current Maven POM files and current implementation as authoritative.
- Preserve unrelated user changes. Do not use destructive Git commands such as `reset --hard` or `checkout --` unless the user explicitly requests them.
- Do not change generated files, source code, POM files, configuration, or module documentation unless the task authorizes those changes.
- When asked to provide changed code in chat, output the complete contents of every changed file; do not provide patches or archives in place of file contents.

## Maven and module boundaries

- Keep all code Java 21-compatible.
- Use dependency and plugin versions managed by the root `pom.xml`; do not introduce unmanaged versions when a managed dependency exists.
- The `regression` root POM is the parent and reactor aggregator. Product modules inherit from it.
- Do not create dependencies between sibling product modules: `regression-petstore-api`, `regression-jhipster`, and `regression-nextjs-commerce` must remain independent of one another.
- Reuse `regression-core` mechanisms when they apply. Keep product endpoints, product DTOs, product workflows, product cleanup, UI locators, and one-module-only reporting out of core unless demonstrated reuse justifies a shared abstraction.
- Do not add API clients, API services, or API scenarios to `regression-nextjs-commerce`.
- Do not edit OpenAPI- or Swagger-generated sources manually. Treat contract and generator changes as explicit, reviewable changes.
- Do not change the root OpenAPI Generator version for one module. Verify affected generation and compilation first, and use a module-scoped override only when it is required.

## Architecture

- Preserve the established direction: `Gherkin â†’ definitions â†’ steps â†’ pages/services â†’ components/clients`.
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

- Before editing, inspect the relevant POM, implementation, resource configuration, and module documentation.
- Before finishing, run the narrowest relevant Maven verification. For documentation-only work, use `mvn validate` unless a narrower task-specific Maven command is sufficient; for code changes, verify the affected module and its required reactor dependencies.
- Report only files actually changed and the verification result. State any runtime prerequisite or unverified external assumption explicitly.

## Regression MCP server

- Keep `regression-mcp-server` isolated from `regression-core` and every product module.
- Keep its transport local synchronous STDIO only: stdout is exclusively MCP JSON-RPC and diagnostics go only to stderr. Do not add HTTP transport, network access, shell/child-process execution, resources, prompts, sampling, or elicitation without explicit authorization.
- Accept repository scope only from `REGRESSION_ROOT`; do not introduce arbitrary-path tool inputs. Normalize the configured directory with `Path.toRealPath()` and require its root `pom.xml`.
- Keep tools closed-world, read-only, deterministic, schema-defined, and process-test their STDIO behavior when changing the server.
- Gherkin discovery tools must resolve only root-POM-declared modules and inspect only `<module>/src/test/resources/features`. Use the official Cucumber parser and Tag Expressions library; preserve UTF-8 input, Pickle-based scenario expansion, bounded file count/size, deterministic order, and repository/module/feature-root path and symlink containment.