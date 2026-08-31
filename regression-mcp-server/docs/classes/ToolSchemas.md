# Class dossier: `ToolSchemas`

Anchor: `master` at the commit this file is committed under. Read against the
tree, not against `ARCHITECTURE.md`'s summary or any prior report. Line numbers
are given only where a claim needs one, with the line of code quoted beside it;
everything else is stated structurally so it survives an unrelated edit.

Source read in full this pass:
`regression-mcp-server/src/main/java/com/aqa/mcp/ToolSchemas.java` (139 lines,
`wc -l`),
`regression-mcp-server/src/main/java/com/aqa/mcp/RegressionMcpServer.java`
(427 lines), `regression-mcp-server/src/main/java/com/aqa/mcp/ModuleType.java`,
`regression-mcp-server/src/test/java/com/aqa/mcp/RegressionMcpServerContractTest.java`,
`regression-mcp-server/src/test/java/com/aqa/mcp/RegressionMcpServerStdioIntegrationTest.java`,
and `regression-mcp-server/src/test/java/com/aqa/mcp/ReadOnlyProductionBoundaryTest.java`.

---

## 1. IDENTITY

| Field | Value |
|---|---|
| Path | `regression-mcp-server/src/main/java/com/aqa/mcp/ToolSchemas.java` |
| Lines | 139 (`wc -l`) |
| Kind | `final class ToolSchemas` — package-private, a stateless collection of `static` JSON Schema builders with a `private` no-arg constructor |
| Package | `com.aqa.mcp` — not a sub-package. `moduleListOutputSchema` calls `ModuleType.schemaValues()`, and `enum ModuleType` and its `static List<String> schemaValues()` are both package-private to `com.aqa.mcp`; a sub-package would not compile without widening `ModuleType`, which is undesirable (see §5) |
| Nested types | none |
| Tier | 1 — depends only on the JDK collections API and the package-private enum `ModuleType`. Not yet listed in `ARCHITECTURE.md`'s class inventory (a separate pass) |
| Fan-in | **1** — only `RegressionMcpServer` references it, at 20 call sites (§6). No test names it |
| Contract-exposure bucket | **A — shape-visible.** These methods *are* the client-facing tool schema: each return value is handed to `io.modelcontextprotocol.spec.McpSchema.Tool.Builder` and serialized verbatim into the `tools/list` JSON-RPC response. A change to any method body is a change to the published contract |

**History.** The class was created on 2026-08-31 by extracting the 18 schema
builders whole out of `RegressionMcpServer` — the commit this file is committed
under, on branch `refactor/extract-tool-schemas`. Every method body is
byte-for-byte what it was in `RegressionMcpServer`; the only per-method change is
the access modifier on the eleven methods that widened from `private` to
package-private (§3, §4). Before the extraction, the builders' own history is
`RegressionMcpServer.java`'s: `inputSchema` and `outputSchema` were born
package-private in `bdd68fa` ("feat: add local regression MCP server");
`moduleListOutputSchema` in `a0aaf3d` ("Add Maven module discovery MCP tool");
the remaining builders accreted across the commits that added each MCP tool
(`08ce653` Gherkin discovery, `5217e0f` execution lifecycle, `4537f52`
report/artifact). No commit before the extraction ever added a caller of any
builder outside `RegressionMcpServer`.

---

## 2. RESPONSIBILITY

**Owns:** construction of the closed JSON Schema documents attached to every MCP
tool `RegressionMcpServer` registers — the `inputSchema` handed to
`Tool.builder(name, inputSchema)` and the `outputSchema` handed to
`.outputSchema(...)`, including the shared success/failure `oneOf` envelope
(`structuredOutputSchema`) and the small fragment builders (`stringArray`,
`artifactSchema`, `artifactSchemaProperties`).

**Must never own:**

- Tool assembly — `SyncToolSpecification.builder()`, `Tool.builder(...)`,
  annotations, call handlers. Stays in `RegressionMcpServer`'s `*Tool(...)`
  factory methods. Respected: this class imports only `java.util.List` and
  `java.util.Map` and returns plain `Map`s.
