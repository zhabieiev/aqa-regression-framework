# regression-jhipster Hybrid API/UI Automation Framework

## Purpose

`regression-jhipster` is a child module of the multi-module Maven project `com.aqa:regression:1.0.0`. It is a practice and demonstration framework for API, UI, and hybrid test automation against a locally deployed `jhipster-sample-app`.

JHipster replaced earlier test applications such as Juice Shop and Toolshop because it provides:

- full CRUD operations;
- JWT authentication and role-based authorization;
- runtime Swagger/OpenAPI documentation;
- no artificial limit on the number of test records;
- a real web UI suitable for Playwright automation;
- Docker-based local deployment.

The framework uses Java 21, Maven, Cucumber, Jersey, Jackson, OpenAPI Generator, AssertJ, Lombok, and Playwright.

## Current Status

### Confirmed and working

- JHipster is available locally at `http://localhost:8080`.
- `/v3/api-docs` is available without authentication.
- OpenAPI 3.1 models are generated successfully.
- Admin and regular-user JWT authentication works.
- Admin user creation and deletion work through API.
- Negative authentication with `401` and a problem response works.
- Bank Account API CRUD infrastructure works.
- Bank Accounts can be searched by name and all duplicates can be deleted through API.
- Strict Jackson deserialization detects undocumented response fields.
- The JHipster `BankAccount` response was aligned with its OpenAPI schema by hiding `operations` and `user` from JSON serialization.
- Playwright browser lifecycle works from Cucumber hooks.
- UI login with valid credentials works.
- UI scenarios are isolated with a separate browser context and page.
- Screenshots and traces are supported on failure.
- Shared UI components and Page Objects have been introduced.

### Implemented but still being stabilized

- Bank Account creation and deletion through UI.
- Hybrid scenarios that prepare or clean data through API and verify behavior through UI.
- Login data population through `Populator` and a UI bean.
- Bank Account data population through `Populator` and a UI bean.

### Explicitly excluded

`AccountSettingsPage` was initially designed based on a common JHipster route, but the page was not confirmed in the actual application. It must not be treated as part of the current framework. Related account-settings classes and scenarios should be removed unless the route is later verified in the real UI.

## Maven Module Structure

```text
regression
├── regression-core
├── regression-petstore-api
├── regression-juiceshop
├── regression-toolshop
└── regression-jhipster
```

Responsibilities:

- `regression-core`: shared configuration, HTTP request infrastructure, converters, `Populator`, `VariablesController`, and reusable Cucumber functionality.
- `regression-jhipster`: JHipster-specific API services, generated models, UI Page Objects, components, steps, definitions, hooks, and feature files.

## Recommended Module Layout

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
│   │   │           ├── context
│   │   │           ├── definitions
│   │   │           ├── hooks
│   │   │           ├── models
│   │   │           ├── pages
│   │   │           └── steps
│   │   └── resources
│   │       └── properties
│   │           └── dev.properties
│   └── test
│       └── resources
│           └── features
│               ├── api
│               └── ui
│                   ├── authentication
│                   └── bank_account
├── pom.xml
└── README.md
```

The existing module keeps definitions and framework implementation under `src/main/java`. New UI code currently follows that convention to remain consistent with the API module.

## Core Dependencies

### Runtime and test stack

- Java 21
- Maven multi-module build
- Cucumber Java
- Cucumber PicoContainer for scenario-scoped constructor injection
- Jakarta REST/Jersey Client
- Jackson Databind and Java Time module
- OpenAPI Generator
- Swagger annotations
- AssertJ
- Lombok
- Microsoft Playwright for Java
- Docker Compose and Jib for the JHipster application

### Important dependency behavior

- Most versions are inherited from the parent POM.
- `regression-jhipster` overrides OpenAPI Generator with version `7.24.0` because the JHipster runtime specification uses OpenAPI 3.1.
- The parent generator version must not be globally upgraded without checking other modules. Earlier global changes broke Petstore and Toolshop generated sources.
- `cucumber-picocontainer` is required for constructor injection and scenario-scoped state objects.
- `jersey-media-json-jackson` must remain available for Jersey response deserialization.

## Configuration

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

The project currently also uses the shared `Property` enumeration from `regression-core`. Calling `Property.read()` already returns the resolved value. Do not pass the result into `PropertiesController.getProperty()` again.

Incorrect:

```java
PropertiesController.getProperty(UI_BROWSER.read());
```

Correct:

```java
UI_BROWSER.read();
```

`data-cy` is a stable JHipster UI convention, not an environment property. It is configured as a Playwright test ID attribute:

```java
private static final String TEST_ID_ATTRIBUTE = "data-cy";

