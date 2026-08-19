# Hybrid API/UI Test Automation Framework

## Overview

`regression-jhipster` is a Java-based test automation module for API, UI, and hybrid end-to-end testing of a JHipster application.

The framework combines executable Cucumber specifications with a layered implementation for REST API testing and browser automation. API operations can be used to prepare and clean test data, while Playwright is reserved for behavior that must be verified through the user interface.

The primary design goal is not maximum abstraction. It is a clear separation of responsibilities, deterministic scenario execution, explicit failure, and safe extension based on confirmed application behavior.

## Core Principles

- Keep Gherkin readable and focused on behavior.
- Use Cucumber for acceptance behavior, not as a mandatory wrapper for every technical check.
- Keep Definitions thin: bind steps, convert data, and delegate.
- Keep orchestration in Steps.
- Keep HTTP transport in API Services.
- Keep browser interaction and domain UI assertions in Page Objects and Components.
- Use API setup and cleanup for UI scenarios whenever the setup itself is not under test.
- Keep authentication and browser state isolated per Cucumber scenario.
- Prefer stable contracts and exact matching over silent tolerance.
- Add abstractions only after a real reuse case is confirmed.
- Do not model pages, components, endpoints, or flows that have not been observed in the running application.

## Technology Stack

| Area | Technology |
| --- | --- |
| Language | Java 21 |
| Build | Apache Maven, multi-module project |
| BDD | Cucumber JVM |
| Dependency injection | Cucumber PicoContainer |
| API client | Jakarta REST / Jersey Client |
| API models | OpenAPI Generator |
| Serialization | Jackson |
| Browser automation | Microsoft Playwright for Java |
| Assertions | Playwright web-first assertions and shared framework assertions |
| Test data models | Lombok and Jackson-compatible mutable beans |
| Logging | SLF4J with Lombok `@Slf4j` |
| Application runtime | Docker Compose / Jib-based JHipster deployment |

Most dependency versions are managed by the parent Maven project. A shared dependency or OpenAPI Generator upgrade must be verified against every affected module before it is accepted.

## Repository Context

The module is part of a reusable regression project:

```text
regression
├── regression-core
├── regression-jhipster
└── other regression modules
```

`regression-core` provides shared infrastructure such as configuration access, request execution, response validation, variables, object population, converters, and common Cucumber utilities.

`regression-jhipster` contains only application-specific API and UI behavior.

## Module Structure

```text
regression-jhipster
├── src
│   ├── main
│   │   ├── java/com/aqa/jhipster
│   │   │   ├── api
│   │   │   │   ├── definitions
│   │   │   │   ├── enumeration
│   │   │   │   ├── models/generated
│   │   │   │   ├── services
│   │   │   │   └── steps
│   │   │   └── ui
│   │   │   │   ├── components
│   │   │   │   ├── context
│   │   │   │   ├── definitions
│   │   │   │   ├── hooks
│   │   │   │   ├── models
│   │   │   │   ├── pages
│   │   │   │   └── steps
│   │   │   └── config
│   │   └── resources/properties
│   │
│   └── test
│       └── resources/features
│           ├── api
│           └── ui
├── pom.xml
└── README.md
```

Definitions currently follow the established parent-framework convention and remain under `src/main/java`. This is a deliberate compatibility choice, not the standard Maven layout for an executable test module. A future move to `src/test/java` must be evaluated across the parent project, dependency scopes, generated sources, and Cucumber glue configuration rather than applied to this module in isolation.

## Architecture Overview

### API flow

```text
Cucumber Feature
    → API Definition
    → API Steps
    → Domain Service
    → Shared API Service
    → Jersey Client
    → Application API
```

### UI flow

```text
Cucumber Feature
    → UI Definition
    → UI Steps
    → Page Object
    → Page Component
    → Playwright
    → Browser
```

### Hybrid flow

```text
API precondition
    → UI behavior under test
    → UI verification
    → API cleanup
```

