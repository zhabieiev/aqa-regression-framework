# regression-mcp-server

`regression-mcp-server` is an isolated Java 21 MCP Java SDK 2.0.0 STDIO
server that exposes deterministic, closed-schema inspection and execution
tools for the `regression` reactor. It is architecturally isolated from
`regression-core` and every product module (see the root `CLAUDE.md` for the
rules that keep it that way).

This file covers installation, client configuration, the security model,
the execution lifecycle, the run store, artifact limits, JAR-lock
troubleshooting, and known v1.0 limitations. For the full list of tools and
their input/output schemas, see [`docs/TOOLS.md`](docs/TOOLS.md).

## Installation

Prerequisites: Java 21 and Maven, and a checked-out copy of this reactor
(the directory containing the root `pom.xml`).

Build the server from the reactor root:

```bash
mvn -pl regression-mcp-server -am clean verify
```

Run it, pointing it at the reactor you want it to inspect:

```bash
REGRESSION_ROOT=/path/to/regression REGRESSION_MAVEN_HOME=/path/to/apache-maven \
  java -jar regression-mcp-server/target/regression-mcp-server.jar
```

`REGRESSION_ROOT` is required. It must resolve (via `Path.toRealPath()`) to
an existing directory that directly contains the reactor's root `pom.xml`;
the server refuses to start otherwise, with a distinct error for each of
"does not exist," "cannot be resolved," "is not a directory," and "does not
contain the root pom.xml" (`RepositoryRootResolver`). `REGRESSION_MAVEN_HOME`
is only needed by the three execution tools to launch Maven
directly; the read-only inspection tools do not need it.

To confirm the server started correctly: standard output is reserved
exclusively for MCP JSON-RPC traffic — you should see no plain-text banner
or log lines there. All startup diagnostics (including a resolution
failure for `REGRESSION_ROOT`) are written to standard error instead. A
successful start produces no stdout output until the connected client
sends its first request.

## MCP client configuration

MCP client configuration is a machine-local file that is not checked into
this repository (see `.gitignore`'s `/.ai/mcp/mcp.json` and
`/.codex/config.toml` entries) — you create it yourself, pointed at your own
local build of the jar. Most MCP clients configure a server using one of two
generic shapes: a JSON `mcpServers` object, or a TOML `mcp_servers.<name>`
table. The worked examples below illustrate each shape; they are not a
description of any file that already exists in this repository.

**JSON `mcpServers` shape** (used by IDEA, Claude Code, and most other MCP
clients, e.g. a project-local `.ai/mcp/mcp.json`):

```json
{
  "mcpServers": {
    "regression-framework": {
      "command": "/path/to/your/java-21/bin/java",
      "args": [
        "-jar",
        "/path/to/regression/regression-mcp-server/target/regression-mcp-server.jar"
      ],
      "env": {
        "REGRESSION_ROOT": "/path/to/regression"
      }
    }
  }
}
```

**TOML `mcp_servers.<name>` shape** (used by Codex CLI and other clients
that read a TOML config, e.g. your own `.codex/config.toml`):

```toml
[mcp_servers.regression-framework]
command = "/path/to/your/java-21/bin/java"
args = ["-jar", "/path/to/regression/regression-mcp-server/target/regression-mcp-server.jar"]

[mcp_servers.regression-framework.env]
REGRESSION_ROOT = "/path/to/regression"
```

Substitute your own Java 21 executable path, your own checkout's absolute
path to the built jar, and your own reactor root. Add `REGRESSION_MAVEN_HOME`
under the same `env` table if you intend to use the execution tools.

## Security model

- **Scope**: the server's only configurable boundary is `REGRESSION_ROOT`.
  There is no tool input that accepts an arbitrary path. The configured
  root is normalized with `Path.toRealPath()` and must directly contain the
  reactor's root `pom.xml`, or the server refuses to start
  (`RepositoryRootResolver.resolve`).
- **Transport**: standard output is reserved exclusively for MCP JSON-RPC.
  All diagnostics go to standard error. No tool writes anything else to
  stdout.
