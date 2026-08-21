# Tools and schemas

All 14 tools `regression-mcp-server` registers, grounded directly in
`RegressionMcpServer.java` and the three `com.aqa.mcp.validation.*Tool.java`
classes (tool-name constants and schema-building methods are the source of
truth — if this file and the code ever disagree, the code wins).

Every tool's output is one of two shapes (the closed `oneOf` envelope every
tool declares):

```json
{"status": "ok", "data": { ... }}
```
```json
{"status": "error", "error": {"code": "...", "message": "..."}}
```

## Discovery tools (read-only)

### `regression_get_framework_overview`
- Purpose: deterministic overview of the local regression framework.
- Input: `{}` — no arguments accepted (`additionalProperties: false`,
  no properties).
- Output `data`: `name`, `root`, `javaVersion`, `buildTool`, `availability`
  (all required strings).

### `regression_list_modules`
- Purpose: lists the reactor modules declared by the root parent `pom.xml`.
- Input: `{}` — no arguments accepted.
- Output `data.modules[]`: `name`, `relativePath`, `type` (enum:
  `CORE, UI, API, API_UI, MCP, UNKNOWN` — `ModuleType.schemaValues()`),
  `directoryExists`, `pomExists` (all required).

### `regression_list_features`
- Purpose: lists parsed Gherkin features below a declared module's feature
  root.
- Input: required `module` (string). `additionalProperties: false`.
- Output `data`: `module`, `featureRoot`, `featureRootExists`, `features[]`
  (each: `name`, `language`, `tags[]`, `path`, `line`, `scenarioCount`).

### `regression_list_scenarios`
- Purpose: lists executable Cucumber scenarios below a declared module's
  feature root, optionally filtered by a Cucumber tag expression.
- Input: required `module` (string); optional `tags` (string, a Cucumber
  tag expression parsed with the official Tag Expressions library).
  `additionalProperties: false`.
- Output `data`: `module`, `scenarios[]` (each: `feature`, `name`, `type`,
  `tags[]`, `path`, `line`).

## Execution tools

### `regression_start_test_run`
- Purpose: starts an allowed test run for one of the modules registered in
  `ExecutionProfileRegistry` — currently `regression-nextjs-commerce` and
  `regression-jhipster`, both with environment list `["dev"]` and
  `supportsHeadless = true`. The registry is the authority on which
  modules and environments are supported; consult it directly rather than
  hardcoding this list elsewhere, since a third profile may be registered
  later.
- Read-only: no (execution/destructive/non-idempotent/not open-world per
  its `ToolAnnotations`).
- Input, all required except `tags`: `module` (string), `environment`
  (string), `headless` (boolean), `timeoutSeconds` (integer). Optional:
  `tags` (string, `maxLength: 1024`). `additionalProperties: false`.
  `module` and `environment` must match a registered
  `ExecutionProfile`, and `timeoutSeconds` must be between 30 and 1800
  inclusive (enforced by `TestRunRequestValidator`, not by the JSON Schema
  itself).
- Output `data` (run snapshot): `runId`, `module`, `environment`,
  `headless`, `tags`, `timeoutSeconds`, `state`, `createdAt` (all
  required), plus `startedAt`, `finishedAt`, `exitCode`, `reason` when
  applicable, and `stdoutBytes`, `stderrBytes`, `stdoutTruncated`,
  `stderrTruncated` (all required).

### `regression_get_test_run`
- Purpose: returns a server-generated run's current snapshot.
- Read-only: yes, idempotent.
- Input: required `runId` (string) only.
- Output `data`: same run-snapshot shape as `regression_start_test_run`.

### `regression_cancel_test_run`
- Purpose: cancels a server-generated run.
- Read-only: no (destructive/idempotent per its annotations, not
  open-world).
- Input: required `runId` (string) only.
- Output `data`: same run-snapshot shape as `regression_start_test_run`.

## Report and artifact tools

All four require a **terminal**, server-generated run: a missing or foreign
`runId` returns `RUN_NOT_FOUND`, a run that is not yet terminal returns
`RUN_NOT_TERMINAL`, and report/artifact data unavailable on an otherwise-valid
terminal run returns `NOT_FOUND` (see "Common error codes" below for the
precise distinction between the three).

### `regression_get_test_summary`
- Purpose: returns the published, authoritative Surefire summary for a
  terminal run.
- Input: required `runId` (string) only.
- Output `data`: `runId`, `tests`, `passed`, `failures`, `errors`,
  `skipped`, `duration` (string), `suites[]` (each: `id`, `tests`,
  `failures`, `errors`, `skipped`, `duration`), `detailsTruncated`.

### `regression_get_failure_summary`
- Purpose: returns bounded, authoritative Surefire failures plus optional,
  capture-time-only Allure enrichment.