The API and UI implementations remain independent. Their intentional integration point is the business scenario, not a shared transport or browser abstraction.

### BDD scope and rationale

Cucumber is the acceptance-test layer and the established convention of the parent regression framework. It provides executable business scenarios, reusable tags, data-driven examples, and a consistent API/UI specification style.

The framework does not assume that every test benefits from Gherkin or that non-technical stakeholders currently maintain the feature files. Unit, component, converter, schema, and other technical contract checks may use JUnit directly. Continued use of Cucumber should be evaluated against actual collaboration, reporting, and maintenance needs rather than treated as a goal by itself.

## API Layer

### Responsibilities

| Layer | Responsibility | Must not contain |
| --- | --- | --- |
| Feature | Business behavior and readable test data | Java or HTTP details |
| Definition | Cucumber binding, data conversion, variable storage | Service access, authorization selection, orchestration |
| Steps | Business orchestration, authorization choice, business logging | Request construction details |
| Domain Service | Endpoint path, method, body, headers, status, response type | Scenario state and business workflow |
| Shared API Service | Request execution and common response validation | Domain-specific orchestration |

Definitions call only public business operations exposed by Steps. They do not access injected Services or `AuthService` through record accessors.

API Steps may be implemented as records when they contain only injected dependencies and orchestration methods. Their generated dependency accessors are not part of the Definition-layer API.

### Authentication

Authentication is explicit and scenario-scoped:

```text
Definition
    → domain Steps operation
        → AuthService obtains or reuses scenario token
        → Domain Service receives explicit headers
```

Administrator and regular-user tokens are cached separately inside the scenario-owned authentication service. The cache is never static and is not shared across scenarios.

Authorization headers remain explicit at the Steps-to-Service boundary. This preserves the ability to test:

- different user roles;
- custom credentials;
- invalid tokens;
- missing authorization;
- expected authentication failures.

A global mutable authorization filter is intentionally avoided because it would hide test identity and make negative security scenarios harder to express.

### API contracts

Transport models are generated from the application OpenAPI document. Generated sources must not be edited manually.

Jackson deserialization remains strict about unknown fields. If the runtime response differs from the documented contract, investigate and fix the OpenAPI definition or the application serialization. Do not weaken the client merely to make a mismatch disappear.

Strict DTO deserialization is useful but does not replace complete OpenAPI response validation. Media types, required fields, formats, status definitions, and numeric constraints may require a separate contract-testing layer.

The current Maven generation step does not by itself provide a complete contract-change workflow. A contract pipeline should treat the OpenAPI document as a versioned build input and perform the following stages before full regression execution:

```text
validate specification
    → compare with the accepted contract
    → classify breaking changes
    → generate models with a pinned generator version
    → compile consumers
    → run focused contract/deserialization tests
    → publish or approve the matching contract version
```

Regression execution should use an accepted contract version or checksum. It should not silently regenerate against an uncontrolled latest specification because that can hide when an incompatible backend change was introduced.

### API logging

Endpoint Services execute requests, validate responses, and deserialize entities. Successful business operations are logged at the Steps layer after the expected response has been confirmed.

State-changing operations such as create and delete may be logged. Routine GET operations are not logged by default.

Never log:

- passwords;
- JWT values;
- complete authorization headers;
- sensitive request or response fields.

## UI Layer

### Responsibilities

| Layer | Responsibility | Must not contain |
| --- | --- | --- |
| Feature | User-visible behavior | Browser mechanics |
| Definition | Cucumber binding and DataTable conversion | Locators, Playwright calls, workflow branching |
| Steps | Scenario workflow and current Page Object state | CSS/XPath selectors |
| Page Object | Page behavior, navigation, readiness, domain assertions | Cucumber annotations |
| Component | Reusable behavior inside a bounded DOM region | Scenario orchestration |
| UI model | Input and expected data | Browser objects or locators |
| Hooks | Browser lifecycle and diagnostic artifacts | Business actions |

