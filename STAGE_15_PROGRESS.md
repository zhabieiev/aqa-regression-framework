# Stage 15 Progress & Decisions Log

This file is the authoritative, up-to-date record of Stage 15 (Architecture
Validator) work — what's done, what was decided, and what's next. Read this
BEFORE starting any new gate. It exists so a fresh coding-agent session (no
prior conversation history) can resume work correctly without re-deriving
decisions already made.

Companion documents: `CLAUDE.md` (repo-wide agent rules — authoritative,
always follow), `README.md`, `STAGE_15_16_KICKOFF.md` (original Stage 15/16
scope). This file supersedes STAGE_15_16_KICKOFF.md wherever the two
disagree on a design detail — STAGE_15_16_KICKOFF.md is the original plan,
this file is what was actually decided/built.

## Process discipline for every gate (non-negotiable)

- One sub-phase (gate) at a time. Do not start the next gate's work inside
  this one.
- Split each gate into a read-only Phase A (inspect, verify assumptions,
  propose) before any Phase B (implement) — do not interleave them.
- If a genuine architectural/scope/visibility ambiguity is found, STOP and
  report it — do not silently resolve it, do not guess. State the finding
  in a few sentences (what's inconsistent, which class/method, 1-2 options)
  and wait for a decision. Purely tactical/local questions (naming, minor
  syntax, a small hardcoded-count fix) can be resolved directly with the
  user in this session without escalating.
- Never commit, branch, push, or merge without fresh, per-action
  authorization from the user. A prior approval does not carry forward to
  a new action.
- Every task run appends its full report to `output.log`.
- After a gate is ACCEPTED by the user, append one short entry to the
  "Decisions Log" section at the bottom of this file (what was built, key
  decisions, what's deferred) — a few lines, not a copy of output.log.
- Report exact verification numbers (test counts, diff stats) — never
  approximate.
- Distinguish clearly what was already done (before this session) from
  what was just done (in this session).
- Bundle related confirmation requests into one message rather than
  trickling them one at a time.

## Stage 15 architecture — accepted design (do not re-litigate)

**Three independent tools** (not an aggregator relationship):
`regression_validate_module_boundaries`, `regression_validate_framework_conventions`,
`regression_validate_architecture`.

**Technology**: JavaParser (`com.github.javaparser:javaparser-core:3.27.0`,
confirmed against Maven Central's JSON search API — re-verify if adding new
JavaParser usage in a future gate finds this stale). Symbol Solver added
only if a specific rule genuinely needs it — not added preemptively.

**Rule profiles**: `CORE, API, UI, API_UI, MCP, TEST_ONLY`. Module mapping
(derived at runtime from `ModuleList`/`ModuleTypeClassifier` via
`RuleProfileResolver`, never hardcoded):
- regression-core -> CORE
- regression-petstore-api -> API
- regression-jhipster -> API_UI
- regression-nextjs-commerce -> UI
- regression-mcp-server -> MCP
- TEST_ONLY: reserved for future modules with no production role; maps
  from `ModuleType.UNKNOWN` (conservative default — no CLAUDE.md role
  means no profile-specific rules fire). None exist today.

**Standing architectural principle** (confirmed by the user, applies to
all future rule design): this is a multi-module test-automation framework
where different sub-projects may legitimately use different tech
stacks/libraries. Concretely: `regression-jhipster`'s UI layer uses
**Playwright** (`com.microsoft.playwright.*`), NOT Selenium, while
`regression-nextjs-commerce` uses Selenium. Framework-convention rules must
be gated on actual imports found in the source (technology-precondition
detection), never assumed from `RuleProfile` alone. This is permanent
design, not a temporary workaround.

**Package**: all new code lives in `com.aqa.mcp.validation` (main + test).
Every pre-existing class in `com.aqa.mcp` / `com.aqa.mcp.execution` stays
untouched — a full package reorganization was proposed and explicitly
REJECTED (see Gate 15.0.5 below) because of real two-way package-private
coupling that would have required widening visibility across ~12
pre-existing classes for no clear benefit.

**Visibility rule going forward**: every class in `com.aqa.mcp.validation`
that Gate 15.3+'s `*Tool` classes or `RegressionMcpServer.java` need to call
across the package boundary must be `public`. This was audited and
confirmed correct as of Gate 15.2 — re-verify visibility explicitly any
time a new class is added that crosses the `com.aqa.mcp` / `com.aqa.mcp.validation`
boundary, rather than assuming.

## Gate status

| Gate | Scope | Status |
|---|---|---|
| 15.0 | Readiness check, module->profile grounding | DONE |
| mini-gate | Fix `assertNoSurvivor()` Windows fragility | DONE, merged to master |
| 15.0.5 | Package reorg readiness check | CLOSED — reorg rejected, see above |
| 15.1 | Validator design proposal | DONE, accepted |
| 15.2 | Chassis (JavaParser dep + 15 scaffolding classes) | DONE, accepted |
| 15.3 | `regression_validate_module_boundaries` end-to-end | DONE, accepted |
| 15.4 | `regression_validate_framework_conventions` end-to-end | DONE, accepted |
| 15.5 | `regression_validate_architecture` end-to-end | DONE, accepted |

## Current `com.aqa.mcp.validation` inventory (as of Gate 15.5, Stage 15 complete)

Main (21 classes): `RuleProfile`, `ModuleProfile`, `RuleProfileResolver`,
`ValidationException`, `ValidationScopeRequest`, `ValidatedValidationScope`,
`ValidationScopeValidator`, `SourceUnit`, `JavaSourceScanner`, `BasePackages`,
`Violation`, `EvaluationContext`, `ValidationRule`, `ModuleValidationResult`,
`ValidationReport`, `ModuleBoundaryRules` (package-private, only consumed
by `ModuleBoundariesTool`), `ModuleBoundariesTool` (public),
`FrameworkConventionRules` (package-private, only consumed by
`FrameworkConventionsTool`), `FrameworkConventionsTool` (public),
`ArchitectureRules` (package-private, only consumed by `ArchitectureTool`),
`ArchitectureTool` (public).

Test (20 classes): `RuleProfileResolverTest`, `ValidationScopeValidatorTest`,
`JavaSourceScannerTest`, `BasePackagesTest`, `ViolationTest`,
`ModuleProfileTest`, `EvaluationContextTest`, `ModuleValidationResultTest`,
`ModuleBoundaryRulesTest`, `ModuleBoundariesToolTest`,
`ModuleBoundariesToolContractTest`, `SourceUnitTest`,
`ValidatedValidationScopeTest`, `ValidationReportTest`,
`FrameworkConventionRulesTest`, `FrameworkConventionsToolTest`,
`FrameworkConventionsToolContractTest`, `ArchitectureRulesTest`,
`ArchitectureToolTest`, `ArchitectureToolContractTest`.

`RegressionMcpServer.java` now registers 14 tools (was 13). The 14th is
`regression_validate_architecture`. Stage 15's three validator tools —
`regression_validate_module_boundaries`, `regression_validate_framework_conventions`,
`regression_validate_architecture` — are all now shipped end to end.
`regression-mcp-server`'s own test suite totals 269 tests (0 failures, 0
errors, 5 pre-existing skips).

## Key technical facts learned (grounded in real code, not assumed)

- Every existing read-only MCP tool resolves module/POM data **fresh per
  request** inside its own `callHandler`, never once at server startup.
  This is load-bearing: an existing integration test
  (`returnsStructuredPomErrorsWithoutExposingFixturePathsAndRemainsUsable`)
  requires the server to keep starting and stay usable even with a
  malformed root `pom.xml`, failing only the tools that need module data,
  per-request. Any new tool needing module/POM data MUST follow this same
  `Supplier<...>`-per-request pattern, never eager computation at
  `createServer()` time.
- `regression-nextjs-commerce` has no `src/main/java` at all — 100% of its
  production framework code lives under `src/test/java`. Any source
  scanner must walk both `src/main/java` and `src/test/java` per module.
- `regression-jhipster`'s real `BasePage.java:46` already contains a
  `PlaywrightAssertions.assertThat(...)` call inside a page object — a
  live, pre-existing violation of the future ARCH-003 rule (assertions
  only in steps/test-layer). Useful as a real fixture reference for Gate
  15.5. Deliberately NOT fixed — out of scope for the validator project.
- The real reactor is currently clean of MOD-001..004 violations — all
  test fixtures for these rules are synthetic (`@TempDir`), not
  modifications to real product-module source.
- `com.aqa.mcp.execution`'s report-shaped classes (`ReportCapture`,
  `PublishedReportIndex`, `SurefireSummaryParser`, `AllureResultParser`)
  have genuine two-way package-private coupling with execution-shaped
  classes (`CaptureMetadata`, `RunStore.AtomicMover`, etc.) — this is why
  the Gate 15.0.5 package split was rejected. Do not re-propose it without
  re-confirming this coupling still holds.

