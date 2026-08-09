# regression-petstore-api

## Purpose

`regression-petstore-api` is a Java 21 API test automation module for the public Swagger Petstore service. It is part of the larger `regression` Maven ecosystem and demonstrates API testing with pure JUnit 5, without Gherkin or Cucumber in the test layer.

The module focuses on maintainable API architecture, OpenAPI-generated DTOs, parallel-safe test data, deterministic cleanup, and Allure reporting with local execution history.

## Ecosystem Context

```text
regression
├── regression-core
├── regression-petstore-api
└── other product-specific modules
```

The root project owns dependency and plugin versions. `regression-petstore-api` adds only module-specific dependencies and configuration.

`regression-core` provides reusable infrastructure, including:

- property loading and shared configuration;
- the common request model and builder;
- Jersey client execution;
- response status validation;
- common serialization, logging, and API utilities.

Petstore owns its endpoint services, orchestration, generated transport models, test-data factories, JUnit extensions, and tests. Although `regression-core` also supports Cucumber-based modules, Petstore does not use that layer.

## Technology Stack

- Java 21;
- Maven multi-module build;
- JUnit 5 and JUnit Platform;
- Jakarta REST/Jersey Client through `regression-core`;
- Jackson for serialization and deserialization;
- OpenAPI Generator for Petstore DTOs;
- AssertJ for recursive object comparison;
- DataFaker for dynamic test data;
- Lombok for logging;
- Allure JUnit 5 and Allure Maven Plugin.

## Module Structure

```text
regression-petstore-api
├── src
│   ├── main
│   │   ├── java/com/aqa/petstore/api
│   │   │   ├── models/generated
│   │   │   ├── services
│   │   │   └── steps
│   │   └── resources/properties
│   │       ├── common.properties
│   │       └── dev.properties
│   └── test
│       ├── java/com/aqa/petstore/api
│       │   ├── data
│       │   ├── extensions
│       │   └── tests
│       └── resources
│           ├── allure.properties
│           ├── categories.json
│           └── junit-platform.properties
├── local-allure-history
│   └── .gitignore
├── pom.xml
├── run-tests.sh
└── README.md
```

## Architecture

```text
JUnit Test
    → Domain Steps
    → Endpoint Service
    → PetStoreApiService
    → GeneralApiService from regression-core
    → Jersey Client
    → Swagger Petstore API
```

| Layer | Responsibility |
| --- | --- |
| JUnit test | Scenario intent, assertions, tags, severity, and cleanup registration |
| Steps | Domain orchestration and successful business-operation logging |
| Service | Endpoint path, HTTP method, request body, expected status, and response DTO |
| PetStoreApiService | Petstore base URI integration with the shared API client |
| regression-core | Request execution, response validation, JSON handling, and common infrastructure |

Services do not contain assertions or test orchestration. Steps do not construct HTTP requests. Tests interact with domain Steps and compare generated DTOs with deserialized responses.

## OpenAPI Models

Transport models are generated into:

```text
com.aqa.petstore.api.models.generated
```

The source specification is resolved from the configured API URL:

```text
https://petstore.swagger.io/v2/swagger.json
```

Generated classes must not be edited manually. Regenerate them after a contract change:

```bash
mvn -pl regression-petstore-api generate-sources \
    -Dmodels.petstore.api.skip.generate=false
```

An OpenAPI Generator version change should be verified against every module in the parent reactor before it is accepted globally.

## Test Strategy

Current coverage includes:

- creating and deleting a pet;
- creating, retrieving, and deleting a store order;
- validating `404 Not Found` for an unknown order;
- positive and negative response deserialization;
- recursive request/response comparison.

Test data is created through dedicated DataFaker factories. `TestRunId` combines a run-specific value with atomic sequences to reduce collisions during parallel execution. Tests therefore do not contain long explicit DTO constructors or reusable static entity identifiers.

`CleanupExtension` registers cleanup immediately after resource creation, executes actions in reverse order, attempts every registered action, and aggregates cleanup failures. Cleanup is executed even when the test assertion fails.

## Parallel Execution

JUnit parallel execution is enabled with a fixed pool of four threads:

```properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.mode.classes.default=concurrent
junit.jupiter.execution.parallel.config.fixed.parallelism=4
```