playwright.selectors().setTestIdAttribute(TEST_ID_ATTRIBUTE);
```

Page Objects and components then use:

```java
page.getByTestId("accountMenu");
```

## API Architecture

The API responsibility chain is:

```text
Cucumber Definition
    → API Steps
    → Domain Service
    → ApiService
    → GeneralApiService
    → Jersey Client
```

### Responsibilities

- Definitions bind Gherkin to Java, convert DataTables, and save named values.
- Steps orchestrate business operations across one or more services.
- Domain services define endpoint paths, methods, request bodies, response types, and expected status codes.
- `ApiService` supplies the configured base URI.
- `GeneralApiService` builds and sends requests and validates expected status codes.
- `ClientController` creates the Jersey Client and supplies Jackson configuration.

Definitions must not expose `bankAccountService()` or `authService()` accessors from Steps. Instead, Steps should provide meaningful operations such as:

```java
deleteAllByName(name);
```

## Authentication

- Endpoint: `POST /api/authenticate`
- Request model: `LoginVM`
- Response model: `JWTToken`
- Token JSON property: `id_token`
- Authorization header: `Authorization: Bearer <token>`

`AuthService` caches admin and regular-user tokens separately and can also authenticate arbitrary credentials.

Headers are passed explicitly per request rather than attached through a global Jersey filter. This is intentional because security tests need to send:

- an admin token;
- a regular-user token;
- no token;
- an invalid token;
- a token with insufficient authority.

## Strict OpenAPI Contract Strategy

Generated models are treated as the transport contract and must never be edited manually.

Source specification:

```text
http://localhost:8080/v3/api-docs
```

Generated package:

```text
com.aqa.jhipster.api.models.generated
```

The framework intentionally keeps strict Jackson behavior:

```text
FAIL_ON_UNKNOWN_PROPERTIES = true
```

An undocumented response property must fail deserialization. This behavior previously detected that the runtime `BankAccount` response contained:

```json
{
  "operations": null,
  "user": null
}
```

while the OpenAPI schema contained only:

```text
id
name
balance
```

The generated model was correct. The JHipster response was fixed at the source by adding a class-level Jackson exclusion to `BankAccount`:

```java
@JsonIgnoreProperties(
    value = { "operations", "user" },
    allowSetters = true
)
```

This change belongs in the JHipster source project, must be committed there, and requires rebuilding the Docker image. Editing a running container is not persistent and does not change already compiled Java classes.

The field-level annotation on `operations` has a different purpose: it prevents recursive serialization of nested relationship properties. It does not hide the `operations` property itself.

## API Bank Account Operations

`BankAccountService` owns transport-level calls such as:

- list accounts;
- create an account;
- delete an account by ID;
- validate `204 No Content` for deletion.

Deletion logs only after successful status validation:

```java
log.info("Bank account with {} id is deleted", id);
```

The API Steps layer provides orchestration such as deleting every account with a matching name:

```text
GET all accounts
    → filter by exact name
    → collect IDs
    → DELETE every matching ID
```

`findFirst()` must not be used for cleanup because duplicate names are allowed by the database and it would delete only one record.

The cleanup operation is intentionally idempotent: when no records match, it succeeds without deleting anything.

Known limitation: JHipster list endpoints may be paginated. A cleanup implementation that reads only the first page cannot guarantee removal of matches on later pages. Prefer a server-side name filter when available, otherwise iterate over all pages.

## UI Architecture

The UI responsibility chain is:

```text
Cucumber Feature
    → UI Definition
    → UI Steps
    → Page Object
    → Component
    → Playwright