## Open items carried into Gate 15.4

- Framework-convention rules FC-001 (Selenium `By` locator fields),
  FC-002 (no `Thread.sleep`), FC-005 (no static mutable WebDriver/page
  state) are Selenium-specific — must be gated on
  `imports org.openqa.selenium.*`, not on profile alone.
- Given the confirmed "different tech stacks per sub-project" principle,
  `regression-jhipster`'s Playwright-based UI code likely deserves its own
  parallel rule set (mirroring FC-001/002/005 but for
  `com.microsoft.playwright.Page`/`Locator`) as a first-class case, not a
  deferred edge case. Design this explicitly in Gate 15.4's Phase A rather
  than skipping Playwright-specific conventions.
- FC-003 (constructor injection) and FC-004 (records for value objects) are
  profile-agnostic; FC-004 ships advisory-only in its first cut (accepted
  in Gate 15.1) given its heuristic nature.
- A `WaitManager`-style abstraction exists only in
  `regression-nextjs-commerce` (`com.aqa.nextjscommerce.waits.WaitManager`);
  `regression-core` has `com.aqa.core.utils.WaitUtils`, a *different* class
  that legitimately calls `Thread.sleep` inside its own polling loop — any
  FC-002 rule needs an explicit, reviewable allow-list of such legitimate
  wait-abstraction implementation files, or it will false-positive on
  correct existing code.