Definitions should normally contain no private helper methods, conditional business flow, or browser logic. Data conversion and fail-fast input validation are acceptable.

UI Steps are regular classes when they hold current-page scenario state. They expose workflow and assertion methods to Definitions while keeping Page Objects hidden from the Cucumber layer.

Assertions live where the relevant DOM and business meaning are known. Steps may delegate to those assertions, but should not reimplement Playwright checks.

### Page Objects and Components

Page Objects represent confirmed application pages. Components represent meaningful, reusable DOM regions such as navigation or a data table.

The framework does not introduce generic wrappers for every click, open, close, or visibility check. Playwright already provides these mechanics with auto-waiting. A shared method is justified only when it expresses framework policy or reusable domain behavior.

Component locators are scoped to their root locator. Page-level elements outside a component root must be accessed explicitly by the owning Page Object or Component.

Return-value convention:

- a method that stays on the same page may return `this` only when fluent chaining is actually used;
- navigation returns the target Page Object;
- queries return their values;
- assertions return `void` unless a real fluent assertion chain requires otherwise.

### HTML tables and Cucumber DataTables

These concepts are intentionally separate:

| Concept | Purpose |
| --- | --- |
| Core `DataTableConverter` | Converts Cucumber `DataTable` input into beans and explicit headers |
| UI table component | Locates and validates rows and cells in the browser DOM |

Browser table lookup should be column-aware and exact. A row must be identified by an exact value in a named column rather than by substring matching across the entire row.

Column structure must be confirmed from the real DOM before implementing a reusable table operation. Empty-table behavior must also be inspected because some applications remove the entire `<table>` when no records exist.

### Waiting and assertions

- Prefer Playwright web-first assertions.
- Rely on Playwright actionability checks for normal interactions.
- Do not add `Thread.sleep`.
- Keep page readiness limited to mandatory page elements.
- Handle optional content at the operation that needs it.
- Assert uniqueness before destructive row actions.
- Keep action and expected-result assertion in separate Gherkin steps.
- Do not assume BrowserContext action timeout also changes the Playwright assertion timeout.

Java `Pattern.quote()` must not be used for regular expressions passed to Playwright because its `\Q...\E` syntax is Java-specific. Literal values must be escaped into a JavaScript-compatible regular expression.

## Browser Lifecycle and Isolation

Each `@ui` scenario receives isolated browser state through PicoContainer-managed objects:

```text
Create Playwright
    → configure data-cy as test ID
    → launch configured Browser
    → create isolated BrowserContext
    → configure base URL and timeouts
    → create Page
    → initialize scenario context atomically
    → run scenario
    → collect failure diagnostics
    → close BrowserContext
    → clear scenario context
    → close Browser
    → close Playwright
```

The current implementation launches a browser per UI scenario. This prioritizes isolation and predictable cleanup over execution speed.

Playwright objects are not stored in static fields and are not shared between parallel scenarios. This is the safe baseline, but browser startup is a known performance cost for a larger UI suite.

The target optimization is:

```text
execution worker → Playwright + Browser
UI scenario      → BrowserContext + Page
```

`BrowserContext` and `Page` must remain scenario-owned and must always be closed after the scenario. `Playwright` and `Browser` may be reused only within an explicitly owned execution worker. The implementation must first define whether parallelism is provided by Maven forks, CI shards, or Cucumber threads because Playwright Java objects are not thread-safe.

A single mutable static browser is not an acceptable optimization. Browser reuse requires worker lifecycle management, browser-disconnection recovery, deterministic final shutdown, and measured evidence that it improves the suite.

Startup and shutdown are fail-safe:

- configuration is validated before resource allocation where possible;
- partially created resources are closed after startup failure;
- cleanup errors do not hide the original startup or scenario failure;
- scenario context initialization is atomic;
- the stored Page must belong to the stored BrowserContext;
- cleanup remains idempotent.

