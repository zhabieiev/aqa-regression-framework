# regression-jhipster Hybrid API/UI Automation Framework

## 1. Purpose

`regression-jhipster` is a Java 21 Maven module for API, UI, and hybrid test automation against a locally deployed JHipster application.

The framework combines:

- Cucumber for executable specifications;
- Jersey for REST API calls;
- Jackson and OpenAPI-generated transport models;
- Playwright for browser automation;
- PicoContainer for scenario-scoped dependency injection;
- Lombok for concise test-data models and logging;
- Docker for the tested JHipster application.

The main architectural goal is strict separation of responsibilities while allowing API operations to prepare and clean data for UI scenarios.

## 2. Current State

### Confirmed and working

- JHipster runs locally at `http://localhost:8080`.
- Swagger UI is available at `/admin/docs`.
- OpenAPI is available at `/v3/api-docs`.
- Admin and regular-user JWT authentication work through API.
- Admin user management works through API.
- Bank Account API create, read, and delete operations work.
- Cleanup deletes every Bank Account matching an exact name, including duplicates.
- Generated API models remain strict about undocumented response fields.
- The JHipster `BankAccount` response was aligned with its OpenAPI schema.
- Playwright starts and closes correctly for `@ui` scenarios.
- UI login works for a regular user and administrator.
- Invalid UI login is covered.
- Bank Account UI creation and deletion flows work with API test-data preparation.
- Screenshots and Playwright traces are collected for failed UI scenarios.

### Not part of the finalized structure

- `AccountSettingsPage`: the page was based on an assumption and was not confirmed in the actual UI.
- `AlertComponent`: removed because no current Page Object uses it.
- `UserManagementPage`: should remain absent until a real scenario and actual DOM are inspected.
- speculative edit, logout, settings, counting, and generic navigation methods without scenario usage.

The framework follows YAGNI: a Page Object, component, or method is added only for confirmed application behavior used by a test.

## 3. Maven Project Context

```text
regression
├── regression-core
├── regression-petstore-api
├── regression-juiceshop
├── regression-toolshop
└── regression-jhipster
```

### `regression-core`

Shared infrastructure includes:

- `PropertiesController` and the `Property` enumeration;
- Jersey request construction and response validation;
- `FileParseUtils`;
- `Populator`;
- common converters and scenario utilities;
- shared Cucumber infrastructure.

### `regression-jhipster`

Owns only JHipster-specific functionality:

- API authentication, services, steps, and definitions;
- generated JHipster API models;
- Playwright context and lifecycle;
- JHipster Page Objects and components;
- UI beans, steps, definitions, and feature files.

## 4. Finalized Module Layout

```text
regression-jhipster
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com.aqa.jhipster
│   │   │       ├── api
│   │   │       │   ├── definitions
│   │   │       │   ├── enumeration
│   │   │       │   ├── models.generated
│   │   │       │   ├── services
│   │   │       │   └── steps
│   │   │       └── ui
│   │   │           ├── components
│   │   │           │   ├── BaseComponent.java
│   │   │           │   ├── DataTableComponent.java
│   │   │           │   └── NavigationBar.java
│   │   │           ├── context
│   │   │           │   ├── PlaywrightManager.java
│   │   │           │   └── UiScenarioContext.java
│   │   │           ├── definitions
│   │   │           │   ├── BankAccountDefinitions.java
│   │   │           │   └── LoginDefinitions.java
│   │   │           ├── hooks
│   │   │           │   └── UiHooks.java
│   │   │           ├── models
│   │   │           │   ├── BankAccountBean.java
│   │   │           │   └── LoginBean.java
│   │   │           ├── pages
│   │   │           │   ├── BasePage.java
│   │   │           │   ├── BankAccountFormPage.java
│   │   │           │   ├── BankAccountPage.java
│   │   │           │   ├── HomePage.java
│   │   │           │   └── LoginPage.java
│   │   │           └── steps
│   │   │               ├── BankAccountSteps.java
│   │   │               └── LoginSteps.java
│   │   └── resources
│   │       └── properties
│   │           └── dev.properties
│   └── test
│       └── resources
│           └── features
│               ├── api
│               └── ui
│                   ├── 01_Login
│                   └── 02_BankAccount
├── pom.xml
└── README.md
```