## Gate 15.5 — Phase A design (ACCEPTED; Phase B not yet started)

Proposed and accepted in Phase A discussion. This section is the durable
written record of that design, since a fresh session must not rely on
conversation history that isn't persisted here. Full supporting inspection
detail lives in `output.log`'s "GATE 15.5 -- Phase A" entry; this section is
the authoritative summary of what was accepted, not a copy of that log.

**Layer-identification convention**: a class's architectural layer is read
from its package's last segment (`definitions`, `steps`, `pages`,
`services`, `components`). `clients` is an empty layer in CLAUDE.md's
vocabulary today (no module currently has a `.clients` package) — this is
a non-gap, not something ARCH rules need to account for.

**ARCH-001** (layering: definitions must not reach past steps into
pages/services/components): for a class whose package's last segment is
`definitions`, flag a `MethodCallExpr` whose immediate scope is a field of
that class where the field's declared type's package — resolved via
`ImportDeclaration`, no Symbol Solver — ends in `.pages`, `.services`, or
`.components`, or whose declared type is a Selenium `By`/Playwright
`Locator`/`Page` type directly. Single-hop, field-declared-type-only
detection. Known accepted gap, documented rather than fixed: two-hop calls
like `regression-core`'s `S3Definitions.s3Steps.s3ServiceActions()
.getObject(...)` will not be caught, because catching them would require a
Symbol Solver, which Phase A explicitly declined to add speculatively
ahead of a demonstrated per-rule requirement.

**ARCH-002** (no package dependency cycles): a per-module directed
package-dependency graph — nodes are packages taken from each
`SourceUnit`'s own `CompilationUnit.getPackageDeclaration()`, edges are
module-internal imports resolved via `BasePackages` — checked with
DFS-with-recursion-stack cycle detection. One `Violation` is emitted per
package participating in a detected cycle, listing the full cycle
sequence. The rule is module-structure-agnostic by construction (it does
not depend on layering vocabulary) and applies uniformly to every module,
including `regression-mcp-server` and `regression-core`. Real-reactor
cleanliness was **not** manually pre-verified in Phase A — Phase B's own
new real-reactor-facing test is what establishes whether the reactor is
cycle-free today, not an assumption carried in from Phase A.

**ARCH-003** (no assertions in pages/components): flags
`assertThat`/`Assertions.assert*` calls — matched against each file's own
static `ImportDeclaration`s (AssertJ, Playwright's `PlaywrightAssertions`,
JUnit `Assertions`) — in classes whose package's last segment is `pages`
or `components`. Confirmed in Phase A to have a **non-clean real
baseline**: 18 real violation call sites across 7 files, all in
`regression-jhipster` (`BasePage.java`, `LoginPage.java`,
`BankAccountPage.java`, `BankAccountFormPage.java`, `BaseComponent.java`,
`DataTableComponent.java`, `NavigationBar.java`), zero in
`regression-nextjs-commerce`. This is expected, correct validator
behavior surfacing pre-existing debt — not a bug in the rule, and not
something Phase B should suppress or special-case.

**ARCH-004** (BasePage should stay thin): advisory severity only, reusing
the same `advisoryViolations`-bucketing output-schema mechanism
`FrameworkConventionsTool` already established for FC-004 in Gate 15.4 —
not reinvented for this gate. Flags a `BasePage`-named/suffixed class with
more than 8 public/protected methods, excluding constructors and private
helpers. **Method-count-only threshold**, calibrated against both real
`BasePage` classes (`regression-jhipster`'s at 4 methods,
`regression-nextjs-commerce`'s at 7 methods — both pass at the `>8`
threshold). Gate 15.1's original two-pronged design (count OR a
name-vocabulary heuristic) was dropped in Phase A after the vocabulary
half was shown to false-positive on `regression-nextjs-commerce`'s
legitimate `header()`/`cart()`/`currentUrl()`/`title()` accessor methods.

**Informational only, no rule, no action**: `regression-core`'s
`GeneralDefinitions.java` makes direct AssertJ assertions inside
`@Then`-annotated methods — a real CLAUDE.md violation, but one not
covered by any of the four scoped ARCH rules above. Noted for awareness;
out of scope for Gate 15.5.

**Profile scoping**: all four rules declare
`EnumSet.allOf(RuleProfile.class)` for `profiles()` — matching
MOD-001..004 and FC-001..005's existing pattern — with real applicability
governed entirely by each rule's own internal package/import-presence
gating rather than any profile restriction. Confirmed via direct package
inspection that `regression-mcp-server` has zero
definitions/steps/pages/services/components packages (ARCH-001 and
ARCH-003 self-gate to zero matches there, same mechanism as FC-001/
FC-001-PW self-gating on absent imports) and that `regression-core` has
definitions/steps/services but no pages/components (ARCH-001's layering
check still applies there; ARCH-003 naturally never fires there).

**Tool shape**: `ArchitectureTool` follows the identical per-request
`Supplier<Map<String,String>>` wiring pattern as `ModuleBoundariesTool`/
`FrameworkConventionsTool` (see "Key technical facts learned" above) — no
new wiring pattern is introduced for this gate.

## Gate 15.5 — Phase B implementation notes

Implemented `ArchitectureRules` (ARCH-001..004) and `ArchitectureTool` exactly
per the Phase A design above, registered as the 14th tool in
`RegressionMcpServer.java` via the same additive, per-request-`Supplier`
pattern as Gates 15.3/15.4. 30 new tests (`ArchitectureRulesTest` — Tier 1,
21 tests; `ArchitectureToolTest` — Tier 2, 6 tests; `ArchitectureToolContractTest`
— Tier 2, 3 tests), bringing the reactor's `regression-mcp-server` total to
269 tests (0 failures, 0 errors, 5 pre-existing skips) — up from Gate 15.4's
239.

**A key discovery from Phase B's own ARCH-002 real-reactor test**:
`regression-nextjs-commerce` has a real, previously-unknown two-package
cycle — `com.aqa.nextjscommerce.config.UiSettings` imports
`com.aqa.nextjscommerce.driver.BrowserType`, while `driver`'s
`ChromeOptionsFactory`, `DriverSession`, and `DriverFactory` all import
`config.UiSettings`. Per the Gate 15.5 standing instruction, this was
stopped on and reported rather than silently worked around. The user's
explicit decision (not a unilateral agent choice): accept it as known,
pre-existing debt — the real-reactor ARCH-002 test now asserts that
*exactly* this one cycle is present (both participating files by name) and
nothing else in the reactor has a cycle, so the test still fails the
build the moment any *different* cycle appears anywhere. Fixing the
underlying `regression-nextjs-commerce` cycle itself was explicitly
deferred to a separate, out-of-scope, not-yet-authorized task — this gate
is scoped to `regression-mcp-server` only and does not touch product
module source.

ARCH-003's real-reactor test confirmed the expected 18 violations across
exactly the 7 named `regression-jhipster` files, exactly as Phase A
predicted — no surprise there.

Real-repository-facing test pattern established (new to this suite, no
prior validation test pointed at the real filesystem): walk up from the
test JVM's working directory until a directory is found containing both a
`pom.xml` and a `regression-mcp-server` subdirectory. This works whether
Surefire's working directory is the module's own basedir (the default for
`mvn -pl regression-mcp-server -am clean verify`) or the reactor root
itself, without hardcoding either shape or an environment variable.
Documented in `ArchitectureToolTest`'s class javadoc.

`git diff --check` is clean. The diff is scoped to: the five new
`com.aqa.mcp.validation` Architecture* files (2 main, 3 test), the
additive `RegressionMcpServer.java` registration, and the mechanical
13→14 tool-count fix plus one added tool-name constant in
`RegressionMcpServerStdioIntegrationTest.java` — no other files touched
this gate.

## Decisions Log

_(Append one short entry here after each gate is accepted by the user —
what was built, key decisions, what's deferred. Do not paste full reports;
those live in output.log / the conversation history.)_

- 2026-08-1x — Gates 15.0 through 15.3 completed and accepted. See sections
  above for full state. Package reorg (15.0.5) proposed and rejected.
  Chassis (15.2) and module-boundaries tool (15.3) shipped, verified,
  merged pending user's own git actions. Next: Gate 15.4.

- 2026-08-18 — Gate 15.4 completed and accepted (Phase A design + Phase B
  implementation). Built `FrameworkConventionRules` (FC-001, FC-001-PW,
  FC-002, FC-002-PW, FC-003, FC-004, FC-005) and `FrameworkConventionsTool`,
  registered as the 13th tool in `RegressionMcpServer.java`; 41 new tests
  (239 total, 0 failures, 0 errors, 5 pre-existing skips). Key decisions:
  the Playwright-parallel rule set (FC-001-PW, FC-002-PW) shipped as
  first-class in this same gate rather than deferred; FC-004's advisory/
  non-blocking findings are bucketed into a separate `advisoryViolations`
  JSON array in the tool's own output rather than extending the shared
  `Violation` record's schema; FC-003 was narrowed from a broad
  "non-final field" heuristic to annotation- and setter-injection
  detection specifically, to avoid false-positiving on legitimate
  scenario-local mutable state. Nothing deferred out of this gate. Next:
  Gate 15.5.

- 2026-08-18 — Gate 15.5 completed and accepted (Phase A design + Phase B
  implementation), closing out Stage 15. Built `ArchitectureRules`
  (ARCH-001 definitions-layer discipline, ARCH-002 package-dependency
  cycles, ARCH-003 no assertions in pages/components, ARCH-004 advisory
  thin-BasePage) and `ArchitectureTool`, registered as the 14th tool in
  `RegressionMcpServer.java`; 30 new tests (269 total, 0 failures, 0
  errors, 5 pre-existing skips). Key decisions: ARCH-001 ships single-hop
  field-declared-type-only detection with no Symbol Solver, a known
  accepted gap for two-hop calls like `S3Definitions.s3Steps
  .s3ServiceActions().getObject(...)`; ARCH-004 ships a method-count-only
  `>8` threshold (Gate 15.1's original count-OR-name-vocabulary design was
  dropped after the vocabulary half false-positived on
  `regression-nextjs-commerce`'s legitimate accessor methods); ARCH-002's
  own real-reactor test surfaced a genuine, previously-unknown package
  cycle between `com.aqa.nextjscommerce.config` and
  `com.aqa.nextjscommerce.driver` — per explicit user decision this was
  accepted as known, documented debt rather than fixed (the test now
  asserts exactly that one cycle, not a clean baseline), and fixing the
  underlying `regression-nextjs-commerce` cycle itself is deferred to a
  separate, not-yet-authorized task. Nothing else deferred out of this
  gate. Stage 15 (Architecture Validator) is now complete: all three
  validator tools (`regression_validate_module_boundaries`,
  `regression_validate_framework_conventions`,
  `regression_validate_architecture`) are shipped, verified, and merged.
  Next: Stage 16 (final v1.0 acceptance).