- The tool-name constants (`OVERVIEW_TOOL_NAME` …
  `READ_FAILURE_ARTIFACT_TOOL_NAME`). None of the 18 methods reads one.
  Respected.
- Response-payload mapping — `runOutput`, `summaryOutput`,
  `failureSummaryOutput`, `featureOutput`, `scenarioOutput`, `artifactOutput`
  and the rest of the `*Output(...)` family in `RegressionMcpServer`. Those
  convert domain objects to `Map` at call time; schema construction is a
  separate concern and was not part of the extraction. Respected.
- Argument validation — `moduleArgument`, `startRequest`, `runId`,
  `artifactArguments`, `tagExpression` in `RegressionMcpServer`. Respected.
- The schemas' *content*. The extraction moved the builders unchanged; it did
  not alter a single schema.

---

## 3. PUBLIC SURFACE

18 `static` methods, all returning `Map<String, Object>`. Column "Called from"
distinguishes the fourteen invoked directly by a `RegressionMcpServer` member
from the four reached only through other builders in this class.

| Method | Builds | Called from |
|---|---|---|
| `moduleInputSchema(boolean allowTags)` *(pkg-priv)* | input schema for the feature/scenario list tools; adds an optional `tags` property when `allowTags` | `RegressionMcpServer.featureListTool` (`moduleInputSchema(false)`), `RegressionMcpServer.scenarioListTool` (`moduleInputSchema(true)`) |
| `startInputSchema()` *(pkg-priv)* | input schema for `regression_start_test_run` (`module`, `tags`, `environment`, `headless`, `timeoutSeconds`) | `RegressionMcpServer.startTestRunTool` |
| `runIdInputSchema()` *(pkg-priv)* | the closed one-field `{runId}` input schema | `RegressionMcpServer.testSummaryTool`, `.failureSummaryTool`, `.failureArtifactsTool`, `.runActionTool` (the shared get/cancel helper) — four sites |
| `runOutputSchema()` *(pkg-priv)* | output schema for a run snapshot | `RegressionMcpServer.startTestRunTool`, `.runActionTool` — two sites |
| `summaryOutputSchema()` *(pkg-priv)* | output schema for the Surefire summary | `RegressionMcpServer.testSummaryTool` |
| `failureSummaryOutputSchema()` *(pkg-priv)* | output schema for bounded failure records plus Allure enrichment | `RegressionMcpServer.failureSummaryTool` |
| `artifactSchemaProperties()` *(private)* | the five shared artifact-metadata properties | `artifactSchema`, `readArtifactOutputSchema` (this class only) |
| `artifactSchema()` *(private)* | one artifact-metadata object | `artifactsOutputSchema` (this class only) |
| `artifactsOutputSchema()` *(pkg-priv)* | output schema for the artifact listing | `RegressionMcpServer.failureArtifactsTool` |
| `readArtifactOutputSchema()` *(pkg-priv)* | artifact metadata plus a base64 `content` string | `RegressionMcpServer.readFailureArtifactTool` |
| `artifactInputSchema()` *(pkg-priv)* | the closed `{runId, artifactId}` input schema | `RegressionMcpServer.readFailureArtifactTool` |
| `featureOutputSchema()` *(pkg-priv)* | output schema for parsed Gherkin features | `RegressionMcpServer.featureListTool` |
| `scenarioOutputSchema()` *(pkg-priv)* | output schema for executable scenarios | `RegressionMcpServer.scenarioListTool` |
| `structuredOutputSchema(Map dataProperties, List<String> requiredData)` *(private)* | the shared `oneOf` of a `{status:"ok", data:…}` success object and a `{status:"error", error:{code,message}}` failure object; wraps `dataProperties`/`requiredData` in the success branch | nine of the ten output builders above (all except the two artifact fragment builders) — this class only |
| `stringArray()` *(private)* | the fragment `{"type":"array","items":{"type":"string"}}` | `featureOutputSchema`, `scenarioOutputSchema` (this class only) |
| `inputSchema()` *(pkg-priv)* | the no-argument `{"type":"object","additionalProperties":false}` | `RegressionMcpServer.overviewTool`, `.listModulesTool` — two sites |
| `outputSchema()` *(pkg-priv)* | output schema for the framework overview | `RegressionMcpServer.overviewTool` |
| `moduleListOutputSchema()` *(pkg-priv)* | output schema for the reactor module list; its `type` enum is `ModuleType.schemaValues()` | `RegressionMcpServer.listModulesTool` |