Pet tests may run concurrently because they use unique long identifiers. Store Order tests use a shared public endpoint with a restricted ID range, so the class is protected by a JUnit `ResourceLock`. This deliberately trades some throughput for predictable isolation.

Any new test must be reviewed for shared state before it is allowed to run concurrently.

## Allure Reporting

Tests use Allure `Epic`, `Feature`, `Story`, `Severity`, and JUnit tags. Custom categories distinguish:

- cleanup failures;
- external Petstore HTTP 5xx failures;
- network and timeout failures;
- serialization and deserialization failures.

`run-tests.sh` preserves Allure 2 history through `local-allure-history`, assigns a sequential build number, generates execution metadata, and opens the report through the JDK web server. Trend data becomes meaningful after multiple script-based runs.

Generated Allure runtime, reports, results, and history JSON files are excluded from Git.

## Running the Module

### Prerequisites

- JDK 21;
- Maven available from the command line;
- Bash, including Git Bash on Windows;
- internet access to `https://petstore.swagger.io`.

### Recommended execution

From the module directory:

```bash
chmod +x run-tests.sh
./run-tests.sh
```

The script:

1. cleans the module and required projects;
2. restores the previous Allure history and creates execution metadata;
3. builds and installs `regression-core` without running its Cucumber tests;
4. runs only Petstore JUnit tests;
5. generates the report even if tests fail;
6. saves the updated Trend history;
7. opens the report at `http://localhost:8000`;
8. returns the original test exit code after the report server stops.

Run one test class:

```bash
./run-tests.sh -Dtest=PetTest
```

Run smoke tests:

```bash
./run-tests.sh -Dgroups=smoke
```

Use another report port:

```bash
ALLURE_PORT=8081 ./run-tests.sh
```

### Maven-only execution

From the root project:

```bash
mvn -pl :regression-core -am install -Dmaven.test.skip=true
mvn -pl :regression-petstore-api test
mvn -pl :regression-petstore-api allure:report
```

Direct `-pl :regression-petstore-api -am test` is not recommended because Maven will also execute the Cucumber tests from `regression-core`.

## Extension Guidelines

When adding a new endpoint or scenario:

1. regenerate and reuse the OpenAPI DTO whenever the contract already defines it;
2. add endpoint mechanics to a resource-specific Service;
3. add orchestration and successful-operation logging to Steps;
4. create test data in a factory under `src/test/java`;
5. register cleanup immediately after resource creation;
6. keep assertions in the JUnit test;
7. add Allure metadata and meaningful JUnit tags;
8. verify parallel safety and add a narrow `ResourceLock` only for genuinely shared resources;
9. add a custom Allure category only when it provides actionable failure classification.

Do not add Cucumber glue, Gherkin features, hand-written copies of generated DTOs, fixed shared IDs, sleeps, or test-order dependencies.

## Current Limitations and Trade-offs

- The tests use the shared public Swagger Petstore sandbox. Its data and availability are outside the framework's control, and intermittent HTTP 5xx responses are possible.
- Current coverage is intentionally limited to representative Pet and Store Order flows; it is not a complete Petstore regression suite.
- Store Order tests are serialized because of the service's small usable ID range and shared external state.
- Cleanup is best-effort. It cannot run after forced JVM termination, process kill, or infrastructure failure before extension callbacks execute.
- OpenAPI DTO deserialization validates Java model compatibility but does not replace full response-schema or consumer-contract validation.
- DataFaker improves isolation but makes exact test data different between runs; deterministic seeding and richer request/response Allure attachments are not yet implemented.
- Allure Trend history is local. CI must persist `local-allure-history` or use CI-native Allure history storage between jobs.
- The module performs no authentication testing because the public Petstore endpoints used here do not require an application-specific token flow.

## Recommended Next Improvements

- attach sanitized API requests and responses to Allure;
- introduce targeted retry only for confirmed transient transport failures;
- add contract validation as a separate layer rather than weakening DTO deserialization;
- persist Allure history through CI artifacts or cache;
- add deterministic Faker seeds when exact failure reproduction becomes necessary;
- replace the shared public service with a controlled local Petstore deployment for fully isolated CI execution.