Definitions currently remain under `src/main/java` because this is the established convention of the parent framework.

## 5. Dependencies

### Main stack

- Java 21
- Maven multi-module build
- Cucumber Java
- Cucumber PicoContainer
- Jakarta REST/Jersey Client
- Jackson Databind and Java Time
- OpenAPI Generator
- AssertJ
- Lombok
- Microsoft Playwright for Java
- Docker Compose and Jib

### Important constraints

- Most dependency versions are inherited from the root POM.
- JHipster model generation uses the runtime OpenAPI document.
- Generated models must never be edited manually.
- A global OpenAPI Generator upgrade must be verified against every module; previous global upgrades broke generated Petstore and Toolshop sources.
- `cucumber-picocontainer` is required for constructor injection and scenario-scoped state.
- `jersey-media-json-jackson` is required for Jersey entity deserialization.
- Lombok must be available both in Maven dependencies and as enabled annotation processing in IntelliJ.

## 6. Configuration

Example `properties/dev.properties`:

```properties
url.api=http://localhost:8080
url.ui=http://localhost:8080

user.administrator.username=admin
user.administrator.password=admin
user.username=user
user.password=user

ui.browser=chromium
ui.headless=false
ui.timeout=10000
ui.slow.motion=0
ui.trace=true
```

Recommended property constants:

```text
URL_API
URL_UI
UI_BROWSER
UI_HEADLESS
UI_TIMEOUT
UI_SLOW_MOTION
UI_TRACE
```

`Property.read()` already resolves a property value through the shared configuration layer.

Correct:

```java
UI_BROWSER.read();
```

Incorrect:

```java
PropertiesController.getProperty(UI_BROWSER.read());
```

`data-cy` is an application locator convention, not an environment-dependent setting. It remains a constant in `PlaywrightManager`:

```java
private static final String TEST_ID_ATTRIBUTE = "data-cy";

playwright.selectors().setTestIdAttribute(TEST_ID_ATTRIBUTE);
```

Page Objects and components then use `page.getByTestId(value)`.

## 7. API Architecture

```text
Cucumber Feature
    → API Definition
    → API Steps
    → Domain Service
    → ApiService
    → GeneralApiService
    → Jersey Client
```

### Responsibilities

| Layer | Responsibility |
| --- | --- |
| Feature | Business behavior and test data |
| Definition | Gherkin binding, DataTable conversion, named-variable handling |
| Steps | Business orchestration across services |
| Domain service | Endpoint, HTTP method, body, headers, response model, expected status |
| General service | Request execution and common status validation |
| Client controller | Jersey client and JSON provider configuration |

Definitions must call business-level Steps operations. They must not reach into Steps to extract `BankAccountService` or `AuthService`.

Preferred:

```java
bankAccountSteps.deleteAllByName(name);
```

Avoid:

```java
bankAccountSteps.bankAccountService().delete(...);
```

## 8. API Authentication

- Endpoint: `POST /api/authenticate`
- Request: `LoginVM`
- Response: `JWTToken`
- Token property: `id_token`
- Header: `Authorization: Bearer <token>`

Admin and regular-user tokens are cached separately. Headers remain explicit per request because authorization tests must be able to send different identities, invalid tokens, or no token.

## 9. Strict OpenAPI Contract Strategy

Source specification:

```text
http://localhost:8080/v3/api-docs
```

Generated package:

```text
com.aqa.jhipster.api.models.generated
```

The Jersey response mapper must remain strict:

```text
FAIL_ON_UNKNOWN_PROPERTIES = true
```

This strictness detected that the Bank Account runtime response contained undocumented relationship fields:

```json
{
  "operations": null,
  "user": null
}
```

while OpenAPI described only `id`, `name`, and `balance`.

The contract was fixed in the JHipster source instead of weakening the client:

```java
@JsonIgnoreProperties(
    value = { "operations", "user" },
    allowSetters = true
)
```

The JHipster application image must be rebuilt after this source change. Modifying a running container is not persistent and cannot replace compiled Java bytecode.

Jackson strict DTO deserialization is necessary but is not a complete OpenAPI validator. It does not validate every required field, media type, status response, format, or numeric boundary. Full schema validation can be added later as a separate contract-testing layer.