- Input: required `runId` (string) only.
- Output `data`: `runId`, `tests`, `failures`, `errors`, `skipped`,
  `failureRecords[]` (each: `failureId`, `type`, `suite`, `testCase`,
  `message`, `stackTrace`, `allure` object, `recordTruncated`),
  `allureAvailability`, `detailsTruncated`.
- Bounded response size: 96 KiB total serialized response
  (`RegressionMcpServer.MAX_FAILURE_SUMMARY_RESPONSE_BYTES`); exceeding it
  returns a `REPORT_MALFORMED` error instead of a partial response.

### `regression_get_failure_artifacts`
- Purpose: lists the server-published artifacts captured for a terminal run.
- Input: required `runId` (string) only.
- Output `data`: `runId`, `artifacts[]` (each: `artifactId`, `name`,
  `mimeType`, `size`, `relativePath`).

### `regression_read_failure_artifact`
- Purpose: returns bounded, MIME-allow-listed bytes for one
  server-generated `artifactId` belonging to a terminal run.
- Input: required `runId` and `artifactId` (both strings, the only two
  keys allowed).
- Output `data`: the same artifact fields as
  `regression_get_failure_artifacts`, plus `content` (Base64-encoded
  string).
- Bounds: MIME type must be one of `image/png`, `image/jpeg`,
  `text/plain`, `application/json`, `text/xml`
  (`UNSUPPORTED_MIME_TYPE` error otherwise); total serialized response
  capped at 2 MiB (`RegressionMcpServer.MAX_ARTIFACT_READ_RESPONSE_BYTES`,
  `ARTIFACT_TOO_LARGE` error otherwise).

## Architecture-validator tools

All three share the same input shape (optional `module` string filter,
optional `profile` string filter restricted to the enum `CORE, API, UI,
API_UI, MCP, TEST_ONLY` — `RuleProfile.schemaValues()`,
`additionalProperties: false`) and the same top-level output shape
(`data.modules[]`, each: `module`, `profile` (same enum), `rulesApplied[]`,
`violations[]`, `truncated`; each violation: `ruleId`, `module`, `file`,
`line`, `message`). Two of the three add a parallel `advisoryViolations[]`
array (same item shape as `violations[]`) for their one advisory-severity
rule.

### `regression_validate_module_boundaries`
- Purpose: checks declared reactor module source against the fixed
  sibling-module and layering-boundary rules.
- No `advisoryViolations` array — all of its rules are blocking.

### `regression_validate_framework_conventions`
- Purpose: checks Selenium/Playwright locator discipline, no blocking
  waits, constructor injection, and static mutable UI state.
- Adds `advisoryViolations[]`, populated only by the records-for-value-
  objects rule (`FrameworkConventionRules.RECORDS_FOR_VALUE_OBJECTS_RULE_ID`);
  every other rule's findings land in `violations[]`.

### `regression_validate_architecture`
- Purpose: checks definitions-layer discipline, package-dependency
  cycles, no assertions in pages/components, and an advisory thin-`BasePage`
  check.
- Adds `advisoryViolations[]`, populated only by the thin-`BasePage` rule
  (`ArchitectureRules.THIN_BASE_PAGE_RULE_ID`); every other rule's findings
  land in `violations[]`.

## Common error codes

Not an exhaustive list of every error code in the server, but the ones most
relevant to normal client use: `INVALID_ARGUMENTS` (schema-level input
rejection), `INVALID_TIMEOUT` (timeout outside 30-1800), `INVALID_TAG_EXPRESSION`
(malformed Cucumber tag expression), `UNSUPPORTED_MODULE`
(`regression_start_test_run`'s `module` does not match a profile
registered in `ExecutionProfileRegistry` — currently
`regression-nextjs-commerce` or `regression-jhipster`), `UNSUPPORTED_CAPABILITY`
(`regression_start_test_run`'s `environment` or `headless` value is not
supported by the module's execution profile), `RUN_NOT_FOUND` (a `runId`
does not match any server-generated run — returned by
`regression_get_test_run`, `regression_cancel_test_run`, and the four
report/artifact tools), `RUN_NOT_TERMINAL` (report/artifact tool called on
a still-active run), `NOT_FOUND` (report data or an artifact is
unavailable for an otherwise valid, terminal run — for example a run that
predates report capture, an unavailable Surefire report, or an unknown
`artifactId`), `REPORT_MALFORMED` / `REPORT_INDEX_CORRUPT` (published
report data failed its own bounded/structural check), `ARTIFACT_TOO_LARGE`
(artifact read response exceeds its 2 MiB cap), `UNSUPPORTED_MIME_TYPE`
(artifact's MIME type is not on the allow-list), `FEATURE_FILE_TOO_LARGE` /
`SOURCE_FILE_TOO_LARGE` (a scanned file exceeds its 1 MiB cap),
`REPOSITORY_ERROR` (a discovery tool's underlying `pom.xml`/module
resolution failed for the current request only, not at server startup).