## Dependency Injection and Scenario State

Cucumber PicoContainer provides constructor injection and creates the object graph for each scenario.

Scenario-owned mutable state includes:

- authentication token caches;
- current UI Page Objects held by Steps;
- BrowserContext and Page;
- tracing state;
- named test variables managed by the shared framework.

This state must remain instance-based. Do not move it into static caches, global singletons, or shared mutable filters.

Future worker-scoped browser infrastructure does not change this rule: scenario `BrowserContext`, `Page`, tracing, current Page Objects, variables, and authentication state must never be stored in the worker runtime.

Constructor injection is preferred. Records are appropriate for immutable dependency holders; regular classes are appropriate when lifecycle or scenario state changes during execution.

## Test Data Strategy

The framework supports readable Cucumber tables while retaining strict execution behavior:

```text
Cucumber DataTable
    → shared converter/populator
    → typed API or UI model
    → explicit list of supplied fields
    → Steps/Page operation
```

Explicit headers are preserved for partial UI forms. Unknown headers and missing required values fail immediately instead of being ignored.

Hybrid scenarios should use unique, clearly identifiable test data. Cleanup by name must use exact equality and remove all duplicates. ID-based post-scenario cleanup is preferred when IDs are available.

Test cleanup has two complementary stages:

```text
pre-cleanup  → remove leftovers from interrupted earlier runs
test         → create and verify current data
post-cleanup → remove entities created by the current scenario
```

Cleanup failures should be logged without hiding the original test failure.

An `@After` hook is a best-effort cleanup mechanism, not a crash-recovery guarantee. It may not execute after JVM termination, a critical `OutOfMemoryError`, container loss, or a hard CI timeout. A resilient cleanup design therefore requires multiple layers:

1. Register every created entity immediately after successful creation and delete it in scenario cleanup.
2. Add a unique run identifier to discover all data created by a test run.
3. Perform run-level bulk cleanup independently from individual scenario results.
4. Use an external scheduled janitor or TTL policy for data left by terminated processes.
5. Prefer tenant, schema, database, or container reset in disposable test environments.

Deletes should be idempotent, and an already absent entity should normally be treated as successfully cleaned. Until the external recovery layers are implemented, interrupted runs may leave residual data; existing pre-cleanup reduces this risk only for entities and queries that support reliable discovery.

## Configuration

Environment-specific values are read through the shared property layer. Page Objects and API Services do not load property files directly.

Typical configuration categories include:

- API and UI base URLs;
- administrator and regular-user credentials;
- browser engine;
- headless mode;
- slow motion;
- action and navigation timeout;
- Playwright tracing.

Supported browser values are:

```text
chromium
firefox
webkit
```

Boolean values and supported browser names are parsed strictly. Blank, non-numeric, negative, or unsupported values fail before the scenario proceeds. Explicit rejection of non-finite numeric values such as `NaN` and `Infinity` remains a validation improvement.

`data-cy` is an application locator convention and remains framework configuration rather than an environment property.

## Running the Tests

### Prerequisites

- JDK 21;
- Maven compatible with the parent project;
- Docker for the tested application;
- the required Playwright browser binaries.

Install a browser binary when preparing a new environment:

```bash
mvn -pl regression-jhipster exec:java \
  -Dexec.mainClass=com.microsoft.playwright.CLI \
  -Dexec.args="install chromium"
```

Run the module with its required upstream modules:

```bash
mvn -pl regression-jhipster -am clean test
```

Filter Cucumber execution by tags when supported by the configured runner:

```bash
mvn -pl regression-jhipster -am test -Dcucumber.filter.tags="@api"
mvn -pl regression-jhipster -am test -Dcucumber.filter.tags="@ui"
```

After changing `regression-core`, rebuild the dependency chain:

```bash
mvn -pl regression-core -am clean install -DskipTests
```

Generated OpenAPI sources should be regenerated through the Maven build configuration rather than copied or edited manually.

