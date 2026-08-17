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

`regression-mcp-server` is an isolated Java 21 MCP Java SDK 2.0.0 STDIO server. It provides deterministic discovery tools, Stage 13 execution tools (`regression_start_test_run`, `regression_get_test_run`, `regression_cancel_test_run`), and Stage 14 report/artifact tools (`regression_get_test_summary`, `regression_get_failure_summary`, `regression_get_failure_artifacts`, `regression_read_failure_artifact`). Execution v1 is intentionally limited to `regression-nextjs-commerce`, `dev`, a Boolean `headless` value, a 30–1800 second timeout, and an optional 1024-character Cucumber tag expression. Start requires `module`, `environment`, `headless`, and `timeoutSeconds`, and accepts only optional `tags`; get and cancel accept only the server-generated `runId`. The server always adds `not @wip`; a client `@cart` request becomes `(@cart) and not @wip`.

The server reads its only repository boundary from `REGRESSION_ROOT`. The value must resolve to an existing directory containing the reactor root `pom.xml`. Package the executable server and run it with:

```bash
mvn -pl regression-mcp-server -am clean verify
REGRESSION_ROOT=/path/to/regression REGRESSION_MAVEN_HOME=/path/to/apache-maven java -jar regression-mcp-server/target/regression-mcp-server.jar
```

Standard output is reserved for MCP JSON-RPC. Diagnostics are written to standard error.

Every tool declares closed schemas and structured `status` envelopes. Execution runs are server-generated below `.regression-mcp/runs/<runId>` with immutable `run.json`, atomically replaced `status.json`, and separate 16 MiB stdout/stderr logs; this ignored directory is never client-addressable. Each stream retains a final 64 KiB in-memory tail after its persisted log cap. Only one run may be active. States are `QUEUED`, `RUNNING`, `PASSED`, `FAILED`, `CANCELLED`, `TIMED_OUT`, and `ERROR`.

The coordinator records every observed owned process as a PID plus start instant, parent PID when known, and observation depth. It never terminates by PID alone, process name, shell, `taskkill`, or WMI. Cancellation, timeout, EOF, JVM shutdown, and stale-run recovery use the same bounded, deepest-first identity-safe cleanup path; an unprovable possibly-live stale identity blocks a new execution until recovery is resolved. Maven is launched through trusted Java and Maven Classworlds arguments, never `mvn.cmd` or a shell; Maven Surefire may create a downstream Windows `cmd.exe`. Standard output remains JSON-RPC-only. External Commerce smoke execution is manual. Stage 14 adds bounded, authoritative Surefire summaries with optional capture-time-only Allure enrichment (`regression_get_test_summary`, `regression_get_failure_summary`), plus server-generated-`artifactId`-only failure-artifact listing and reading with a MIME allow-list and a bounded response size (`regression_get_failure_artifacts`, `regression_read_failure_artifact`); all four require a terminal server-generated run and return structured `NOT_FOUND` for a missing, foreign, or stale run or artifact. If Windows locks the shaded JAR, stop the configured MCP server, confirm its Java process has exited, then rebuild and restart it. The MCP workflow is ready for Windows and Linux; Linux requires all MCP security tests, including symlink checks, to execute.