- **No shell, no arbitrary command**: `regression_start_test_run` is the
  only tool that launches a process, and it launches Maven only through a
  direct, trusted Java/Classworlds invocation — never `mvn.cmd`, never a
  shell, and never with client-supplied command, path, or argument input.
  (Maven's own Surefire step may itself spawn a downstream Windows
  `cmd.exe`; that is Maven/Surefire's behavior, not something the server
  constructs.) Its two siblings in the execution-tool group,
  `regression_get_test_run` and `regression_cancel_test_run`, never launch
  a process themselves: `get` is a pure in-memory/on-disk state read
  (`TestRunCoordinator.get()`), and `cancel` only terminates a process the
  server already tracks, through the same identity-safe cleanup path
  described below.
- **Closed-world tool schemas**: every tool declares a closed input schema
  (`additionalProperties: false`) and a closed, structured `status`
  envelope output (`{"status":"ok","data":{...}}` or
  `{"status":"error","error":{"code":...,"message":...}}`). No tool accepts
  free-form or unvalidated input.
- **Process-ownership discipline**: the execution coordinator records every
  observed owned process as a PID plus start instant, parent PID (when
  known), and observation depth. It never terminates a process by PID
  alone, by process name, by shell, by `taskkill`, or by WMI. Cancellation,
  timeout, EOF, JVM shutdown, and stale-run recovery all share the same
  bounded, deepest-first, identity-safe cleanup path. An unprovable,
  possibly-live stale identity blocks a new execution until recovery is
  resolved, rather than risking a false termination.