### 3.1 Internal call graph

Acyclic, maximum depth three (`artifactsOutputSchema` → `artifactSchema` →
`artifactSchemaProperties`). `structuredOutputSchema` has nine in-class callers;
`artifactSchemaProperties` and `stringArray` two each; `artifactSchema` one. The
four `private` members (`structuredOutputSchema`, `stringArray`, `artifactSchema`,
`artifactSchemaProperties`) are exactly the set with no caller outside the class.

### 3.2 Visibility

Fourteen methods are package-private because `RegressionMcpServer` calls them
across the package-mate boundary; `private` would not compile. Four are `private`
because only other builders in this class call them. Relative to the pre-extraction
state in `RegressionMcpServer`:

| Group | Methods | Was | Is |
|---|---|---|---|
| Widened for the move | `moduleInputSchema`, `startInputSchema`, `runIdInputSchema`, `runOutputSchema`, `summaryOutputSchema`, `failureSummaryOutputSchema`, `artifactsOutputSchema`, `readArtifactOutputSchema`, `artifactInputSchema`, `featureOutputSchema`, `scenarioOutputSchema` — **11** | private | package-private |
| Unchanged, still internal | `artifactSchemaProperties`, `artifactSchema`, `structuredOutputSchema`, `stringArray` — **4** | private | private |
| Unchanged, already package-private | `inputSchema`, `outputSchema`, `moduleListOutputSchema` — **3** | package-private | package-private |

`inputSchema`, `outputSchema` and `moduleListOutputSchema` were package-private in
`RegressionMcpServer` **with no caller that required it** — every call was
in-class, so `private` would have compiled, and neither the git history nor any
reference in the tree explained the wider visibility. The extraction is the event
that supplies a caller (the cross-class call from `RegressionMcpServer`) for which
package-private is the correct visibility. For those three the move widens
nothing.

**Accepted cost — package-private no longer implies a test seam for these
methods.** Elsewhere in this module, package-private visibility on a member is a
reliable signal that a same-package test reaches it: the `*_TOOL_NAME` constants
and the `*Tool(...)` factory methods in `RegressionMcpServer` are package-private
for that reason, and `RegressionMcpServerContractTest` and
`RegressionMcpServerStdioIntegrationTest` call those factories directly by simple
name. The eleven widened builders here carry package-private visibility **without**
that meaning — no test calls any of them (§8); the sole caller is
`RegressionMcpServer`. This cost was accepted rather than treated as a blocker:
the alternative was to keep a single-responsibility block of schema builders
welded to an otherwise-unrelated class purely to keep the convention pure. It is
mitigated by the call-site style (§6): an explicit `ToolSchemas.` qualifier at
every one of the 20 sites, never a static import, so one `grep` still shows
`RegressionMcpServer` as the only caller. Read package-private on these methods as
"same-package caller in `RegressionMcpServer`", not as "exercised by a same-package
test".

---

## 4. STATE AND INVARIANTS

None. Every method is `static`, reads no field and no `this`, and constructs only
local data from literals plus one pure static call, `ModuleType.schemaValues()`.
`moduleInputSchema` and `readArtifactOutputSchema` build a local
`java.util.LinkedHashMap`, mutate only that local, and return an immutable copy or
embed it — nothing escapes. There is no synchronization, ordering, or lifecycle
concern.

One implicit invariant: the `Map` returned by every top-level output builder — via
`structuredOutputSchema` — is a JSON Schema object with `oneOf` at its root. The
contract tests in §8 assert exactly that (`.containsKey("oneOf")`).

---

