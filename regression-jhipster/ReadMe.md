# Hybrid API/UI Test Automation Framework

## Overview

`regression-jhipster` is a Java-based test automation module for API, UI, and hybrid end-to-end testing of a JHipster application.

The framework combines executable Cucumber specifications with a layered implementation for REST API testing and browser automation. API operations can be used to prepare and clean test data, while Playwright is reserved for behavior that must be verified through the user interface.

The primary design goal is not maximum abstraction. It is a clear separation of responsibilities, deterministic scenario execution, explicit failure, and safe extension based on confirmed application behavior.

## Core Principles

- Keep Gherkin readable and focused on behavior.
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
├── regression-petstore-api
├── regression-toolshop
├── other regression modules
└── regression-jhipster
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
│   │   │       ├── components
│   │   │       ├── context
│   │   │       ├── definitions
│   │   │       ├── hooks
│   │   │       ├── models
│   │   │       ├── pages
│   │   │       └── steps
│   │   └── resources/properties
│   └── test
│       └── resources/features
│           ├── api
│           └── ui
├── pom.xml
└── README.md
```

Definitions currently follow the established parent-framework convention and remain under `src/main/java`.

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

Playwright objects are not stored in static fields and are not shared between parallel scenarios. Browser reuse should be considered only after lifecycle stability has been demonstrated and explicit thread ownership is designed.

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

Boolean and numeric values are parsed strictly. Invalid, blank, negative, non-finite, or unsupported values fail before the scenario proceeds.

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

## Next Steps and Improvements

### Framework roadmap

1. Introduce a dedicated UI timeout property instead of reusing a generic interval configuration.
2. Configure Playwright assertion timeout explicitly and independently from action/navigation timeout.
3. Implement a scenario-scoped cleanup registry that tracks created entity IDs and performs API cleanup in an `@After` hook.
4. Add pagination-aware API cleanup or server-side filtering for list endpoints.
5. Confirm and model the UI empty-table state so absence assertions work when the last entity is deleted.
6. Add OpenAPI response-schema validation as a separate contract-testing layer.
7. Add browser console, page error, request failure, and optional video diagnostics.
8. Add CI execution matrices for API-only, UI smoke, and cross-browser suites.
9. Validate scenario-level parallel execution before considering browser reuse.
10. Extract generic Playwright infrastructure into `regression-core` only after a second module proves the same abstraction useful.

### Coverage roadmap

- Add logout only when its scenario is implemented.
- Add user-management UI coverage only after the actual DOM, pagination, and permissions are inspected.
- Add update/edit flows when they provide distinct business value.
- Extend authorization coverage for administrator, regular-user, invalid-token, and unauthenticated cases.
- Add contract and boundary scenarios for newly generated API models.

## Guidance for AI Agents

AI-assisted changes must preserve the same architectural constraints as human changes.

Before editing:

- read this README and the relevant source files completely;
- inspect the current feature, runtime OpenAPI document, or real DOM;
- identify scenario scope and ownership of every dependency;
- check for existing uncommitted changes and preserve unrelated work;
- distinguish confirmed facts from assumptions.

During implementation:

- keep Definitions free of Services, locators, and business branching;
- do not expose Steps dependencies merely to shorten a call chain;
- keep API headers explicit between Steps and Services;
- keep token, browser, and page state non-static and scenario-scoped;
- never edit generated OpenAPI models manually;
- do not introduce generic click, wait, or assertion wrappers without demonstrated reuse;
- scope Component locators to their root;
- use exact matching for entity identity and destructive operations;
- preserve strict configuration and DataTable validation;
- never log secrets;
- use API setup for UI tests unless setup is the behavior under test.

Before handing off:

- run the narrowest relevant tests and then the affected module build;
- verify imports, formatting, and generated-source compatibility;
- document any unverified runtime assumption;
- update this README only for stable architectural or operational changes;
- report deferred work explicitly instead of implementing speculative code.

## Architectural Invariants

The following rules are intentional and should change only after an explicit architecture decision:

- API Definitions call Steps, never Services.
- UI Definitions call Steps, never Page Objects.
- API Steps own authorization selection and business orchestration.
- API Services own HTTP details and response deserialization.
- Page Objects and Components own DOM knowledge.
- Components search within their root by default.
- Scenario state is injected and non-static.
- Generated API models remain generated.
- Authentication headers remain explicit.
- Playwright actions use web-first synchronization.
- Hybrid scenarios use API operations for fast setup and cleanup.
- New abstractions require confirmed reuse.
- Explicit failure is preferred over silent tolerance.

## Status

The framework has a stable layered API implementation, a scenario-isolated Playwright lifecycle, and an intentionally compact UI model. It is ready to be extended incrementally from confirmed scenarios, runtime contracts, and inspected DOM structures.