## Diagnostics

Failed UI scenarios attach a full-page screenshot to the Cucumber scenario. When tracing is enabled, failed traces are stored under:

```text
target/playwright/traces
```

Successful traces are stopped without export. Trace files and other `target` artifacts must not be committed.

When source inclusion is enabled for Playwright tracing, configure `PLAYWRIGHT_JAVA_SRC` if source files are expected inside the trace archive.

## Extension Guidelines

When adding API coverage:

1. Confirm the endpoint in the runtime OpenAPI document.
2. Regenerate transport models when the contract changes.
3. Add or extend the endpoint Service.
4. Add business orchestration to Steps.
5. Bind Gherkin only through Definition methods.
6. Keep authorization selection inside Steps.
7. Add positive, negative, and authorization scenarios where relevant.

When adding UI coverage:

1. Define the behavior in Gherkin.
2. Inspect the real DOM in the target state.
3. Prefer stable `data-cy`, role, and scoped locators.
4. Extend an existing Page Object or Component only when it owns the behavior.
5. Add a new Component only for a meaningful reusable DOM region.
6. Keep workflow state in Steps.
7. Keep DOM assertions in Page Objects or Components.
8. Confirm empty, loading, error, and duplicate-match states where relevant.

When making AI-assisted changes specifically:

- do not expose Steps dependencies merely to shorten a call chain — Definitions call only the public business operations Steps expose, not injected Services or `AuthService` through record accessors;
- keep API headers explicit between Steps and Services rather than hiding them behind a shared filter;
- include benchmark or failure-recovery evidence when changing browser lifecycle or cleanup behavior, not just the code change;
- update this README only for stable architectural or operational changes, and update the "Current Limitations and Trade-offs" table below when a roadmap item is demonstrably completed.

CLAUDE.md's repository-wide rules (thin Definitions, no static mutable state, generated-source discipline, explicit authorization before git writes, and more) apply here in full; this list only adds what is specific to this module.

## Current Limitations and Trade-offs

The current implementation is a stable architectural baseline, not a claim that every production-scale concern has already been solved.

| Area | Current state | Consequence |
| --- | --- | --- |
| Browser lifecycle | A new Playwright process and Browser are created for every UI scenario | Strong isolation, but increasing startup cost as the UI suite grows |
| Parallel execution | Scenario state is isolated, but the Cucumber/Maven/CI parallel model has not been validated end to end | Browser reuse and execution concurrency must not be enabled speculatively |
| Cleanup recovery | Explicit pre-cleanup and scenario cleanup are available; a persistent run registry and external janitor are not yet implemented | A terminated JVM or CI worker can leave test data behind |
| OpenAPI evolution | Models are generated and deserialized strictly; version promotion and breaking-change detection are not yet automated | Contract drift may first appear as compilation or deserialization failure |
| Schema coverage | Generated DTOs verify model compatibility but not the complete response contract | Status/media type/schema constraints may require dedicated contract validation |
| UI tables | Exact column-aware lookup targets the confirmed DOM | Pagination, virtualization, localization, responsive columns, and the empty-table DOM require separate verification |
| Source layout | Definitions remain under `src/main/java` for parent-project compatibility | The module does not currently follow the conventional Maven placement of executable test glue under `src/test/java` |
| BDD cost | Cucumber provides a consistent acceptance layer | Gherkin and Definitions add maintenance cost when scenarios are purely technical or have no wider readership |
| Diagnostics | Screenshots and optional traces are available on failure | Console errors, page errors, failed requests, and video are not yet collected centrally |
| Configuration validation | Browser names, booleans, and negative/non-numeric timeout values are validated | Non-finite numeric values are not yet rejected explicitly |

The core `DataTableConverter` is not coupled to HTML tables and does not remove the DOM limitations listed above. It converts Cucumber input into typed test data; browser table behavior remains the responsibility of UI Components based on inspected markup.