- **Bounded, allow-listed artifact access**: `regression_read_failure_artifact`
  only serves bytes for a server-generated `artifactId` whose MIME type is
  on an explicit allow-list — `image/png`, `image/jpeg`, `text/plain`,
  `application/json`, `text/xml` (`RunStore.ALLOWED_ARTIFACT_MIME_TYPES`) —
  and every report/artifact response is bounded in size (see "Artifact
  limits" below). Anything outside the allow-list or the size bound is
  rejected with a structured error, never silently truncated or served
  partially without a `truncated`/error signal.
- **CI security gate**: the `regression-mcp-server-security` GitHub Actions
  job runs on both `ubuntu-latest` and `windows-latest`. On the Ubuntu leg
  specifically, a dedicated step fails the build if any MCP Surefire test
  report shows a skip — this is what forces the module's Windows-specific
  skips (symlink-permission tests, see below) to actually execute on Linux
  instead of silently passing everywhere.

## Execution lifecycle

A run moves through a fixed set of states:

```
QUEUED -> RUNNING -> PASSED | FAILED | TIMED_OUT | ERROR
   \--------------------> CANCELLED (client-requested, from QUEUED or RUNNING)
```

Only one run may be active at a time; `regression_start_test_run` rejects a
new request while a run is still `QUEUED` or `RUNNING`. Execution v1 is
intentionally narrow: `module` must match a profile registered in
`ExecutionProfileRegistry` (currently `regression-nextjs-commerce` or
`regression-jhipster`), `environment` must be one of that profile's declared
environments (currently `dev` for both), `headless` is a required boolean,
and `timeoutSeconds` must be between `30` and `1800` inclusive
(`TestRunRequestValidator.MIN_TIMEOUT_SECONDS` /
`MAX_TIMEOUT_SECONDS`). An optional `tags` Cucumber tag expression (at most
1024 characters) is accepted; the server always ANDs in `not @wip`, so a
client-supplied `@cart` becomes `(@cart) and not @wip`.

A run snapshot's `skippedTests` field is present only once a Surefire report
has been parsed for that run; an absent key means the count is unavailable
(no report was ever captured, or none has been parsed yet), while a present
`0` means a report was parsed and no tests were skipped. This closes a gap
`../docs/TECHNICAL_DEBT.md` used to track (in a since-removed item): a `regression_start_test_run`
call with a `tags` expression that matches nothing still terminates as
`PASSED` (Cucumber ran zero scenarios, so the Maven process still exits 0),
but a client no longer has to make a second tool call to notice — a `PASSED`
run whose `skippedTests` is unexpectedly high, in the terminal
`regression_get_test_run` snapshot itself, is the signal that something
matched nothing.

Stale-run recovery: if the server cannot prove a previously-observed owned
process identity is no longer live (for example after an unclean shutdown),
it treats that identity as possibly still running and blocks any new
`regression_start_test_run` call until the same bounded, identity-safe
recovery path used for cancellation/timeout resolves it — this is a
deliberate refusal to guess, not a bug if you see a `start` call rejected
after a server restart.

## Worked example: a full execution session

The section above describes the execution lifecycle in the abstract; this is
what it looks like in practice. A real, verbatim MCP client session was
captured on 2026-08-22 against `regression-nextjs-commerce`, from
`initialize` through a passing terminal run and its report. The full
recording lives in [`docs/SESSION_DEMO.md`](docs/SESSION_DEMO.md), including
a list of known response-shape characteristics observed in it (for example,
`stdoutBytes`/`stderrBytes` staying at zero until the run is terminal).

Below is an abridged excerpt — **not the full session**; see the linked
document for the complete, unedited recording:

```json
{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"regression_start_test_run","arguments":{"module":"regression-nextjs-commerce","environment":"dev","headless":true,"timeoutSeconds":900}}}
```
```json
{"jsonrpc":"2.0","id":4,"result":{"content":[{"type":"text","text":"{\"data\":{\"environment\":\"dev\",\"runId\":\"run-53bbecaa59492926f34b2dfd0ec1ada8\",\"stderrTruncated\":false,\"createdAt\":\"2026-08-22T09:29:52.308395500Z\",\"state\":\"QUEUED\",\"tags\":\"not @wip\",\"stdoutBytes\":0,\"timeoutSeconds\":900,\"module\":\"regression-nextjs-commerce\",\"stdoutTruncated\":false,\"stderrBytes\":0,\"headless\":true},\"status\":\"ok\"}"}],"isError":false,"structuredContent":{"data":{"environment":"dev","runId":"run-53bbecaa59492926f34b2dfd0ec1ada8","stderrTruncated":false,"createdAt":"2026-08-22T09:29:52.308395500Z","state":"QUEUED","tags":"not @wip","stdoutBytes":0,"timeoutSeconds":900,"module":"regression-nextjs-commerce","stdoutTruncated":false,"stderrBytes":0,"headless":true},"status":"ok"}}}
```
```json
{"jsonrpc":"2.0","id":5,"result":{"content":[{"type":"text","text":"{\"data\":{\"runId\":\"run-53bbecaa59492926f34b2dfd0ec1ada8\",\"stdoutTruncated\":false,\"tags\":\"not @wip\",\"module\":\"regression-nextjs-commerce\",\"stderrTruncated\":false,\"startedAt\":\"2026-08-22T09:29:52.482044700Z\",\"createdAt\":\"2026-08-22T09:29:52.308395500Z\",\"stdoutBytes\":0,\"state\":\"RUNNING\",\"reason\":\"RUNNING\",\"environment\":\"dev\",\"headless\":true,\"timeoutSeconds\":900,\"stderrBytes\":0},\"status\":\"ok\"}"}],"isError":false,"structuredContent":{"data":{"runId":"run-53bbecaa59492926f34b2dfd0ec1ada8","stdoutTruncated":false,"tags":"not @wip","module":"regression-nextjs-commerce","stderrTruncated":false,"startedAt":"2026-08-22T09:29:52.482044700Z","createdAt":"2026-08-22T09:29:52.308395500Z","stdoutBytes":0,"state":"RUNNING","reason":"RUNNING","environment":"dev","headless":true,"timeoutSeconds":900,"stderrBytes":0},"status":"ok"}}}
```
```json
{"jsonrpc":"2.0","id":12,"result":{"content":[{"type":"text","text":"{\"data\":{\"exitCode\":0,\"stderrBytes\":518,\"runId\":\"run-53bbecaa59492926f34b2dfd0ec1ada8\",\"finishedAt\":\"2026-08-22T09:30:15.094616100Z\",\"reason\":\"PASSED\",\"stdoutBytes\":4781,\"timeoutSeconds\":900,\"stderrTruncated\":false,\"createdAt\":\"2026-08-22T09:29:52.308395500Z\",\"tags\":\"not @wip\",\"headless\":true,\"stdoutTruncated\":false,\"startedAt\":\"2026-08-22T09:29:52.482044700Z\",\"environment\":\"dev\",\"state\":\"PASSED\",\"module\":\"regression-nextjs-commerce\"},\"status\":\"ok\"}"}],"isError":false,"structuredContent":{"data":{"exitCode":0,"stderrBytes":518,"runId":"run-53bbecaa59492926f34b2dfd0ec1ada8","finishedAt":"2026-08-22T09:30:15.094616100Z","reason":"PASSED","stdoutBytes":4781,"timeoutSeconds":900,"stderrTruncated":false,"createdAt":"2026-08-22T09:29:52.308395500Z","tags":"not @wip","headless":true,"stdoutTruncated":false,"startedAt":"2026-08-22T09:29:52.482044700Z","environment":"dev","state":"PASSED","module":"regression-nextjs-commerce"},"status":"ok"}}}
```
```json
{"jsonrpc":"2.0","id":13,"result":{"content":[{"type":"text","text":"{\"data\":{\"failures\":0,\"tests\":2,\"passed\":2,\"detailsTruncated\":true,\"runId\":\"run-53bbecaa59492926f34b2dfd0ec1ada8\",\"skipped\":0,\"duration\":\"15.266\",\"errors\":0,\"suites\":[{\"duration\":\"15.437\",\"errors\":0,\"skipped\":0,\"id\":\":com.aqa.nextjscommerce.runners.RunCucumberTest\",\"failures\":0,\"tests\":2}]},\"status\":\"ok\"}"}],"isError":false,"structuredContent":{"data":{"failures":0,"tests":2,"passed":2,"detailsTruncated":true,"runId":"run-53bbecaa59492926f34b2dfd0ec1ada8","skipped":0,"duration":"15.266","errors":0,"suites":[{"duration":"15.437","errors":0,"skipped":0,"id":":com.aqa.nextjscommerce.runners.RunCucumberTest","failures":0,"tests":2}]},"status":"ok"}}}
```

(Abridged: the `initialize`/`tools/list` handshake, the deliberately invalid
`start` call, six further identical-shaped `RUNNING` polls, and the
`regression_get_failure_summary`/`regression_get_failure_artifacts` calls
are all omitted above — see `docs/SESSION_DEMO.md` for all of them, in full,
plus the list of known response-shape characteristics this recording
surfaces.)

## Run store

Execution runs are stored under `.regression-mcp/runs/<runId>/` inside the
configured `REGRESSION_ROOT`. This directory is entirely server-generated
and is never a client-addressable path in any tool input — clients only
ever see it indirectly through server-issued `runId`/`artifactId` values.
It is gitignored (`/.regression-mcp/` in the root `.gitignore`).

Per run: an immutable `run.json`, an atomically-replaced `status.json`
(each update writes to a temporary file and swaps it in), and separate
`stdout.log`/`stderr.log` files. Each of the two log files is capped at
16 MiB (`BoundedLogDrainer.FILE_LIMIT`); once a stream exceeds that, further
bytes are dropped from the persisted file (and the run's `stdoutTruncated`/
`stderrTruncated` flags are set), but the server still retains a final
64 KiB in-memory tail per stream (`BoundedLogDrainer.TAIL_LIMIT`) so recent
output around a failure remains inspectable even after the file cap is hit.

## Artifact limits

These are the exact, source-verified bounds enforced today (all values
confirmed directly against `regression-mcp-server`'s source, not estimated):

| Bound | Value | Enforced by | What happens when exceeded |
| --- | --- | --- | --- |
| `regression_get_failure_summary` response size | 96 KiB (`96 * 1024` bytes) | `RegressionMcpServer.MAX_FAILURE_SUMMARY_RESPONSE_BYTES` | `REPORT_MALFORMED` error |
| `regression_read_failure_artifact` response size | 2 MiB (`2 * 1024 * 1024` bytes) | `RegressionMcpServer.MAX_ARTIFACT_READ_RESPONSE_BYTES` | `ARTIFACT_TOO_LARGE` error |
| Artifact MIME type | must be one of `image/png`, `image/jpeg`, `text/plain`, `application/json`, `text/xml` | `RunStore.ALLOWED_ARTIFACT_MIME_TYPES` | `UNSUPPORTED_MIME_TYPE` error |
| Captured file size (per file, during a run) | 8 MiB (`8L * 1024 * 1024` bytes) | `ReportCapture.MAX_FILE_BYTES` | capture fails with an `IOException` ("Capture size limit exceeded"), surfaced as a terminal error for that capture |
| Captured artifacts total (per run) | 64 MiB (`64L * 1024 * 1024` bytes) | `ReportCapture.MAX_TOTAL_BYTES` | same as above, once the running total crosses this bound |
| Per-failure message/stack-trace detail | 28 KiB (`28 * 1024` bytes) | `SurefireSummaryParser.MAX_FAILURE_DETAIL_BYTES` | detail is truncated; the record's `recordTruncated`/summary's `detailsTruncated` flags are set |
| stdout/stderr log file (per run, per stream) | 16 MiB (`16L * 1024 * 1024` bytes) | `BoundedLogDrainer.FILE_LIMIT` | further bytes are dropped from the file; `stdoutTruncated`/`stderrTruncated` flags are set; a 64 KiB in-memory tail is retained regardless (`BoundedLogDrainer.TAIL_LIMIT`) |
| Scanned feature file size (discovery tools) | 1 MiB (`1_048_576` bytes) | `FeatureDiscovery.MAX_FEATURE_FILE_BYTES` | `FEATURE_FILE_TOO_LARGE` error |
| Scanned Java source file size (architecture-validator tools) | 1 MiB (`1_048_576` bytes) | `JavaSourceScanner.MAX_JAVA_FILE_BYTES` | `SOURCE_FILE_TOO_LARGE` error |
| `regression_start_test_run` timeout | 30-1800 seconds inclusive | `TestRunRequestValidator.MIN_TIMEOUT_SECONDS` / `MAX_TIMEOUT_SECONDS` | `INVALID_TIMEOUT` error |
| `tags` Cucumber expression length | 1024 characters | input schema `maxLength` on `start`'s `tags` field | request rejected by schema validation before reaching the tool |

## JAR-lock troubleshooting

If Windows locks the shaded JAR, stop the configured MCP server, confirm
its Java process has exited, then rebuild and restart it.

Why this happens: on Windows, a running process that has an executable JAR
open (as this server does for the whole time it's running) holds a file
lock that prevents another process — including Maven's own `jar`/`shade`
build step — from overwriting or deleting that same file. If you rebuild
(`mvn -pl regression-mcp-server -am clean verify`) while an MCP client
still has the previous build of `regression-mcp-server.jar` running, the
build's packaging step can fail because Windows won't let it replace the
locked file. This is standard Windows file-locking behavior, not specific
to this project, and it does not occur on Linux (the CI security job runs
this same build cleanly on both `ubuntu-latest` and `windows-latest`
precisely because Linux does not hold this kind of lock).

Recovery: stop the MCP client's connection to the server (or otherwise
terminate it), confirm the `java` process running
`regression-mcp-server.jar` has actually exited (e.g. via Task Manager or
`Get-Process java` in PowerShell), then rebuild and restart it. Rebuilding
while the old process is still holding the file open will simply repeat
the failure.

## v1.0 limitations

- Execution v1 supports `regression-nextjs-commerce` and
  `regression-jhipster`, only the `dev` environment, a required Boolean
  `headless`, a 30-1800 second timeout, and an optional 1024-character
  Cucumber tag expression. No other module, environment, or execution
  shape is supported yet.
- Only one run may be active at a time — there is no queueing beyond the
  single active slot.
- The architecture validator's `ARCH-001` rule (definitions-layer
  discipline) uses single-hop, field-declared-type-only detection with no
  Symbol Solver; it has a known, accepted gap for two-hop calls such as
  `regression-core`'s `S3Definitions.s3Steps.s3ServiceActions()
  .getObject(...)` (see `../docs/TECHNICAL_DEBT.md`).
- `regression-nextjs-commerce` previously had a real package-dependency
  cycle between `com.aqa.nextjscommerce.config` and
  `com.aqa.nextjscommerce.driver`; it was eliminated by moving
  `BrowserType` into `config`. The architecture validator's `ARCH-002`
  rule's real-reactor test now asserts that the whole reactor has zero
  package-dependency cycles, with no per-module exception.
- The three validator tools (`ModuleBoundariesTool`,
  `FrameworkConventionsTool`, `ArchitectureTool`) share roughly 120-140
  duplicated lines of schema/envelope/evaluation-loop code each. This is
  known internal debt (see `../docs/TECHNICAL_DEBT.md`), does not affect
  any tool's external behavior or schema, and is listed here only for
  transparency.
- External UI/API smoke tests (Playwright in `regression-jhipster`,
  Selenium in `regression-nextjs-commerce`, live API calls in
  `regression-petstore-api`) remain manual-only for
  `regression-petstore-api`. `regression-nextjs-commerce` and
  `regression-jhipster` are MCP-executable via
  `regression_start_test_run` as of 2026-08-20; no CI job
  triggers any of them automatically, and `regression-petstore-api`
  should not be added to MCP execution without separate, explicit
  authorization. `regression-jhipster` execution against its live
  externally-owned application at `localhost:8080` requires that
  application to already be running and reachable; the server performs
  no automatic health check or startup of it (see the health-check
  design decision recorded for this profile).