```

### Layer rules

- Definitions contain Cucumber annotations and DataTable conversion only.
- Steps own scenario orchestration and current Page Object state.
- Page Objects describe page-level behavior and navigation.
- Components describe reusable UI regions.
- UI beans contain test data only; they never contain Playwright `Page` or locators.
- Hooks own browser lifecycle, traces, screenshots, and resource cleanup.

## Playwright Lifecycle

Current safe lifecycle:

```text
Each @ui scenario
    → Playwright.create()
    → launch Browser
    → create BrowserContext
    → create Page
    → execute scenario
    → screenshot on failure
    → save trace on failure
    → close BrowserContext
    → close Browser
    → close Playwright
```

Key classes:

- `PlaywrightManager`
- `UiScenarioContext`
- `UiHooks`

`UiScenarioContext` is scenario-scoped through PicoContainer and holds the current `BrowserContext` and `Page`.

The initial lifecycle creates a browser per scenario. This is slower but correctly isolated and safe. Playwright Java objects are not thread-safe. Future optimization may reuse one browser per execution thread, but a single global static `Page` or `BrowserContext` must never be shared across parallel scenarios.

`BrowserContext` implements `AutoCloseable`. IntelliJ may suggest try-with-resources at every getter call, but the context must remain alive for the complete scenario. It is closed centrally in the `@After` hook. Local try-with-resources inside tracing methods would close it too early.

## UI Components

### `BaseComponent`

Stores:

- Playwright `Page`;
- mandatory root `Locator`.

It provides:

- `waitUntilDisplayed()` using Playwright web-first assertions;
- `byDataCy()` implemented through `page.getByTestId()`.

Thin wrappers such as `assertVisible(locator)` were removed because they add no behavior over Playwright assertions.

### `NavigationBar`

Uses `accountMenu` as its root. It supports:

- account menu access;
- logout;
- administration menu navigation;
- user-management navigation;
- entity menu navigation;
- Bank Account navigation;
- authentication-state verification.

### `DataTableComponent`

Wraps a table locator and provides:

- readiness assertion;
- row lookup by text;
- row-present and row-absent assertions;
- row count.

Important: the Bank Account list page does not render a table when the list is empty. It renders an empty-state message instead. Therefore, `BankAccountPage.waitUntilLoaded()` must not require the table. It waits for the heading and create button. Methods that require data wait for `DataTableComponent` locally.

### `AlertComponent`

Uses a logical union of success and error alerts as the root. It provides success/error visibility and message assertions.

## Page Object Conventions

### `BasePage`

Responsibilities:

- store the Playwright `Page`;
- navigate using the `baseURL` configured in `BrowserContext`;
- call the target page's readiness check;
- provide `byDataCy()` through Playwright test IDs;
- provide URL assertions.

`BasePage` does not read `url.ui`; configuration belongs to `PlaywrightManager`.

The internal navigation method is named `navigateTo(path)`, not `open(path)`, to avoid ambiguity with public page methods.

`Pattern.quote()` must not be used for Playwright URL regex assertions because it produces Java-specific `\Q...\E` syntax that is not supported when the pattern is evaluated by browser-side JavaScript.

### Fluent return types

`BasePage` declares:

```java
public abstract BasePage waitUntilLoaded();
```

Concrete pages use covariant returns:

```java
public BankAccountPage waitUntilLoaded();
public LoginPage waitUntilLoaded();
public HomePage waitUntilLoaded();
```

Rules:

- an action remaining on the same page returns `this`;
- navigation returns the target Page Object;
- a query returns a value;
- internal low-level helpers may return `void`.

### Confirmed pages

- `LoginPage`
- `HomePage`
- `UserManagementPage`
- `BankAccountPage`
- `BankAccountFormPage`

### Bank Account empty state

Correct readiness condition:

```text
URL contains /bank-account
heading is visible
create button is visible
```

The table is optional. After creating a record, account assertions explicitly wait for the table.

### Strict row selection

Operations that edit, delete, or assert a specific account should first assert that exactly one row matches the test name. This detects leftover duplicates rather than allowing Playwright to click an arbitrary match.

## UI Data Population

UI forms follow the same pattern as existing API tests:

```text
Cucumber DataTable
    → List<Map<String, String>>
    → Populator.populateList()
    → UI bean
    → headers
    → Steps
    → Page Object