## Next Steps and Improvements

See [`docs/ROADMAP.md`](../docs/ROADMAP.md) for how one item from this list (the UI-timeout/cleanup-registry/pagination item below) is reflected in the reactor-wide roadmap.

### Priority 1 — correctness and recovery

1. Introduce a versioned OpenAPI contract pipeline: validation, breaking-change comparison, generation with a pinned tool version, compilation, focused contract tests, and approved contract checksum/version.
2. Add response-schema validation where generated DTO deserialization is insufficient.
3. Implement an immediate scenario cleanup registry for created entity IDs, followed by run-level cleanup using a unique run identifier.
4. Add an external TTL-based janitor or disposable-environment reset so cleanup does not depend solely on Cucumber hooks.
5. Add pagination-aware API discovery or server-side filtering for cleanup without broad, unsafe deletion.
6. Introduce a dedicated UI timeout property, reject non-finite numeric values, and configure Playwright assertion timeout independently from action and navigation timeouts.
7. Confirm the real empty-table, loading, error, pagination, and duplicate-row states before expanding the shared table behavior.

### Priority 2 — execution scale and diagnostics

1. Benchmark the existing browser-per-scenario lifecycle with representative smoke and regression suites.
2. Define the actual parallel execution unit: Maven fork, CI shard, or Cucumber worker thread.
3. If measurements justify reuse, implement one Playwright and Browser per execution worker while retaining a new BrowserContext and Page per scenario.
4. Verify isolation under parallel execution for cookies, storage, downloads, tracing paths, generated test data, authentication, and cleanup.
5. Add CI suites for API contract checks, API regression, UI smoke, and cross-browser coverage.
6. Add browser console, page error, request failure, and optional video diagnostics with bounded artifact retention.

### Priority 3 — maintainability decisions

1. Review the source layout across the parent repository and decide whether executable Definitions, Hooks, and application-specific test code should move to `src/test/java`.
2. Periodically review whether Cucumber continues to provide acceptance, collaboration, or reporting value; keep purely technical checks outside Gherkin where appropriate.
3. Extract generic Playwright infrastructure into `regression-core` only after a second module proves the same abstraction and lifecycle requirements useful.

A roadmap item is complete only when its behavior is tested, its failure mode is documented, and the relevant limitation above is removed or updated. Moving an item into code without validating runtime behavior does not close the architectural risk.

### Coverage roadmap

- Add logout only when its scenario is implemented.
- Add user-management UI coverage only after the actual DOM, pagination, and permissions are inspected.
- Add update/edit flows when they provide distinct business value.
- Extend authorization coverage for administrator, regular-user, invalid-token, and unauthenticated cases.
- Add contract and boundary scenarios for newly generated API models.

## Architectural Invariants

The following rules are intentional and should change only after an explicit architecture decision:

- API Definitions call Steps, never Services.
- UI Definitions call Steps, never Page Objects.
- API Steps own authorization selection and business orchestration.
- API Services own HTTP details and response deserialization.
- Page Objects and Components own DOM knowledge.
- Components search within their root by default.
- Scenario state is injected and non-static.
- BrowserContext and Page remain scenario-owned even if the Browser lifecycle changes.
- Generated API models remain generated.
- OpenAPI contract changes must remain explicit and reviewable.
- Authentication headers remain explicit.
- Playwright actions use web-first synchronization.
- Hybrid scenarios use API operations for fast setup and cleanup.
- New abstractions require confirmed reuse.
- Explicit failure is preferred over silent tolerance.

## Status

The framework has a stable layered API implementation, a scenario-isolated Playwright baseline, and an intentionally compact UI model. It is ready for incremental functional coverage based on confirmed scenarios, accepted contracts, and inspected DOM structures.

Production-scale parallel execution, worker-level browser reuse, versioned contract promotion, and crash-resilient cleanup remain explicit improvement areas. They must be validated independently and must not weaken the current scenario isolation or failure transparency.
