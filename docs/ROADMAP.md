# Roadmap

This file is a forward-looking handoff for whoever (human or AI agent)
picks up this project next. It orients a fresh session to where things
stand and where the concrete opportunities are, without requiring
conversation history that no longer exists.

For known, accepted debt — what's imperfect today and why it was left that
way — see [`docs/TECHNICAL_DEBT.md`](TECHNICAL_DEBT.md). This file does not
repeat that list; read it first for context on what's already known and
deliberately deferred.

## Possible next steps

None of the following are authorized or scheduled work — they are
concrete, code-pointer-backed opportunities for a future task, each
requiring its own scoping and explicit authorization before any change is
made (per `CLAUDE.md`'s repository instructions).

### Extend test execution beyond `regression-nextjs-commerce` / `dev`

Today, `regression_start_test_run` supports exactly one module and one
environment. `ExecutionProfileRegistry`
(`regression-mcp-server/src/main/java/com/aqa/mcp/execution/ExecutionProfileRegistry.java`)
hardcodes a single-entry `PROFILES` map:

```java
private static final ExecutionProfile COMMERCE = new ExecutionProfile(COMMERCE_MODULE,
        "regression-nextjs-commerce/pom.xml", List.of("dev"), true);
private static final Map<String, ExecutionProfile> PROFILES = Map.of(COMMERCE_MODULE, COMMERCE);
```

Adding a second module or environment means:
- Adding another `ExecutionProfile` entry to `PROFILES` (module name,
  module POM path, allowed `environments` list, `supportsHeadless` flag).
- `TestRunRequestValidator`
  (`regression-mcp-server/src/main/java/com/aqa/mcp/execution/TestRunRequestValidator.java`)
  already delegates module/environment gating entirely to
  `ExecutionProfileRegistry.requireProfile(...)`, so it needs no change for
  a same-shaped new profile — only for a genuinely new validation rule
  (e.g. a module needing a different headless/timeout policy shape).
- `RegressionMcpServer.java`'s `regression_start_test_run` tool wiring
  passes the declared reactor module list into the validator already
  (`declaredModules`), so a new module just needs to exist as a real
  reactor module and get an `ExecutionProfile` entry — no separate
  allowlist to update there.
- `TestRunCoordinator` and the Maven process-launch path
  (`DirectMavenProcessLauncher`, `MavenInvocationFactory`) currently only
  have `regression-nextjs-commerce`'s Selenium/`dev` shape exercised
  end-to-end; a new module's Maven invocation (system properties, profile
  activation, etc.) needs to be verified explicitly, not assumed to work
  from the `regression-nextjs-commerce` case alone.
- Before authorizing, check whether other product modules
  (`regression-petstore-api`, `regression-jhipster`) even have a Maven
  profile/execution shape suitable for MCP-driven runs — this is a
  reactor-wide question, not just an `ExecutionProfileRegistry` edit.

### Extract the shared validator `Tool` helper

`docs/TECHNICAL_DEBT.md` item 3 names the duplicated methods across
`ModuleBoundariesTool`, `FrameworkConventionsTool`, and `ArchitectureTool`:
`evaluate`, `parseRequest`, `reportOutput`, `moduleResultOutput`,
`violationOutput`, `inputSchema`, `violationSchema`, `moduleResultSchema`,
`outputSchema`, `readOnlyAnnotations`, `successResult`, `errorResult`,
`serialize`. A future refactor could extract a shared
`ValidationToolSupport`-style helper (static methods or a small
package-private class in `com.aqa.mcp.validation`) that each `Tool` class
calls into, leaving only the per-tool rule set and tool name/description
in each `Tool` class itself. Scope carefully: the three classes' `evaluate`
methods differ in which `ValidationRule` list they evaluate against, and
their `outputSchema`/`violationSchema` differ slightly in field sets
(e.g. `FrameworkConventionsTool` and `ArchitectureTool` both support an
`advisoryViolations` bucket that `ModuleBoundariesTool` does not) — a
faithful extraction needs to preserve those differences exactly, verified
against each tool's existing contract tests
(`ModuleBoundariesToolContractTest`, `FrameworkConventionsToolContractTest`,
`ArchitectureToolContractTest`) which lock in current output shape.

### Scope an ARCH rule for `definitions`-layer assertions

