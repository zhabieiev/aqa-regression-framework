# regression-petstore-api

## Overview

`regression-petstore-api` is a Java 21 API automation module for the public Swagger Petstore service. It is part of the `regression` multi-module framework and demonstrates API testing with pure JUnit 5, without Gherkin or Cucumber in the module test layer.

The module uses generated OpenAPI DTOs, reusable endpoint services, domain Steps, dynamic test data, parallel execution, automatic cleanup, and Allure reporting.

## Ecosystem and Core Integration

```text
regression
├── regression-core
├── regression-petstore-api
└── other product-specific modules
```

The root POM manages shared dependency and plugin versions. `regression-petstore-api` owns only Petstore-specific code and configuration.

`regression-core` provides:

- property loading;
- the shared request model and builder;
- Jersey request execution;
- Jackson serialization and deserialization;
- response status validation and common API logging.

Petstore reuses this infrastructure but defines its own generated models, Services, Steps, data factories, JUnit extensions, and tests.

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
| Test | Scenario, assertions, tags, severity, and cleanup registration |
| Steps | Domain orchestration and successful-operation logging |
| Service | Endpoint, HTTP method, request body, expected status, and response DTO |
| Base service | Petstore base URL and integration with `regression-core` |
| Core | Shared transport, JSON handling, and response validation |

Assertions remain in JUnit tests. Services contain no test logic, and generated DTOs are never edited manually.

## Structure

```text
regression-petstore-api
├── src/main/java/com/aqa/petstore/api
│   ├── models/generated
│   ├── services
│   └── steps
├── src/main/resources/properties
├── src/test/java/com/aqa/petstore/api
│   ├── data
│   ├── extensions
│   └── tests
├── src/test/resources
│   ├── allure.properties
│   ├── categories.json
│   └── junit-platform.properties
├── pom.xml
└── run-tests.sh
```

## Technology and Approaches

- Java 21 and Maven;
- JUnit 5 and JUnit Platform;
- Jersey and Jackson through `regression-core`;
- OpenAPI Generator for transport models;
- AssertJ recursive comparison;
- DataFaker test-data factories;
- JUnit Extension cleanup;
- parallel execution with resource locking;
- Allure JUnit 5, custom failure categories, and Trend history.

The current OpenAPI specification is:

```text
https://petstore.swagger.io/v2/swagger.json
```

## Test Strategy

Current tests cover representative Pet and Store Order flows:

- create a pet;
- create, retrieve, and delete an order;
- validate `404 Not Found` for an unknown order;
- compare request and response DTOs recursively.

Test DTOs are created through DataFaker factories. `TestRunId` and atomic sequences reduce data collisions without exposing long explicit constructors in test methods.

`CleanupExtension` registers cleanup immediately after resource creation, executes actions in reverse order, attempts all registered actions, and aggregates cleanup failures.

JUnit runs tests concurrently with a fixed pool of four threads. Store Order tests use `ResourceLock` because the public API has a small shared ID range.

Allure metadata organizes tests by Epic, Feature, Story, Severity, and tags. Custom categories separate cleanup, HTTP 5xx, network, timeout, and JSON mapping failures. `run-tests.sh` preserves local Trend history between runs.

## Running the Module

Prerequisites:

- JDK 21;
- Maven;
- Bash or Git Bash;
- internet access to `petstore.swagger.io`.

Recommended execution from the module directory:

```bash
./run-tests.sh
```

The script builds `regression-core` without running its Cucumber tests, runs only Petstore JUnit tests, generates an Allure report even after test failures, saves Trend history, and opens the report locally.

Run one class or one tag:

```bash
./run-tests.sh -Dtest=PetTest
./run-tests.sh -Dgroups=smoke
```

Maven-only execution from the repository root:

```bash
mvn -pl :regression-core -am install -Dmaven.test.skip=true
mvn -pl :regression-petstore-api test
mvn -pl :regression-petstore-api allure:report
```

Regenerate OpenAPI models when the contract changes:

```bash
mvn -pl :regression-petstore-api generate-sources \
    -Dmodels.petstore.api.skip.generate=false
```

## Extension Guidelines

When adding a new resource or scenario:

1. reuse or regenerate the OpenAPI DTO;
2. add HTTP mechanics to a resource-specific Service;
3. add domain orchestration to Steps;
4. create test data through a factory under `src/test/java`;
5. register cleanup immediately after resource creation;
6. keep assertions in the JUnit test;
7. add meaningful Allure metadata and JUnit tags;
8. verify shared state before enabling concurrent execution;
9. use the narrowest possible `ResourceLock` when isolation cannot be achieved through unique data.

Do not introduce Gherkin, Cucumber glue, fixed shared test data, sleeps, manually copied DTOs, or test-order dependencies into this module.

## Current Limitations and Trade-offs

- Swagger Petstore is a shared public sandbox and may return intermittent HTTP 5xx errors.
- The module contains representative coverage, not a complete Petstore regression suite.
- Store Order tests are serialized because usable IDs are restricted and shared.
- `ResourceLock` works only inside one JVM and does not coordinate parallel CI jobs.
- Cleanup cannot run after forced JVM termination or infrastructure shutdown.
- The tested delete flow has no independent fallback cleanup if the delete request fails.
- Generated DTO deserialization does not replace full OpenAPI schema or consumer-contract validation.
- DataFaker values are not deterministically seeded.
- Allure Trend history is local and must be persisted separately in CI.
- Authentication is outside the current module scope.

See [`docs/ROADMAP.md`](../docs/ROADMAP.md) for the reactor-wide roadmap, including a fallback-cleanup item for the delete flow above.