## 10. API Bank Account Cleanup

Cleanup by name performs:

```text
GET accounts
    → filter by exact name
    → collect every matching ID
    → DELETE every match
```

`findFirst()` is intentionally not used because duplicate names are allowed. Cleanup is idempotent: zero matches is a successful outcome.

Deletion logs only after `204 No Content` is validated:

```java
log.info("Bank account with {} id is deleted", id);
```

If the list endpoint becomes paginated, cleanup must either use a server-side name filter or traverse every page.

## 11. UI Architecture

```text
Cucumber Feature
    → UI Definition
    → UI Steps
    → Page Object
    → Component
    → Playwright
```

### Layer responsibilities

| Layer | Owns | Must not own |
| --- | --- | --- |
| Feature | Behavior and readable data | Technical browser operations |
| Definition | Cucumber annotations and table conversion | Locators or Playwright calls |
| Steps | Scenario workflow and current page state | CSS selectors |
| Page Object | Page behavior and page-level assertions | Cucumber annotations |
| Component | Reusable page region behavior | Scenario orchestration |
| UI bean | Input data | `Page`, `Locator`, or browser logic |
| Hooks | Browser lifecycle and failure artifacts | Business test steps |

## 12. Finalized Playwright Lifecycle

For every `@ui` scenario:

```text
Playwright.create()
    → select data-cy as test ID
    → launch configured Browser
    → create isolated BrowserContext
    → configure base URL and timeouts
    → create Page
    → atomically initialize UiScenarioContext
    → execute scenario
    → attach screenshot on failure
    → save trace on failure
    → close BrowserContext
    → clear UiScenarioContext
    → close Browser
    → close Playwright
```

### `PlaywrightManager`

Owns:

- `Playwright`;
- launched `Browser`;
- browser selection;
- launch options;
- BrowserContext creation and configuration;
- `data-cy` test-ID configuration.

It supports `chromium`, `firefox`, and `webkit` and rejects unsupported values.

Configuration parsing should fail fast:

- `ui.headless` accepts only `true` or `false`;
- timeout and slow motion must be numeric and non-negative;
- browser names are trimmed and normalized with `Locale.ROOT`.

If startup fails after a resource has been created, `start()` closes already-created resources before rethrowing the failure.

### `UiScenarioContext`

Scenario-scoped mutable holder for exactly one `BrowserContext` and one `Page`.

Initialization is atomic:

```java
scenarioContext.initialize(browserContext, page);
```

Separate `setBrowserContext()` and `setPage()` methods are removed to prevent partially initialized state.

Guarded getters throw clear exceptions when the context is unavailable. `clear()` removes references after resources have been closed.

### `UiHooks`

Owns:

- `@Before("@ui")` startup;
- tracing startup when enabled;
- failure screenshot attachment;
- trace saving for failed scenarios;
- cleanup in `finally` blocks.

Tracing state should be tracked explicitly so `stop()` is never called when tracing did not start. Screenshot or trace failures must not prevent browser cleanup.

`BrowserContext` is deliberately not opened with a local try-with-resources block. It must remain alive for the whole scenario and is closed centrally in the `@After` hook.

### Isolation and parallelism

The current implementation launches a browser per scenario. It is slower but simple, isolated, and safe.

Playwright Java objects are not thread-safe. Never share a static `Page` or `BrowserContext` across parallel scenarios. Browser reuse may be considered later only with explicit per-thread ownership and isolated contexts.

## 13. Finalized UI Components

### `BaseComponent`

Owns:

- non-null `Page`;
- non-null root `Locator`.

Provides:

- inherited `waitUntilDisplayed()` based on a Playwright web-first assertion;
- `byDataCy(value)` using `page.getByTestId(value)`.

Thin assertion wrappers such as `assertVisible(locator)` are intentionally absent because they add no domain behavior.

### `NavigationBar`

The current tested responsibility is authentication verification. Its root is `accountMenu`; it opens the account menu and verifies that `logoutButton` is visible.

The minimized component contains only locators and methods required by existing scenarios. Settings, logout execution, User Management navigation, and entity navigation are added only when corresponding scenarios need them.

### `DataTableComponent`

