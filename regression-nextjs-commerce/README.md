# regression-nextjs-commerce

UI automation subproject for the public
[Next.js Commerce demo store](https://demo.vercel.store).

The module is part of the `regression` Maven ecosystem, inherits dependency versions and build
configuration from the parent project, and reuses common mechanisms from `regression-core`.

The project covers browser-based UI testing only. API clients, API services, and API scenarios are
outside the module scope.

## Technology stack

- Java 21
- Maven
- Selenium WebDriver
- Cucumber
- JUnit Platform
- PicoContainer
- AssertJ
- Allure
- Chrome and Chrome mobile emulation

## Project scope

The implemented scenarios cover:

- product catalog search;
- search result validation;
- opening a product;
- selecting product variants;
- adding a product to the cart;
- increasing and decreasing quantity;
- removing a product from the cart;
- validating the empty-cart state.

## Architecture

The module follows this execution flow:

```text
Feature
  -> Definitions
    -> Steps
      -> Pages
        -> Components
          -> WaitManager
            -> Selenium WebDriver
```

Scenario state is passed separately through `CommerceScenarioContext`:

```text
ProductSteps -> CommerceScenarioContext -> CartSteps
```

### Layer responsibilities

| Layer | Responsibility |
|---|---|
| `features` | Describes user behaviour in Gherkin |
| `definitions` | Binds Gherkin expressions to Java and converts Cucumber input |
| `steps` | Orchestrates actions and performs scenario-level assertions |
| `pages` | Represents application routes and composes page components |
| `components` | Encapsulates reusable UI areas and their locators |
| `models` | Contains immutable input and UI-state objects |
| `context` | Stores scenario-scoped data shared between steps |
| `driver` | Creates and manages the browser session |
| `waits` | Provides explicit Selenium waits |
| `hooks` | Controls browser lifecycle and failure diagnostics |

## Source structure

All module code is located under `src/test` because this is a test-only Maven subproject. It does
not produce application or reusable production code.

```text
src/test
├── java/com/aqa/nextjscommerce
│   ├── components
│   ├── config
│   ├── context
│   ├── definitions
│   ├── driver
│   ├── hooks
│   ├── models
│   ├── pages
│   ├── runners
│   ├── steps
│   └── waits
└── resources
    ├── features
    ├── properties
    ├── allure.properties
    ├── cucumber.properties
    └── junit-platform.properties
```

## Definitions and Steps

Definitions are intentionally thin Cucumber adapters.

They:

- declare `@Given`, `@When`, and `@Then` expressions;
- receive values from Gherkin;
- convert `DataTable` input;
- delegate execution to the Steps layer.

Definitions do not contain:

- Selenium calls;
- Page Object operations;
- assertions;
- scenario state;
- business-flow orchestration.

Methods are ordered as `Given -> When -> Then` where applicable.

The scenarios use the domain actor `the customer`. The fact that a scenario is a UI test is
expressed through the `@ui` tag instead of technical wording in Gherkin.

Steps classes contain orchestration and scenario-level assertions:

- `CatalogSteps` is stateless;
- `ProductSteps` records the selected product configuration;
- `CartSteps` reads that configuration and compares it with the cart state.

## Pages

`CommercePages` is the scenario-scoped entry point to all Page Objects.

It receives `DriverSession` through PicoContainer and creates a shared `PageContext` containing:

- `WebDriver`;
- `UiSettings`.

This keeps `DriverSession` outside individual Page Objects while preserving lazy browser
initialization.

`BasePage` contains only reusable page-level behaviour:

- navigation relative to the configured base URL;
- page-load synchronization;
- current URL and title access;
- shared header and cart components.

Each concrete page implements its own page-specific load condition and behaviour.

## Components

Components represent reusable regions of the interface:

- `HeaderComponent`;
- `ProductGridComponent`;
- `CartDrawerComponent`.

All component locators are declared at the top of the class as `private static final By`.

A locator that depends on runtime data is represented by a static locator template and a dedicated
factory method.

Components use their own search root instead of searching the entire document directly. This
reduces locator ambiguity and allows the same component abstraction to work inside:

- the browser document;
- a parent element;
- an open Shadow DOM root.

`ComponentRoot` resolves the required `SearchContext` lazily. The root is resolved again during
wait polling, which prevents components from retaining stale elements after a UI re-render.

The current application can use regular document roots, while the component infrastructure is
prepared for open Shadow DOM components if they are introduced later.

## Models

The module uses immutable Java records:

```java
ProductSelection
CartItem
```

Records are preferred for:

- scenario input;
- immutable DTOs;
- expected UI state;
- UI snapshots.

Mutable Java Beans should be introduced only when a model requires progressive population,
setters, or no-argument construction semantics.

## Scenario context

`CommerceScenarioContext` is created by PicoContainer for each Cucumber scenario.

It currently stores the expected `ProductSelection` produced by `ProductSteps` and consumed by
`CartSteps`.

The context must not contain:

- WebDriver;
- Page Objects;
- Selenium actions;
- assertions;
- unrelated action history.

Only data that must cross a step-class boundary should be stored there.

A future improvement is to make `CommerceScenarioContext` a typed domain facade over the shared
`VariablesController` from `regression-core`. This will allow domain-specific access while keeping
values visible to reusable core variable assertions.

## Driver lifecycle

`DriverSession` owns one WebDriver instance per Cucumber scenario.

The lifecycle is:

1. PicoContainer creates a scenario-scoped `DriverSession`.
2. `UiHooks` starts the browser before an `@ui` scenario.
3. Pages and components use the same driver during the scenario.
4. On failure, diagnostics are attached to the test result.
5. The browser is closed in the `finally` block of the `@After` hook.
6. The driver reference is cleared after `quit()`.

A new browser is created for every scenario. Cookie deletion is therefore unnecessary and is not
used as an isolation mechanism.

The following failure artifacts are attached:

- screenshot;
- current URL;
- page title;
- page source;
- diagnostic attachment if collecting an artifact fails.

## Wait strategy

The module uses explicit waits through `WaitManager`.

The explicit wait duration defines how long Selenium may poll for a UI condition before failing.
It is separate from the page-load timeout, which controls navigation completion.

The framework does not mix implicit and explicit waits.

`WaitManager` supports operations scoped to a `SearchContext`, including regular elements and open
Shadow DOM roots.

## Reuse from regression-core

The module depends directly on `regression-core` and reuses shared mechanisms where appropriate.

Currently reused functionality includes:

- `PropertyReader`;
- `DataTableConverter`;
- common Maven dependency and plugin configuration.

For example, a product options table is converted directly into an immutable model:

```gherkin
And the customer selects these product options:
  | product              | color | size |
  | Acme Circles T-Shirt | Black | S    |
```

```java
convertToSingle(table, ProductSelection.class)
```

The module does not depend on sibling regression subprojects.

## Configuration

Default configuration is stored in:

```text
src/test/resources/properties/dev.properties
```

Available properties:

| Property | Purpose |
|---|---|
| `url.ui` | Storefront base URL |
| `ui.browser` | Browser type |
| `ui.headless` | Headless browser mode |
| `ui.explicit.wait.seconds` | Explicit UI wait timeout |
| `ui.page.load.timeout.seconds` | Page-load timeout |
| `ui.window.width` | Desktop browser width |
| `ui.window.height` | Desktop browser height |
| `ui.mobile.enabled` | Enables Chrome mobile emulation |
| `ui.mobile.device` | Chrome mobile device profile |

Default values:

```properties
url.ui=https://demo.vercel.store
ui.browser=chrome
ui.headless=true
ui.explicit.wait.seconds=15
ui.page.load.timeout.seconds=45
ui.window.width=1440
ui.window.height=1000
ui.mobile.enabled=false
ui.mobile.device=Pixel 7
```

## Running tests

See the top-level [`README.md`](../README.md#prerequisites) "Prerequisites" section for the browser/WebDriver and network requirements this module needs before running.

Run the module and build its required dependencies from the regression repository root:

```bash
mvn -pl regression-nextjs-commerce -am clean test
```

Run only this module after its parent and dependencies have already been installed:

```bash
mvn -pl regression-nextjs-commerce test
```

Run with visible Chrome:

```bash
mvn -pl regression-nextjs-commerce test \
  -Dui.headless=false
```

Run Chrome mobile emulation:

```bash
mvn -pl regression-nextjs-commerce test \
  -Pmobile
```

Run tests with a Cucumber tag expression:

```bash
mvn -pl regression-nextjs-commerce test \
  -Dcucumber.filter.tags="@smoke and @cart"
```

Override the target URL:

```bash
mvn -pl regression-nextjs-commerce test \
  -Durl.ui=https://demo.vercel.store
```

## Cucumber tags

Current feature tags include:

| Tag | Purpose |
|---|---|
| `@ui` | Activates UI hooks |
| `@smoke` | Marks the primary smoke scenarios |
| `@catalog` | Product catalog scenarios |
| `@cart` | Shopping cart scenarios |
| `@wip` | Excluded by the default runner configuration |

The glue package is configured in both:

- `cucumber.properties`;
- `junit-platform.properties`.

This supports execution through `RunCucumberTest` and direct execution of a feature or scenario
from IntelliJ without scanning unrelated packages from `regression-core`.

## Reports

Allure result files are generated in:

```text
regression-nextjs-commerce/target/allure-results
```

If Allure CLI is installed, the report can be opened with:

```bash
allure serve regression-nextjs-commerce/target/allure-results
```

## Parallel execution

Parallel Cucumber execution is currently disabled.

The module architecture supports scenario-level isolation because PicoContainer creates separate
instances of:

- `DriverSession`;
- `CommercePages`;
- `CommerceScenarioContext`;
- Definitions and Steps classes.

Static fields are allowed only for immutable constants such as locators.

Before enabling parallel execution for the complete regression project, shared static state in
`regression-core`, including lazy singleton initialization, must be audited for thread safety.
Browser concurrency must also be limited according to the available machine or CI resources.

## Adding new UI coverage

When a new element is added to an existing component:

1. Add its locator as `private static final By`.
2. Add a semantic component method.
3. Use `WaitManager` instead of direct timing or sleeps.
4. Expose the operation through the relevant Page Object when necessary.
5. Add orchestration or assertions in the Steps layer.
6. Add only the Gherkin binding to Definitions.

When a new reusable UI region is introduced:

1. Create a component extending `BaseComponent`.
2. Define its document, element, or Shadow DOM root.
3. Keep all searches relative to that root.
4. Compose the component in the appropriate Page Object.

When a new route is introduced:

1. Create a Page Object extending `BasePage`.
2. Implement its page-specific load condition.
3. Add its construction to `CommercePages`.
4. Keep route-specific locators and behaviour inside that page.

This separation keeps Gherkin readable, assertions centralized, Page Objects focused, and Selenium
details isolated from the business-level test flow.