## 5. DEPENDENCIES OUT

| Dependency | How referenced | Sites |
|---|---|---|
| `java.util.Map` | `import java.util.Map;` | pervasive — `Map.of`, `Map.ofEntries`, `Map.entry`, `Map.copyOf` |
| `java.util.List` | `import java.util.List;` | `List.of(...)` throughout |
| `java.util.LinkedHashMap` | fully-qualified, not imported | two: `moduleInputSchema` (`new java.util.LinkedHashMap<>()`) and `readArtifactOutputSchema` (`new java.util.LinkedHashMap<>(artifactSchemaProperties())`). Kept fully-qualified as they were in `RegressionMcpServer` — the extraction changed no body text |
| `com.aqa.mcp.ModuleType` | bare name, same package | one: `moduleListOutputSchema`, `Map.of("type", "string", "enum", ModuleType.schemaValues())` |

No static imports. No constant, no field, no other production class. No method
reads mutable state.

`ModuleType` is the constraint on the class's package. `ModuleType.java` declares
`enum ModuleType {` and `static List<String> schemaValues() {` — both
package-private to `com.aqa.mcp`. `ToolSchemas` must live in `com.aqa.mcp` for
`moduleListOutputSchema` to compile; a sub-package would force `ModuleType` wider.

---

## 6. DEPENDENTS IN

`RegressionMcpServer` is the only caller, at **20 sites** — ten members, each
building exactly one input schema and one output schema:

| `RegressionMcpServer` member | input-schema call | output-schema call |
|---|---|---|
| `overviewTool` | `ToolSchemas.inputSchema()` | `ToolSchemas.outputSchema()` |
| `listModulesTool` | `ToolSchemas.inputSchema()` | `ToolSchemas.moduleListOutputSchema()` |
| `featureListTool` | `ToolSchemas.moduleInputSchema(false)` | `ToolSchemas.featureOutputSchema()` |
| `scenarioListTool` | `ToolSchemas.moduleInputSchema(true)` | `ToolSchemas.scenarioOutputSchema()` |
| `startTestRunTool` | `ToolSchemas.startInputSchema()` | `ToolSchemas.runOutputSchema()` |
| `testSummaryTool` | `ToolSchemas.runIdInputSchema()` | `ToolSchemas.summaryOutputSchema()` |
| `failureSummaryTool` | `ToolSchemas.runIdInputSchema()` | `ToolSchemas.failureSummaryOutputSchema()` |
| `failureArtifactsTool` | `ToolSchemas.runIdInputSchema()` | `ToolSchemas.artifactsOutputSchema()` |
| `readFailureArtifactTool` | `ToolSchemas.artifactInputSchema()` | `ToolSchemas.readArtifactOutputSchema()` |
| `runActionTool` (shared by `getTestRunTool`, `cancelTestRunTool`) | `ToolSchemas.runIdInputSchema()` | `ToolSchemas.runOutputSchema()` |

`runIdInputSchema` accounts for four of the twenty, `runOutputSchema` for two,
`inputSchema` and `moduleInputSchema` for two each; the rest are one apiece.

**Call-site style: an explicit `ToolSchemas.` qualifier at every site, never a
static import.** A static import was available and would compile, but two earlier
read-only inspection passes established "no caller outside `RegressionMcpServer`"
by grepping the builder names in six forms (§"Hypotheses"), and a bare
`inputSchema()` / `outputSchema()` collides with same-named methods on the MCP SDK
`Tool` object and on the three `com.aqa.mcp.validation` tool classes (§11 O2). The
explicit qualifier keeps `grep -F "ToolSchemas."` a complete, unambiguous account
of every caller. That property is why the qualifier was chosen.

---

## 7. CONTRACT EXPOSURE

Bucket **A — shape-visible**. The `Map` each method returns is:

1. passed to `Tool.builder(name, inputSchema)` / `.outputSchema(outputSchema)` in
   a `RegressionMcpServer` `*Tool(...)` factory;
2. assembled by `McpServer.sync(...)....tools(...).build()` in
   `RegressionMcpServer.createServer(RepositoryRoot, TestRunCoordinator)`;