Wraps its inherited table root and provides only currently needed operations:

- find a row containing text;
- assert that a matching row is absent;
- optionally count data rows when a real scenario needs it.

It must not duplicate the inherited root with a second `table` field or override `waitUntilDisplayed()` with identical behavior.

The Bank Account page does not render a table when the list is empty. Therefore table visibility is not part of general page readiness. Operations that require rows wait for the table locally.

### Removed components

`AlertComponent` is removed from the finalized structure because no current Page Object uses it. It can be reintroduced when an implemented scenario verifies success or error alerts across more than one page.

## 14. Finalized Page Objects

### `BasePage`

Owns only the non-null Playwright `Page` and common mechanics:

- `navigateTo(path)`;
- covariant `waitUntilLoaded()` contract;
- `byDataCy(value)`;
- URL assertion;
- path normalization.

It does not read `url.ui`; base URL belongs to `BrowserContext` configuration in `PlaywrightManager`.

Blank paths normalize to `/`. A nonblank path receives a leading slash when missing.

`Pattern.quote()` is not used in Playwright URL assertions. It produces Java-specific `\Q...\E` syntax that does not work when Playwright transfers the regular expression to JavaScript.

### Fluent return convention

```java
public abstract BasePage waitUntilLoaded();
```

Concrete pages use covariant returns:

```java
public LoginPage waitUntilLoaded();
public HomePage waitUntilLoaded();
public BankAccountPage waitUntilLoaded();
public BankAccountFormPage waitUntilLoaded();
```

Rules:

- same-page action or assertion returns `this`;
- navigation returns the target Page Object;
- data query returns its value;
- private mechanics may return `void`.

### `LoginPage`

Confirmed responsibilities:

- open `/login`;
- verify URL and mandatory fields;
- fill credentials from `LoginBean` using supplied DataTable headers;
- submit a successful login and return `HomePage`;
- submit an expected failure and remain on `LoginPage`;
- assert authentication error.

The strict header switch rejects unsupported columns and protects against silent typos ignored by the shared Jackson reader.

### `HomePage`

`HomePage` represents the authenticated landing state, not every possible application navigation route.

Confirmed responsibilities:

- wait for `NavigationBar`;
- wait until URL no longer contains `/login`;
- verify authenticated state through `NavigationBar`.

Unused `open()`, logout, settings, User Management, and Bank Account navigation methods are removed. Bank Account scenarios may open their confirmed route directly through `BankAccountPage.open()`.

### `BankAccountPage`

Confirmed responsibilities:

- open `/bank-account`;
- verify URL, heading, and create button;
- open the create form;
- assert an account by exact test name and balance;
- delete the uniquely matching account;
- assert that an account is absent.

Readiness requires only mandatory elements:

```text
URL contains /bank-account
BankAccountHeading is visible
entityCreateButton is visible
```

The table is optional in the empty state.

Before destructive operations, the page asserts that exactly one row matches the name. This exposes dirty data instead of clicking an arbitrary duplicate.

Unused edit, boolean presence, raw row exposure, and count methods are removed.

### `BankAccountFormPage`

Confirmed responsibilities:

- verify the create/update form;
- fill explicitly supplied fields from `BankAccountBean` and headers;
- save and return `BankAccountPage`.

Supported headers:

```text
name
balance
user
```

Every supplied header is validated by a strict switch. Every required value is checked before interacting with its locator.

The unused cancel flow and field getter methods are removed until a scenario needs them.

### Deferred pages

`UserManagementPage` is not part of the finalized active structure. API user management does not justify a UI Page Object. Add it only after defining a UI scenario and inspecting the real table, row actions, pagination, and empty state.

## 15. UI Data Population

The UI uses the shared core population pattern:

```text
Cucumber DataTable
    → List<Map<String, String>>
    → Populator.populateList()
    → UI bean
    → explicit headers
    → UI Steps
    → Page Object
```

Current UI beans:

- `LoginBean` — `username`, `password`;
- `BankAccountBean` — `name`, `balance`, `user`.

Mutable Lombok beans are retained because they integrate with the existing Jackson-based `Populator` and support partial tables.

`LoginBean.password` must be excluded from generated `toString()` output.