```

Mutable Lombok beans are preferred here over records because the framework already uses Jackson-based `Populator`, supports partial tables, and sometimes needs to distinguish an absent column from a default primitive value.

Current UI beans:

- `LoginBean`
- `BankAccountBean`

`LoginBean.password` must be excluded from `toString()` to reduce accidental credential logging.

Headers are passed separately because they identify which fields were explicitly included in a DataTable. Page Objects use a strict `switch` over headers. Unknown headers throw `IllegalArgumentException`.

This strict switch is important because the shared JSON reader currently ignores unknown properties; without explicit header validation, a typo such as `firstNeme` could be silently ignored by `Populator`.

## Login UI Flow

Feature style:

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
```

The `ui user` prefix intentionally distinguishes UI steps from existing `api user` steps and prevents ambiguous Cucumber definitions in hybrid scenarios.

Responsibilities:

- `LoginDefinitions`: populate one `LoginBean`, extract headers, delegate.
- `LoginSteps`: keep the current `LoginPage` and `HomePage` for the scenario.
- `LoginPage`: fill fields based on headers, submit credentials, and expose authentication-error assertions.
- `HomePage`: confirm that navigation left `/login` and that the logout action is available in `NavigationBar`.

Action and verification should be separated for negative login:

```text
When → submit invalid credentials
Then → assert authentication error
```

## Bank Account UI and Hybrid Flows

`BankAccountBean` fields:

```text
name
balance
user
```

`BankAccountFormPage.fillAccount(bean, headers)` fills only explicitly supplied fields and rejects unsupported headers.

Recommended scenarios:

1. UI creation:
   - API deletes all pre-existing accounts with the test name.
   - UI opens the Bank Account list.
   - UI creates an account.
   - UI verifies the row.

2. UI deletion:
   - API deletes pre-existing accounts with the test name.
   - API creates the precondition account.
   - UI opens the Bank Account list.
   - UI verifies and deletes the account.
   - UI verifies absence.

Example:

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

`Background` contains only authentication. The Bank Account page is opened inside each scenario so that API preconditions can be prepared before the UI list loads.

## Test Data Cleanup

API pre-cleanup by name makes fixed-name tests repeatable and removes leftovers from previous failed runs.

It does not replace post-cleanup:

```text
pre-cleanup
    → remove previous leftovers

post-cleanup
    → remove records created by the current run
```

The preferred long-term design is a scenario-scoped cleanup registry that stores IDs of all created test entities and an `@After` hook that deletes them through API even after UI failure.

Until that is implemented, hybrid scenarios should explicitly clean preconditions and avoid names that could match legitimate environment data. Test names should use a clear dedicated prefix.

## Assertions and Waiting Strategy

- Prefer Playwright web-first assertions such as `assertThat(locator).isVisible()`.
- Do not add Selenium-style explicit waits around normal Playwright actions.
- Page readiness must be based on mandatory elements only.
- Optional empty/non-empty page states must be handled in the operation that requires them.
- Do not expose raw `Locator` objects from Page Objects unless a reusable component genuinely requires them.
- Business assertions may return `this` for fluent composition.
- Avoid thin custom assertion wrappers that only rename Playwright APIs.
- Add domain-specific assertions only when they combine or normalize meaningful business checks.

The configured BrowserContext timeout and Playwright assertion timeout may differ. Failure logs previously showed a default assertion timeout of 5000 ms. If a project-wide assertion timeout is required, configure Playwright assertions explicitly rather than assuming `BrowserContext.setDefaultTimeout()` changes assertion defaults.