3. serialized into `result.tools[].inputSchema` / `.outputSchema` of the
   `tools/list` JSON-RPC response the MCP client reads.

`regression-mcp-server/docs/TOOLS.md` documents these schemas per tool. The
extraction changed no body, so the serialized schema of all fourteen tools is
byte-for-byte identical to the pre-extraction output — confirmed by the tests in
§8 and the run in §12.

---

## 8. TEST COVERAGE

No test calls any `ToolSchemas` method by name. Two test classes assert on the
schemas these methods produce, both by reading the assembled artefact rather than
the builder:

- **`RegressionMcpServerContractTest`** (package `com.aqa.mcp`) builds a
  `SyncToolSpecification` via a `RegressionMcpServer` factory (e.g.
  `RegressionMcpServer.overviewTool(validRoot())`) and asserts on
  `toolSpecification.tool().inputSchema()` / `.tool().outputSchema()` — the MCP
  SDK `Tool` accessors. Methods:
  `exposesAnExplicitNoArgumentInputContractAndStructuredOutputContract`,
  `exposesTheReadOnlyModuleListContract`,
  `exposesClosedReadOnlyFeatureAndScenarioContracts`,
  `exposesTheClosedReadOnlySurefireSummaryContract`,
  `exposesTheClosedReadOnlyFailureSummaryContract`,
  `exposesTheClosedReadOnlyFailureArtifactsContract`,
  `exposesTheClosedReadOnlyReadFailureArtifactContract`. They check the exact
  input-schema map and that each output schema `.containsKey("oneOf")`.
- **`RegressionMcpServerStdioIntegrationTest`** (package `com.aqa.mcp`) starts the
  real server over STDIO and reads the serialized `tools/list` JSON; its helpers
  `assertToolList` / `assertExecutionToolContracts` assert
  `tool.path("inputSchema").path("additionalProperties")` is `false` for every
  emitted tool. This is the end-to-end shape check.

Because both read the assembled `Tool` / the wire response, moving the builders to
a new class is invisible to them — confirmed green in §12.

`ReadOnlyProductionBoundaryTest.productionSourcesDoNotContainWriteProcessOrNetworkBoundaries`
walks `src/main/java/com/aqa/mcp` and asserts every `.java` file — `ToolSchemas.java`
included — contains none of a list of write/process/network tokens
(`ProcessBuilder`, `java.net`, `Files.write`, `FileWriter`, `cmd.exe`, …).
`ToolSchemas` contains only `Map`/`List`/`LinkedHashMap` construction and
`ModuleType.schemaValues()`; it passes.

`ArchitectureToolContractTest`, `FrameworkConventionsToolContractTest`,
`ModuleBoundariesToolContractTest` (package `com.aqa.mcp.validation`) use the same
`.tool().inputSchema()` / `.tool().outputSchema()` accessor pattern for *their*
tools — evidence the accessor style is module-wide, not that they touch this class.

---

## 9. FAILURE BEHAVIOUR

None of its own: no I/O, no parsing, no process, no network. A programming error in
a body — a malformed schema, a duplicate key to `Map.of` — surfaces at
server-assembly time as an `IllegalArgumentException` from `Map.of`, or as a
`Tool.Builder` validation failure, or as a red
`RegressionMcpServerContractTest` / `RegressionMcpServerStdioIntegrationTest`.
There is no silent-failure path.

---

## 10. RESOURCES AND LIFECYCLE

None. No method opens a file, socket, process, or thread; nothing to close. Each
is invoked once, at server-assembly time, inside the `.tools(...)` argument list of
`RegressionMcpServer.createServer(RepositoryRoot, TestRunCoordinator)`; the `Map`
it returns is retained for the life of the `McpServer`. The extraction changed
neither when nor how often any method runs.

---

## 11. OBSERVATIONS

### O1 — three methods were package-private in `RegressionMcpServer` with no caller that needed it