Headers are passed separately because they indicate which fields were explicitly present. Page Objects reject unknown headers even though the shared JSON reader ignores unknown JSON properties.

DataTables for `populateList()` are horizontal:

```gherkin
| username | password |
| admin    | admin    |
```

A vertical table would interpret `admin` as a header in this flow and can cause `Unsupported login field: admin`.

## 16. Login Flow

```gherkin
@ui
Feature: Login

  @smoke
  Scenario: User signs in with valid credentials
    Given ui user opens the login page
    When ui user signs in with credentials:
      | username | password |
      | user     | user     |
    Then ui user is authenticated

  Scenario: Administrator signs in
    Given ui user opens the login page
    When ui user signs in with credentials:
      | username | password |
      | admin    | admin    |
    Then ui user is authenticated

  Scenario: User cannot sign in with an invalid password
    Given ui user opens the login page
    When ui user signs in expecting failure:
      | username | password         |
      | user     | invalid-password |
    Then the authentication error is displayed
```

Responsibilities:

- `LoginDefinitions` converts exactly one row to `LoginBean` and extracts headers;
- `LoginSteps` keeps current `LoginPage` and successful `HomePage` state;
- `LoginPage` performs browser interaction;
- `HomePage` verifies authenticated application state.

Negative login keeps action and assertion separate:

```text
When → submit invalid credentials
Then → verify authentication error
```

## 17. Bank Account Hybrid Flow

API is used for data setup and cleanup; UI is used only for the behavior under test.

```gherkin
@ui
Feature: Bank account management

  Background:
    Given ui user opens the login page
    When ui user signs in with credentials:
      | username | password |
      | admin    | admin    |
    Then ui user is authenticated

  @smoke
  Scenario: Administrator creates a bank account through UI
    Given api user deletes all bank accounts with name:
      | name | UI Created Bank Account 1 |
    And ui user opens the bank accounts page
    When ui user creates a bank account:
      | name                      | balance | user  |
      | UI Created Bank Account 1 | 1000    | admin |
    Then ui bank account is displayed:
      | name                      | balance |
      | UI Created Bank Account 1 | 1000    |

  @hybrid
  Scenario: Administrator deletes a bank account through UI
    Given api user deletes all bank accounts with name:
      | name | UI Bank Account For Deletion |
    And api user creates new bank account and saves to 'account':
      | name    | UI Bank Account For Deletion |
      | balance | 2000                         |
    And ui user opens the bank accounts page
    Then ui bank account is displayed:
      | name                         | balance |
      | UI Bank Account For Deletion | 2000    |
    When ui user deletes bank account "UI Bank Account For Deletion"
    Then ui bank account "UI Bank Account For Deletion" is not displayed
```

`Background` contains authentication only. Each scenario opens the list after its own API preconditions are complete.

## 18. Test Data Cleanup

Pre-cleanup by exact name removes leftovers from previous interrupted runs and makes fixed-name tests repeatable.

Pre-cleanup does not replace post-cleanup:

```text
pre-cleanup  → remove old leftovers
test         → create and verify current data
post-cleanup → remove data created by this scenario
```

The preferred next implementation is a scenario-scoped cleanup registry:

- store IDs of entities created by API or UI;
- delete them through API in an `@After` hook;
- execute cleanup even when the UI scenario fails;
- log cleanup failures without hiding the original test failure.

Until then, use a dedicated test-data name prefix and exact matching to avoid deleting legitimate environment data.

## 19. Assertions and Waiting

- Use Playwright web-first assertions.
- Do not add Selenium-style explicit waits around normal Playwright actions.
- Page readiness checks only mandatory elements.
- Optional empty/non-empty states are handled by the operation that needs them.
- Do not expose raw `Locator` objects from Page Objects without a genuine reuse case.
- Avoid thin assertion wrappers that only rename Playwright APIs.
- Add domain assertions when they combine meaningful business checks.
- Validate unique matches before edit or delete operations.

`BrowserContext.setDefaultTimeout()` does not necessarily replace Playwright assertion defaults. Earlier failure logs showed a 5000 ms assertion timeout. If a common assertion timeout is required, configure Playwright assertions explicitly rather than assuming BrowserContext timeout controls them.

## 20. Running JHipster

