# Regression Automation Framework

This repository is a Java 21 Maven reactor for regression automation across API and browser-based UI systems. It separates reusable technical infrastructure from product-specific test modules so that each product can use the test runner and UI/API stack appropriate to its needs.

## Maven structure

```text
regression (com.aqa:regression, packaging=pom)
|-- regression-core
|-- regression-petstore-api
|-- regression-jhipster
|-- regression-nextjs-commerce
\-- regression-mcp-server
```

`regression` is the parent and reactor aggregator. It manages Java 21, shared dependency versions, common compiler and Surefire configuration, the default `env=dev` setting, and OpenAPI Generator defaults.

Each child module inherits from the parent POM. Product modules depend on `regression-core` where shared mechanisms apply; they do not declare dependencies on one another.

| Module | Responsibility |
| --- | --- |
| `regression-core` | Shared automation infrastructure: environment-property access, scenario variables, data-table and string conversion, object population, request construction, Jersey HTTP transport, response assertions, JSON/XML handling, and S3 support. It also contains reusable Cucumber definitions and feature tests. |
| `regression-petstore-api` | JUnit 5 API regression tests for Petstore. It provides product API services and steps, OpenAPI-generated models, test data factories, cleanup support, and module-local Allure reporting. |
| `regression-jhipster` | Hybrid Cucumber API and Playwright UI automation for a JHipster application. It contains product services, steps, generated models, UI pages/components, scenario lifecycle hooks, and Gherkin features. |
| `regression-nextjs-commerce` | Test-only Selenium/Cucumber UI automation for the Next.js Commerce demo storefront. It owns Selenium pages, components, explicit waits, scenario context, driver lifecycle, and UI features. It does not add API clients or API scenarios. |

## Technology stack

- Java 21 and Maven
- JUnit Jupiter and JUnit Platform
- Cucumber with PicoContainer
- Jersey / Jakarta REST for shared API transport
- Jackson for JSON and XML mapping
- AWS SDK v2 S3 support
- Playwright in `regression-jhipster`
- Selenium WebDriver in `regression-nextjs-commerce`
- AssertJ, SLF4J, Lombok, and OpenAPI Generator
- Allure configuration in `regression-petstore-api`

## Architecture

The framework keeps reusable infrastructure in `regression-core` and product behaviour in the corresponding product module.

API-oriented flows use the following separation:

```text
Gherkin or JUnit test
  -> definitions or domain steps
    -> product API service
      -> regression-core request model and Jersey client
        -> target API
```

UI-oriented Cucumber flows use:

```text
Gherkin
  -> definitions
    -> steps
      -> pages
        -> components
          -> browser client
```

Definitions bind Gherkin input and delegate. Steps orchestrate the scenario and own scenario-level assertions. Pages own route behaviour; components own reusable DOM regions. The Next.js Commerce module uses `WaitManager` and Selenium `WebDriverWait` for explicit synchronization. JHipster UI scenarios use Playwright pages, browser contexts, and hooks.

## Configuration and reporting

The parent POM exposes the `env` property to Maven test execution, defaulting to `dev`. Core and product modules load environment-specific property files from their own resources where configured.

Petstore declares Allure dependencies and a Maven reporting plugin. Its `run-tests.sh` script manages local Allure metadata, report generation, history, and an interactive local report server. The script is intentionally a local workflow; it is not described here as a CI reporting solution.

JHipster reads its local API/UI endpoint and browser settings from its `dev.properties`. Next.js Commerce reads its UI URL, browser, headless, timeout, window, and mobile-emulation settings from `src/test/resources/properties/dev.properties`; its Maven `mobile` profile enables mobile emulation.

## Running Maven

Run these commands from the repository root. Product tests may require their configured external application or target service to be available.

Validate the reactor POMs without running tests:

```bash
mvn validate
```

Run the shared core feature tests:

```bash
mvn -pl :regression-core test
```

Run an individual product module together with the reactor modules it requires:

```bash
mvn -pl :regression-petstore-api -am test
mvn -pl :regression-jhipster -am test
mvn -pl :regression-nextjs-commerce -am test
```

Run the Next.js Commerce module with its mobile profile:

```bash
mvn -pl :regression-nextjs-commerce -am test -Pmobile
```

Run the complete reactor:

```bash
mvn test
```

## Roadmap

Confirmed improvement areas include:

- Add a failure-safe fallback cleanup path for the Petstore delete scenario.
- Define a non-interactive CI reporting workflow and durable Allure-history/artifact policy.
- Document a common module-execution convention for local and CI use.
- Maintain an OpenAPI Generator compatibility matrix for modules that generate models.
- Add schema and contract validation as a layer separate from DTO mapping.
- In JHipster, add a dedicated UI assertion timeout, an ID-based scenario cleanup registry, and pagination-aware API cleanup.

## Further reading

Each product module has its own documentation with its product-specific configuration and execution details:

- `regression-petstore-api/README.md`
- `regression-jhipster/README.MD`
- `regression-nextjs-commerce/README.md`

## MCP inspection server

`regression-mcp-server` gives an AI coding agent (Codex, Claude, etc.) a safe, structured way to work with this reactor's tests without shell access or direct file reads: discover modules/features/scenarios, run the one supported test suite and wait for a result, get a precise diagnosis when something fails (Surefire/Allure detail, not raw logs), and check the test framework's own architectural health. It is an isolated Java 21 MCP Java SDK 2.0.0 STDIO server exposing 14 deterministic, closed-schema tools: 4 discovery tools, 3 execution tools, 4 report/artifact tools, and 3 architecture-validator tools. Its only configurable boundary is `REGRESSION_ROOT`, and standard output is reserved exclusively for MCP JSON-RPC.

Test **execution** (start/get/cancel) is intentionally narrow in v1.0: only `regression-nextjs-commerce`, only the `dev` environment. Discovery and the architecture validators work across all 5 reactor modules; only running a test and diagnosing its failure is limited to nextjs-commerce today.

See [`regression-mcp-server/README.md`](regression-mcp-server/README.md) for installation, IDEA/Codex client configuration, the security model, the execution lifecycle, the run store, artifact limits, JAR-lock troubleshooting, and known v1.0 limitations, and [`regression-mcp-server/docs/TOOLS.md`](regression-mcp-server/docs/TOOLS.md) for every tool's full input/output schema.

Stages 14, 15, and 16 are complete — `regression-mcp-server` shipped as v1.0.0 (tag `regression-mcp-server-v1.0.0`). See [`docs/dev-history/STAGE_15_16_KICKOFF.md`](docs/dev-history/STAGE_15_16_KICKOFF.md) for the original Stage 15/16 scope and [`docs/dev-history/STAGE_15_PROGRESS.md`](docs/dev-history/STAGE_15_PROGRESS.md) for the as-built development record.
