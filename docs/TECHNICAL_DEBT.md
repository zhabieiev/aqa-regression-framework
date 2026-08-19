# Technical Debt

This is a living reference of known, accepted debt in the repository. Each
item below was deliberately left in place rather than fixed, because fixing
it was out of scope for the work that surfaced it. None of these are
blockers for any shipped functionality. This file has no stage/gate numbers
and no dated narrative — when an item is fixed, delete it from this file
rather than marking it done.

## 1. Package-dependency cycle in `regression-nextjs-commerce`

**What**: `com.aqa.nextjscommerce.config.UiSettings` imports
`com.aqa.nextjscommerce.driver.BrowserType`, while `driver`'s
`ChromeOptionsFactory`, `DriverSession`, and `DriverFactory` all import
`config.UiSettings` back — a two-package import cycle.

**Location**:
- `regression-nextjs-commerce/src/test/java/com/aqa/nextjscommerce/config/UiSettings.java`
- `regression-nextjs-commerce/src/test/java/com/aqa/nextjscommerce/driver/BrowserType.java`
- `regression-nextjs-commerce/src/test/java/com/aqa/nextjscommerce/driver/ChromeOptionsFactory.java`
- `regression-nextjs-commerce/src/test/java/com/aqa/nextjscommerce/driver/DriverSession.java`
- `regression-nextjs-commerce/src/test/java/com/aqa/nextjscommerce/driver/DriverFactory.java`

**Why accepted**: `regression_validate_architecture`'s ARCH-002 rule
(package-dependency cycles) found this pre-existing cycle when its
real-reactor test was first written. Untangling `config` and `driver`
requires a `regression-nextjs-commerce`-only refactor that was explicitly
out of scope for `regression-mcp-server` validator work. `ArchitectureTool`
does not special-case or suppress it: `ArchitectureToolTest`'s real-reactor
ARCH-002 test asserts that *exactly* this one cycle exists (both
participating files named explicitly) and fails the build the moment any
*different* cycle appears anywhere in the reactor, so this item stays
visible rather than silently tolerated.

**Status**: present, unfixed, guarded by a real-reactor regression test.

## 2. ARCH-001 layering rule is single-hop only (no Symbol Solver)

**What**: `regression_validate_architecture`'s ARCH-001 rule (a
`definitions`-package class must not reach past `steps` into
`pages`/`services`/`components`) flags a `MethodCallExpr` only when its
immediate scope is a field of the `definitions` class whose *declared*
type resolves — via `ImportDeclaration`, not a Symbol Solver — to a
`pages`/`services`/`components` package, or is a Selenium `By`/Playwright
`Locator`/`Page` type directly. Two-hop calls that reach through an
intermediate object, e.g. `regression-core`'s
`S3Definitions.s3Steps.s3ServiceActions().getObject(...)`, are not caught.

**Location**: `regression-mcp-server/src/main/java/com/aqa/mcp/validation/ArchitectureRules.java`
(ARCH-001 rule implementation, see the class javadoc and the ARCH-001
method's own doc comment).

**Why accepted**: JavaParser's Symbol Solver was deliberately not added
speculatively; the project's standing rule is that a Symbol Solver is
added only once a specific rule demonstrates a genuine need for full type
resolution. Single-hop, declared-type-only detection was accepted as
adequate first-cut coverage.

**Status**: present, unfixed. Adding full two-hop (and deeper) detection
would require introducing a Symbol Solver into `ArchitectureRules`, which
has not been authorized.

## 3. Structural duplication across the three validator `Tool` classes

**What**: `ModuleBoundariesTool`, `FrameworkConventionsTool`, and
`ArchitectureTool` each independently implement the same set of methods —
`evaluate`, `parseRequest`, `reportOutput`, `moduleResultOutput`,
`violationOutput`, `inputSchema`, `violationSchema`, `moduleResultSchema`,
`outputSchema`, `readOnlyAnnotations`, `successResult`, `errorResult`, and
`serialize` — with near-identical bodies. Each file is roughly 180-195
lines, of which an estimated 120-140 lines per file are duplicated
schema/envelope/evaluation-loop boilerplate rather than logic specific to
that tool's rule set.

**Location**:
- `regression-mcp-server/src/main/java/com/aqa/mcp/validation/ModuleBoundariesTool.java`
- `regression-mcp-server/src/main/java/com/aqa/mcp/validation/FrameworkConventionsTool.java`
- `regression-mcp-server/src/main/java/com/aqa/mcp/validation/ArchitectureTool.java`

**Why accepted**: each tool shipped independently across separate gates of
work, and consolidating the shared shape into a common helper was judged a
separate, purely-internal refactor with no effect on any tool's external
schema or behavior — not worth bundling into the gate that shipped the
third tool. See `docs/ROADMAP.md` for a concrete extraction proposal.

**Status**: present, unfixed. Does not affect any tool's external
behavior or schema; internal-only debt.

## 4. Direct AssertJ assertions inside `GeneralDefinitions`'s `@Then` steps

**What**: `regression-core`'s `GeneralDefinitions` record makes direct
`assertThat(...)` (AssertJ) calls inside its own `@Then`-annotated Cucumber
step methods (e.g. `varIsEqualToString`, `varIsEqualToObject`,
`varListContainsItem`), rather than delegating assertions to a dedicated
step/assertion layer.

**Location**: `regression-core/src/main/java/com/aqa/core/definitions/GeneralDefinitions.java`

**Why accepted**: `GeneralDefinitions` sits at the `definitions` layer,
where CLAUDE.md's stated architecture keeps definitions thin (bind
Gherkin input, delegate) rather than asserting directly. No
`regression_validate_architecture` rule (ARCH-001..004) currently covers
this shape — it was noted as informational during the Architecture
Validator's design work but never turned into a scoped rule, since a
`definitions`-layer assertion rule needs its own design pass distinct from
ARCH-001..004's pages/components-focused scope.

**Status**: present, unfixed, not covered by any current validator rule.