Run from the `jhipster-sample-app` root in PowerShell.

Start an existing stopped container:

```powershell
docker compose -f .\src\main\docker\app.yml start
```

Start after `docker compose down`:

```powershell
docker compose -f .\src\main\docker\app.yml up -d
```

Rebuild after JHipster source changes:

```powershell
.\mvnw.cmd -ntp verify "-DskipTests" "-Pprod,api-docs" jib:dockerBuild
docker compose -f .\src\main\docker\app.yml up -d --force-recreate
```

Status and logs:

```powershell
docker compose -f .\src\main\docker\app.yml ps
docker compose -f .\src\main\docker\app.yml logs -f
```

Endpoints:

- UI: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/admin/docs`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- API: `http://localhost:8080/api`

Default credentials:

- administrator: `admin / admin`
- regular user: `user / user`

## 21. Installing Playwright Browsers

From `regression-jhipster`:

```powershell
mvn exec:java `
  "-Dexec.mainClass=com.microsoft.playwright.CLI" `
  "-Dexec.args=install chromium"
```

Install Firefox or WebKit as well before selecting them through `ui.browser`.

## 22. Rebuilding Shared Framework Changes

After changing `regression-core`:

```powershell
mvn -pl regression-core -am clean install -DskipTests
```

Then reload Maven in IntelliJ and rebuild or rerun `regression-jhipster`.

## 23. Playwright Artifacts

Failure artifacts are stored under:

```text
target/playwright
└── traces
```

Screenshots are attached directly to the Cucumber scenario. Traces are stored as ZIP archives for failed scenarios. `target` must not be committed.

## 24. Remaining Work in Priority Order

1. Apply atomic `UiScenarioContext.initialize(browserContext, page)` and remove separate setters.
2. Harden `PlaywrightManager` startup rollback and strict property parsing.
3. Harden `UiHooks` so screenshot and trace failures cannot block resource cleanup.
4. Introduce dedicated `UI_TIMEOUT` instead of reusing a generic interval property.
5. Implement ID-based post-scenario cleanup registry.
6. Ensure API name cleanup handles pagination.
7. Configure Playwright assertion timeout explicitly if 5000 ms is insufficient.
8. Add logout only when its scenario is implemented.
9. Add User Management UI coverage only after inspecting the actual DOM.
10. Consider browser reuse per execution thread only after the current lifecycle is stable.
11. Add full OpenAPI response-schema validation as a distinct contract layer.
12. Extract generic Playwright infrastructure into `regression-core` only after it has proven reusable in another module.

## 25. Design Rules for Future Development

- Model only UI confirmed in the running application.
- Add code from scenario demand, not anticipated reuse.
- Keep environment configuration outside Page Objects.
- Keep locators inside Page Objects or components.
- Keep Cucumber annotations inside Definitions.
- Keep scenario orchestration inside Steps.
- Keep HTTP transport inside API services.
- Keep generated OpenAPI models untouched.
- Fix contract mismatches in the tested application, not by weakening the client.
- Use API for fast data preparation and cleanup.
- Use UI for the behavior the scenario intends to verify.
- Use exact stable `data-cy` locators and remember that values are case-sensitive.
- Treat a missing table as a valid empty state when that matches the real UI.
- Keep scenarios independent and safe after failures.
- Prefer explicit failure over silent tolerance of configuration or DataTable mistakes.

## 26. Final Handoff Summary

The project now has a stable hybrid architecture with a mature API layer and a deliberately small Playwright UI layer.

The finalized active UI model contains three shared components (`BaseComponent`, `NavigationBar`, and `DataTableComponent`), four concrete pages (`LoginPage`, `HomePage`, `BankAccountPage`, and `BankAccountFormPage`), scenario-scoped browser state, UI hooks, and strict bean/header-driven form population.

API and UI responsibilities are separated, while hybrid scenarios use API setup and cleanup to keep browser tests fast and deterministic. Strict OpenAPI deserialization remains enabled, and the earlier Bank Account contract mismatch was fixed in the JHipster application rather than hidden in the framework.

The next work should improve lifecycle failure safety and post-scenario cleanup. New Page Objects and components should be introduced only when a confirmed scenario and inspected DOM require them.