## Running JHipster

Run commands from the `jhipster-sample-app` root directory in PowerShell.

Start an existing stopped container:

```powershell
docker compose -f .\src\main\docker\app.yml start
```

Start after `docker compose down`:

```powershell
docker compose -f .\src\main\docker\app.yml up -d
```

Rebuild after application source changes:

```powershell
.\mvnw.cmd -ntp verify "-DskipTests" "-Pprod,api-docs" jib:dockerBuild
docker compose -f .\src\main\docker\app.yml up -d --force-recreate
```

Check status and logs:

```powershell
docker compose -f .\src\main\docker\app.yml ps
docker compose -f .\src\main\docker\app.yml logs -f
```

Endpoints:

- UI: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/admin/docs`
- OpenAPI: `http://localhost:8080/v3/api-docs`
- API base URL: `http://localhost:8080/api`

Default local credentials:

- administrator: `admin / admin`
- regular user: `user / user`

## Installing Playwright Chromium

Run once from `regression-jhipster`:

```powershell
mvn exec:java `
  "-Dexec.mainClass=com.microsoft.playwright.CLI" `
  "-Dexec.args=install chromium"
```

## Rebuilding Shared Framework Changes

After changing `regression-core`:

```powershell
mvn -pl regression-core -am clean install -DskipTests
```

Then reload Maven in IntelliJ and rebuild or run the JHipster module.

## Artifacts

Playwright artifacts are stored under:

```text
target/playwright
├── screenshots
└── traces
```

`target` must not be committed.

Traces should normally be persisted only on failure. Video is not currently required because Playwright trace contains more useful action, DOM, network, and screenshot information for this framework stage.

## Known Risks and Follow-up Work

1. Implement reliable post-scenario API cleanup using created entity IDs.
2. Ensure API cleanup traverses all pages or uses server-side filtering.
3. Verify every `data-cy` selector against the actual JHipster DOM. Values are case-sensitive; for example, the confirmed list heading is `BankAccountHeading`, not `bankAccountHeading`.
4. Stabilize Bank Account UI creation and deletion after the empty-table readiness fix.
5. Configure assertion timeout explicitly if 5000 ms is insufficient.
6. Add logout coverage.
7. Add admin authorization coverage for User Management.
8. Add screenshots and trace verification tests.
9. Decide whether browser reuse per worker thread is worth the additional lifecycle complexity.
10. Consider extracting truly generic Playwright infrastructure into `regression-core` only after the JHipster implementation is stable.
11. Add strict OpenAPI response validation in addition to strict DTO deserialization. Jackson alone does not validate every schema constraint, required field, media type, status response, format, or numeric boundary.
12. Avoid calling one Steps class from another long-term. Shared hybrid test-data preparation should move into a dedicated test-data service used by both API/UI orchestration and cleanup hooks.

## Design Principles for Further Work

- Model only UI pages and components confirmed in the real application.
- Keep environment configuration outside Page Objects.
- Keep transport logic in API services.
- Keep business orchestration in Steps.
- Keep Cucumber binding and table conversion in Definitions.
- Keep generated OpenAPI models immutable and regenerate them from the runtime specification.
- Fix contract mismatches at the JHipster source, not by weakening strict deserialization.
- Use API for fast test-data preparation and cleanup; use UI only for the behavior being verified.
- Keep scenarios independent, repeatable, and safe after failures.
- Prefer exact, stable `data-cy` locators and Playwright web-first assertions.
- Treat absence of a table as a valid empty state when the real application behaves that way.

## Handoff Summary

The framework now has a mature API foundation and a working Playwright/Cucumber UI foundation. Authentication is operational, shared UI abstractions are established, strict OpenAPI behavior is preserved, and Bank Account hybrid scenarios define the next integration boundary.

The immediate priority is to finish Bank Account UI stabilization and implement ID-based post-scenario cleanup. After that, the framework can expand coverage without changing its core layering.