`inputSchema`, `outputSchema`, `moduleListOutputSchema` were declared without an
access modifier while every call site was in-class (`RegressionMcpServer` is
`public final` and unextended, so no inheritance path). `private` would have
compiled. The git history shows all three created package-private with no later
commit adding an external caller. The extraction resolves this: package-private is
now the visibility the cross-class call from `RegressionMcpServer` requires. See
§3.2.

### O2 — `inputSchema` / `outputSchema` name collisions (known false positives)

Grepping the bare names `inputSchema(` / `outputSchema(` across the repository
returns hits that are **not** `ToolSchemas` calls:

1. `com.aqa.mcp.validation.ArchitectureTool`,
   `com.aqa.mcp.validation.FrameworkConventionsTool`,
   `com.aqa.mcp.validation.ModuleBoundariesTool` each declare their own
   `private static Map<String, Object> inputSchema()` / `outputSchema()` and call
   them unqualified within their own class.
2. In the test sources, every hit is `toolSpecification.tool().inputSchema()` or
   `tool.tool().outputSchema()` — accessors on the MCP SDK `Tool` object from
   `SyncToolSpecification.tool()`.
3. `RegressionMcpServerStdioIntegrationTest` has the string literal
   `"inputSchema"` twice — `tool.path("inputSchema")` and
   `summary.path("inputSchema")` — navigating the serialized response by its
   protocol field name.

A reader greping these names will re-encounter all three; recorded so the
boundary of the real reference set stays clear. `grep -F "ToolSchemas."` avoids
every collision, which is why the call sites use the explicit qualifier (§6).

### O3 — the four `private` helpers move only as a unit

`structuredOutputSchema`, `artifactSchema`, `artifactSchemaProperties`,
`stringArray` are called only by other builders in this class. They are `private`
because the whole set moved together. A partial extraction — some builders here,
some left in `RegressionMcpServer` — would have forced them wider or duplicated.
All 18 moved.

---

## 12. EXTRACTION AND VERIFICATION

**What was done.** On 2026-08-31, the 18 methods were cut whole from the
contiguous block they occupied in `RegressionMcpServer.java` (which ran from
`private static Map<String, Object> moduleInputSchema(boolean allowTags) {` to the
close of `moduleListOutputSchema`, between `readArtifactResult` and
`readOnlyAnnotations` — both non-schema builders) and pasted into this class,
bodies unchanged, with the eleven modifier changes of §3.2. `RegressionMcpServer`
lost the block with no blank-line scar and gained a `ToolSchemas.` qualifier at
each of the 20 call sites (§6). `RegressionMcpServer.java` went from 547 to 427
lines by `wc -l`; `ToolSchemas.java` is 139.

**Baseline, measured on the unchanged tree** (`master` at `18064cf`, before any
edit), `mvn -pl regression-mcp-server -am test`: **Tests run: 280, Failures: 0,
Errors: 0, Skipped: 5**, BUILD SUCCESS. Observed fact: the first invocation of
that command failed one test —
`TestRunCoordinatorTest.retainedChildIsRemovedWhenParentExitsBeforeCoordinatorCleanup`
(asserts owned-process set size `>= 2`, observed 1), the recurrence catalogued as
`docs/TECHNICAL_DEBT.md` item D15, in `com.aqa.mcp.execution` and unrelated to
these builders — and a single re-run of the identical command was green at
280 / 0 / 0 / 5.

**After the extraction**, the identical command: **Tests run: 280, Failures: 0,
Errors: 0, Skipped: 5**, BUILD SUCCESS. The five skips are unchanged, by name:
`com.aqa.mcp.FeatureDiscoveryTest.rejectsSymlinkedFeatureFilesThatEscapeTheFeatureRoot`,
`com.aqa.mcp.ModuleListTest.rejectsSymlinkedModulePathsEscapingTheRepositoryRoot`,
`com.aqa.mcp.execution.ReportCaptureTest.rejectsSymlinkEscapesAndCleansOwnedStaging`,
`com.aqa.mcp.execution.RunStoreTest.symlinkedStatusTargetIsRejectedWithoutFollowingIt`,
`com.aqa.mcp.validation.JavaSourceScannerTest.rejectsSymlinkedSourceFilesThatEscapeTheSourceRoot`
— each an `org.opentest4j.TestAbortedException` because the local Windows account
cannot create symbolic links; all five execute on the Linux CI runner, where
`.github/workflows/main.yml`'s step "Require all MCP security tests to execute"
fails the build if any is skipped.