`docs/TECHNICAL_DEBT.md` item 4 notes that `GeneralDefinitions.java`'s
direct AssertJ assertions inside `@Then` methods are real CLAUDE.md debt
with no current validator rule covering it. A future ARCH-005 (or similar)
rule in `ArchitectureRules.java` could flag assertion calls
(`assertThat`/`Assertions.assert*`, matched the same way ARCH-003 already
matches them via each file's own `ImportDeclaration`s) inside classes whose
package's last segment is `definitions`. This would need its own Phase A
design pass: unlike ARCH-003's `pages`/`components` scope, a
`definitions`-layer assertion rule needs to decide whether *any* assertion
in a `definitions` class is a violation, or only ones not delegated from a
`steps`-layer call — that distinction was never designed, only noted as
informational.

## Where things live

`regression-mcp-server`'s module layout, for a new agent that needs to
navigate the codebase without rediscovering it from scratch:

```
regression-mcp-server/
  pom.xml
  README.md                          - install, client config, security model,
                                        execution lifecycle, run store, limits
  docs/TOOLS.md                       - full input/output schema per MCP tool
  src/main/java/com/aqa/mcp/
    RegressionMcpServer.java          - server entry point: builds the MCP
                                         server, registers every tool, wires
                                         each tool's Supplier<Map<String,String>>
                                         module-data dependency
    RepositoryRoot.java,
    RepositoryRootResolver.java       - resolves REGRESSION_ROOT, requires a
                                         root pom.xml, normalizes via
                                         Path.toRealPath()
    ModuleList.java,
    ModuleType.java,
    ModuleTypeClassifier.java         - reads declared reactor modules and
                                         classifies each by CLAUDE.md role
    FeatureDiscovery.java             - Gherkin feature/scenario discovery
                                         (Cucumber parser + Pickle expansion)
    FrameworkOverview.java            - the overview tool's data assembly
    ExecutionPlanningFactory.java     - assembles execution-planning
                                         collaborators for the server

    execution/                        - the three Stage 13 execution tools'
                                         supporting code: request validation
                                         (TestRunRequestValidator,
                                         ExecutionProfile,
                                         ExecutionProfileRegistry), the
                                         direct-launcher process path
                                         (DirectMavenProcessLauncher,
                                         MavenInvocationFactory,
                                         MavenRuntimeConfiguration[Loader]),
                                         process ownership/cleanup
                                         (ProcessOwnershipTracker,
                                         OwnedProcessIdentity,
                                         ObservedProcess/SystemProcessView),
                                         the run store and lifecycle
                                         (RunStore, RunId, RunSnapshot,
                                         TestRunState, TestRunCoordinator),
                                         and result capture/reporting
                                         (ReportCapture, SurefireSummaryParser,
                                         AllureResultParser,
                                         PublishedReportIndex, ArtifactContent,
                                         PublicDiagnosticSanitizer)

    validation/                       - the three Stage 15 architecture/
                                         convention validator tools: rule
                                         profiles and resolution
                                         (RuleProfile, ModuleProfile,
                                         RuleProfileResolver), AST scanning
                                         (JavaSourceScanner, SourceUnit,
                                         BasePackages), the shared rule/report
                                         model (ValidationRule,
                                         EvaluationContext, Violation,
                                         ModuleValidationResult,
                                         ValidationReport), the three fixed
                                         rule lists (ModuleBoundaryRules,
                                         FrameworkConventionRules,
                                         ArchitectureRules — each
                                         package-private, consumed only by
                                         its own Tool class), and the three
                                         public Tool classes
                                         (ModuleBoundariesTool,
                                         FrameworkConventionsTool,
                                         ArchitectureTool)

  src/test/java/com/aqa/mcp/
    execution/, validation/           - mirror the main-source packages
                                         above; includes STDIO
                                         integration/contract tests
                                         (e.g. RegressionMcpServerStdioIntegrationTest)
                                         that process-test the server's
                                         actual JSON-RPC stdout behavior
```

New tools are registered in `RegressionMcpServer.java` following the
existing additive pattern: build a `SyncToolSpecification` via the tool
class's own static `tool(...)` factory method, passing in whatever
`Supplier<Map<String,String>>` or `Path repositoryRoot` the tool needs
resolved fresh per request (never cached at server-startup time — see the
"per-request, not eager" pattern already used by every existing tool).