**Call sites confirmed by grep**: `grep -F "ToolSchemas."` over
`RegressionMcpServer.java` returns exactly 20 lines, matching §6.

Same total, same failures, same errors, same skip count, same skip names before
and after: the extraction changed nothing observable through any MCP tool.

---

## Hypotheses — confirm / refute

The durable findings from the two read-only inspection passes that preceded the
extraction. Kept because they are the reason this move was safe and the reason
the document is worth committing.

| # | Statement | Verdict | Basis |
|---|---|---|---|
| **H1** | No code outside `RegressionMcpServer.java` calls any of the 18 builders | **CONFIRMED** | Six search forms across the whole repo: qualified call `RegressionMcpServer.<name>` → none; bare call / declaration `<name>(` → only `RegressionMcpServer.java` (plus the O2 collisions for `inputSchema`/`outputSchema`); method reference `RegressionMcpServer::` → only the payload mappers; `import static com.aqa.mcp.RegressionMcpServer` → none; string literal `"<name>"` → none except two `JsonNode.path("inputSchema")` wire-field reads; reflection (`getDeclaredMethod`, `getMethod(`, `.class.getDeclared`) → none targeting `RegressionMcpServer` |
| **H2** | No reflective lookup targets `RegressionMcpServer` | **CONFIRMED** | The three `getDeclaredMethod` hits are `com.aqa.mcp.execution` tests against other classes; the one `getMethod(` hit is `regression-core`'s HTTP-method getter |
| **H3** | The new class could live in a sub-package of `com.aqa.mcp` | **REFUTED** | `moduleListOutputSchema` calls the package-private `ModuleType.schemaValues()`; a sub-package would not compile without widening `ModuleType` |
| **H4** | The four internal helpers can be `private` in the new class | **CONFIRMED, conditional** | True because all 18 moved together (O3) |
| **H5** | The move forces some `private` methods wider | **CONFIRMED** | Eleven builders called from `RegressionMcpServer`'s factories widened `private` → package-private (§3.2) |
| **H6** | The contract / STDIO tests would break if the builders changed class | **REFUTED** | Both read the assembled `Tool` / the serialized `tools/list`, never the builder by name; the run in §12 confirms green |
| **H7** | `ReadOnlyProductionBoundaryTest` takes the new file as input | **CONFIRMED (input), REFUTED (failure)** | It walks `src/main/java/com/aqa/mcp`; `ToolSchemas.java` is in scope and contains none of the forbidden tokens; green in §12 |
| **H8** | The git history explains why `inputSchema` / `outputSchema` / `moduleListOutputSchema` were package-private | **REFUTED** | Both born package-private (`bdd68fa`, `a0aaf3d`); no commit adds an external caller; no explanation in history or tree |

---

## What this dossier does not cover

- **A caller outside the repository.** The searches cover this working tree only.
  `regression-mcp-server` has no published API and no sibling-module dependency
  (forbidden by `CLAUDE.md`), so a package-private method has no possible external
  caller in practice, but that was not independently proven.
- **`docs/TOOLS.md` diffed against the live schemas.** The shape-invariance
  guarantee rests on the contract and STDIO tests (§8, §12), not on a
  documentation diff.
- **`ARCHITECTURE.md`'s class inventory.** Not updated in the extraction pass;
  `ToolSchemas` should be added to it in a later documentation pass.
- **Search-form boundary.** Verified: qualified call, bare call / declaration,
  method reference, static import, string literal, reflection. Not verified: a
  running-classpath scan, an annotation processor or code generator synthesising a
  reference, or a build-plugin configuration naming a method — none of which exist
  in this module, but none exhaustively excluded by tooling.
