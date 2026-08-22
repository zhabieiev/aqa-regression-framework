# Worked example: a full execution session

This is a worked example of one real `regression-mcp-server` execution
session: a live MCP client driving `regression_start_test_run` against
`regression-nextjs-commerce`, polling `regression_get_test_run` through to a
terminal state, and retrieving its report via `regression_get_test_summary`,
`regression_get_failure_summary`, and `regression_get_failure_artifacts`. It
was captured on 2026-08-22 against the public target
https://demo.vercel.store. Every request and response line below is copied
byte-for-byte from that real session's raw transcript — this is a verbatim
recording of an actual session, not a mock-up or a hand-written example.

## Launch command

The server was launched as:

```
REGRESSION_ROOT=<REGRESSION_ROOT> REGRESSION_MAVEN_HOME=<MAVEN_HOME> \
  java -jar regression-mcp-server/target/regression-mcp-server.jar
```

`<REGRESSION_ROOT>` is the absolute path to a checkout of this reactor (the
directory containing the root `pom.xml`); `<MAVEN_HOME>` is the absolute path
to a local Apache Maven installation. Both are placeholders — substitute your
own. Everything else in this document, including every tool name, argument,
and response byte below, is exactly what a real session produced.

## The session

**1. `initialize`** — the client identifies itself and negotiates the
protocol version. The server responds with its server info and its own
`instructions` field, which names the three tools that have side effects.

```json
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"phaseB1-session-driver","version":"1.0.0"}}}
```

```json
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-06-18","capabilities":{"logging":{},"tools":{"listChanged":false}},"serverInfo":{"name":"regression-mcp-server","version":"1.0.0"},"instructions":"This is a local framework inspection server for the repository configured by REGRESSION_ROOT. Most tools are deterministic and read-only. Three explicitly authorized execution tools (regression_start_test_run, regression_get_test_run, regression_cancel_test_run) start, observe, and cancel test runs for allow-listed modules; they are the only tools with side effects."}}
```

**2. `notifications/initialized`** — the client confirms it is ready. This is
a notification, not a request, so it carries no response.

```json
{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
```

**3. `tools/list`** — the client asks what tools exist. All 14 tools are
returned, each with its full input/output JSON Schema.

```json
{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
```

**[ELISION 1 of 2]** The real response to this call is a single JSON-RPC line
21,743 bytes long (21,744 bytes including its trailing newline), containing
every tool's complete `inputSchema`/`outputSchema`/`annotations`. It is not
reproduced here. What follows is the 14 tool names only, extracted from that
response, in the order they were returned; the full schemas for every tool
are documented in [`docs/TOOLS.md`](TOOLS.md):

```
regression_get_framework_overview
regression_list_modules
regression_list_features
regression_list_scenarios
regression_start_test_run
regression_get_test_run
regression_cancel_test_run
regression_get_test_summary
regression_get_failure_summary
regression_get_failure_artifacts
regression_read_failure_artifact
regression_validate_module_boundaries
regression_validate_framework_conventions
regression_validate_architecture
```

**4. `regression_start_test_run` — deliberately invalid call.** This call
uses `timeoutSeconds: 10`, below the validator's minimum of 30, on purpose.
It is included in full because it is the most informative pair in this
recording: it shows the exact structured error a caller gets for a bad
argument, with no process ever launched.

```json
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"regression_start_test_run","arguments":{"module":"regression-nextjs-commerce","environment":"dev","headless":true,"timeoutSeconds":10}}}
```

```json
{"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"{\"error\":{\"message\":\"timeoutSeconds must be between 30 and 1800.\",\"code\":\"INVALID_TIMEOUT\"},\"status\":\"error\"}"}],"isError":true,"structuredContent":{"error":{"message":"timeoutSeconds must be between 30 and 1800.","code":"INVALID_TIMEOUT"},"status":"error"}}}
```

**5. `regression_start_test_run` — the real call.** Same module, `headless:
true`, a real 900-second timeout, and no `tags` argument at all. The response
returns a `runId` and the initial `QUEUED` state.

```json
{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"regression_start_test_run","arguments":{"module":"regression-nextjs-commerce","environment":"dev","headless":true,"timeoutSeconds":900}}}
```

```json
{"jsonrpc":"2.0","id":4,"result":{"content":[{"type":"text","text":"{\"data\":{\"environment\":\"dev\",\"runId\":\"run-53bbecaa59492926f34b2dfd0ec1ada8\",\"stderrTruncated\":false,\"createdAt\":\"2026-08-22T09:29:52.308395500Z\",\"state\":\"QUEUED\",\"tags\":\"not @wip\",\"stdoutBytes\":0,\"timeoutSeconds\":900,\"module\":\"regression-nextjs-commerce\",\"stdoutTruncated\":false,\"stderrBytes\":0,\"headless\":true},\"status\":\"ok\"}"}],"isError":false,"structuredContent":{"data":{"environment":"dev","runId":"run-53bbecaa59492926f34b2dfd0ec1ada8","stderrTruncated":false,"createdAt":"2026-08-22T09:29:52.308395500Z","state":"QUEUED","tags":"not @wip","stdoutBytes":0,"timeoutSeconds":900,"module":"regression-nextjs-commerce","stdoutTruncated":false,"stderrBytes":0,"headless":true},"status":"ok"}}}
```

**6. `regression_get_test_run` — poll 1 of 8.** Sent 3 seconds after the
start request, on a 3-second polling interval. The run has already moved to
`RUNNING`.

```json
{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"regression_get_test_run","arguments":{"runId":"run-53bbecaa59492926f34b2dfd0ec1ada8"}}}
```

```json
{"jsonrpc":"2.0","id":5,"result":{"content":[{"type":"text","text":"{\"data\":{\"runId\":\"run-53bbecaa59492926f34b2dfd0ec1ada8\",\"stdoutTruncated\":false,\"tags\":\"not @wip\",\"module\":\"regression-nextjs-commerce\",\"stderrTruncated\":false,\"startedAt\":\"2026-08-22T09:29:52.482044700Z\",\"createdAt\":\"2026-08-22T09:29:52.308395500Z\",\"stdoutBytes\":0,\"state\":\"RUNNING\",\"reason\":\"RUNNING\",\"environment\":\"dev\",\"headless\":true,\"timeoutSeconds\":900,\"stderrBytes\":0},\"status\":\"ok\"}"}],"isError":false,"structuredContent":{"data":{"runId":"run-53bbecaa59492926f34b2dfd0ec1ada8","stdoutTruncated":false,"tags":"not @wip","module":"regression-nextjs-commerce","stderrTruncated":false,"startedAt":"2026-08-22T09:29:52.482044700Z","createdAt":"2026-08-22T09:29:52.308395500Z","stdoutBytes":0,"state":"RUNNING","reason":"RUNNING","environment":"dev","headless":true,"timeoutSeconds":900,"stderrBytes":0},"status":"ok"}}}
```

**[ELISION 2 of 2]** Six further polls followed (polls 2 through 7, JSON-RPC
request/response `id` 6 through 11, one every 3 seconds). Each one's response
differed from poll 1's response shown above only in that same incrementing
`id` field — every other byte, including `state`, `reason`, `startedAt`,
`createdAt`, `stdoutBytes`, and `stderrBytes`, was identical to poll 1's
response, poll after poll. They are not reproduced here individually.

**7. `regression_get_test_run` — poll 8 of 8, terminal.** The run has
finished: `state` is `PASSED`, and `finishedAt`, `exitCode`, `stdoutBytes`,
and `stderrBytes` are now populated.

```json
{"jsonrpc":"2.0","id":12,"method":"tools/call","params":{"name":"regression_get_test_run","arguments":{"runId":"run-53bbecaa59492926f34b2dfd0ec1ada8"}}}
```

```json
{"jsonrpc":"2.0","id":12,"result":{"content":[{"type":"text","text":"{\"data\":{\"exitCode\":0,\"stderrBytes\":518,\"runId\":\"run-53bbecaa59492926f34b2dfd0ec1ada8\",\"finishedAt\":\"2026-08-22T09:30:15.094616100Z\",\"reason\":\"PASSED\",\"stdoutBytes\":4781,\"timeoutSeconds\":900,\"stderrTruncated\":false,\"createdAt\":\"2026-08-22T09:29:52.308395500Z\",\"tags\":\"not @wip\",\"headless\":true,\"stdoutTruncated\":false,\"startedAt\":\"2026-08-22T09:29:52.482044700Z\",\"environment\":\"dev\",\"state\":\"PASSED\",\"module\":\"regression-nextjs-commerce\"},\"status\":\"ok\"}"}],"isError":false,"structuredContent":{"data":{"exitCode":0,"stderrBytes":518,"runId":"run-53bbecaa59492926f34b2dfd0ec1ada8","finishedAt":"2026-08-22T09:30:15.094616100Z","reason":"PASSED","stdoutBytes":4781,"timeoutSeconds":900,"stderrTruncated":false,"createdAt":"2026-08-22T09:29:52.308395500Z","tags":"not @wip","headless":true,"stdoutTruncated":false,"startedAt":"2026-08-22T09:29:52.482044700Z","environment":"dev","state":"PASSED","module":"regression-nextjs-commerce"},"status":"ok"}}}
```

**8. `regression_get_test_summary`** — the published, authoritative Surefire
summary for the now-terminal run.

```json
{"jsonrpc":"2.0","id":13,"method":"tools/call","params":{"name":"regression_get_test_summary","arguments":{"runId":"run-53bbecaa59492926f34b2dfd0ec1ada8"}}}
```

```json
{"jsonrpc":"2.0","id":13,"result":{"content":[{"type":"text","text":"{\"data\":{\"failures\":0,\"tests\":2,\"passed\":2,\"detailsTruncated\":true,\"runId\":\"run-53bbecaa59492926f34b2dfd0ec1ada8\",\"skipped\":0,\"duration\":\"15.266\",\"errors\":0,\"suites\":[{\"duration\":\"15.437\",\"errors\":0,\"skipped\":0,\"id\":\":com.aqa.nextjscommerce.runners.RunCucumberTest\",\"failures\":0,\"tests\":2}]},\"status\":\"ok\"}"}],"isError":false,"structuredContent":{"data":{"failures":0,"tests":2,"passed":2,"detailsTruncated":true,"runId":"run-53bbecaa59492926f34b2dfd0ec1ada8","skipped":0,"duration":"15.266","errors":0,"suites":[{"duration":"15.437","errors":0,"skipped":0,"id":":com.aqa.nextjscommerce.runners.RunCucumberTest","failures":0,"tests":2}]},"status":"ok"}}}
```

**9. `regression_get_failure_summary`** — bounded, authoritative Surefire
failures plus optional Allure enrichment. This run has zero failures, so
`failureRecords` is empty.

```json
{"jsonrpc":"2.0","id":14,"method":"tools/call","params":{"name":"regression_get_failure_summary","arguments":{"runId":"run-53bbecaa59492926f34b2dfd0ec1ada8"}}}
```

```json
{"jsonrpc":"2.0","id":14,"result":{"content":[{"type":"text","text":"{\"data\":{\"allureAvailability\":\"AVAILABLE\",\"tests\":2,\"skipped\":0,\"detailsTruncated\":false,\"errors\":0,\"failures\":0,\"failureRecords\":[],\"runId\":\"run-53bbecaa59492926f34b2dfd0ec1ada8\"},\"status\":\"ok\"}"}],"isError":false,"structuredContent":{"data":{"allureAvailability":"AVAILABLE","tests":2,"skipped":0,"detailsTruncated":false,"errors":0,"failures":0,"failureRecords":[],"runId":"run-53bbecaa59492926f34b2dfd0ec1ada8"},"status":"ok"}}}
```

**10. `regression_get_failure_artifacts`** — the server-published artifact
list for the run. Despite the tool's name, and despite this run having zero
failures, it returns 8 artifacts — see "Known characteristics" below.

```json
{"jsonrpc":"2.0","id":15,"method":"tools/call","params":{"name":"regression_get_failure_artifacts","arguments":{"runId":"run-53bbecaa59492926f34b2dfd0ec1ada8"}}}
```

```json
{"jsonrpc":"2.0","id":15,"result":{"content":[{"type":"text","text":"{\"data\":{\"runId\":\"run-53bbecaa59492926f34b2dfd0ec1ada8\",\"artifacts\":[{\"artifactId\":\"de776d7c7c79de1ae0d31e3d4ee192c5\",\"relativePath\":\"01f97224-e0b0-4303-bd59-0861e3301ebe-container.json\",\"name\":\"01f97224-e0b0-4303-bd59-0861e3301ebe-container.json\",\"mimeType\":\"application/json\",\"size\":413},{\"artifactId\":\"8ca6a9a1fe71fb352a7e4da32eff0da9\",\"relativePath\":\"1cdeb18c-c15f-45e1-aa33-0832b318f85d-attachment.csv\",\"name\":\"1cdeb18c-c15f-45e1-aa33-0832b318f85d-attachment.csv\",\"mimeType\":\"text/csv\",\"size\":48},{\"artifactId\":\"3d6de9d789cb54bd777c767db70e6337\",\"relativePath\":\"305fbda6-f9d7-4be7-9e5b-73c6b35561f2-result.json\",\"name\":\"305fbda6-f9d7-4be7-9e5b-73c6b35561f2-result.json\",\"mimeType\":\"application/json\",\"size\":2164},{\"artifactId\":\"92c55d8c8a2fa94ea5e960ea73ab7e02\",\"relativePath\":\"4cb0e88e-c5ed-48b7-b053-13daae5af208-container.json\",\"name\":\"4cb0e88e-c5ed-48b7-b053-13daae5af208-container.json\",\"mimeType\":\"application/json\",\"size\":154},{\"artifactId\":\"96ab355b1bc631b6d5e081d3ea95bac4\",\"relativePath\":\"8764a292-4731-4e3c-8fc1-33467a103941-container.json\",\"name\":\"8764a292-4731-4e3c-8fc1-33467a103941-container.json\",\"mimeType\":\"application/json\",\"size\":437},{\"artifactId\":\"e5fa482f331bf18e87296caa6e69fce7\",\"relativePath\":\"9fb0688d-a2ea-484f-b5b1-2cf109aeda13-container.json\",\"name\":\"9fb0688d-a2ea-484f-b5b1-2cf109aeda13-container.json\",\"mimeType\":\"application/json\",\"size\":413},{\"artifactId\":\"aa4932c5f05aa026241021ee9a7c39e4\",\"relativePath\":\"b289841c-a6be-43a5-8d58-537622d4378d-container.json\",\"name\":\"b289841c-a6be-43a5-8d58-537622d4378d-container.json\",\"mimeType\":\"application/json\",\"size\":437},{\"artifactId\":\"0bbc2b9b04e1fafe2886b7bb8e7287e4\",\"relativePath\":\"df3bb6be-02a9-4550-9954-2fb5659c1f2c-result.json\",\"name\":\"df3bb6be-02a9-4550-9954-2fb5659c1f2c-result.json\",\"mimeType\":\"application/json\",\"size\":4011}]},\"status\":\"ok\"}"}],"isError":false,"structuredContent":{"data":{"runId":"run-53bbecaa59492926f34b2dfd0ec1ada8","artifacts":[{"artifactId":"de776d7c7c79de1ae0d31e3d4ee192c5","relativePath":"01f97224-e0b0-4303-bd59-0861e3301ebe-container.json","name":"01f97224-e0b0-4303-bd59-0861e3301ebe-container.json","mimeType":"application/json","size":413},{"artifactId":"8ca6a9a1fe71fb352a7e4da32eff0da9","relativePath":"1cdeb18c-c15f-45e1-aa33-0832b318f85d-attachment.csv","name":"1cdeb18c-c15f-45e1-aa33-0832b318f85d-attachment.csv","mimeType":"text/csv","size":48},{"artifactId":"3d6de9d789cb54bd777c767db70e6337","relativePath":"305fbda6-f9d7-4be7-9e5b-73c6b35561f2-result.json","name":"305fbda6-f9d7-4be7-9e5b-73c6b35561f2-result.json","mimeType":"application/json","size":2164},{"artifactId":"92c55d8c8a2fa94ea5e960ea73ab7e02","relativePath":"4cb0e88e-c5ed-48b7-b053-13daae5af208-container.json","name":"4cb0e88e-c5ed-48b7-b053-13daae5af208-container.json","mimeType":"application/json","size":154},{"artifactId":"96ab355b1bc631b6d5e081d3ea95bac4","relativePath":"8764a292-4731-4e3c-8fc1-33467a103941-container.json","name":"8764a292-4731-4e3c-8fc1-33467a103941-container.json","mimeType":"application/json","size":437},{"artifactId":"e5fa482f331bf18e87296caa6e69fce7","relativePath":"9fb0688d-a2ea-484f-b5b1-2cf109aeda13-container.json","name":"9fb0688d-a2ea-484f-b5b1-2cf109aeda13-container.json","mimeType":"application/json","size":413},{"artifactId":"aa4932c5f05aa026241021ee9a7c39e4","relativePath":"b289841c-a6be-43a5-8d58-537622d4378d-container.json","name":"b289841c-a6be-43a5-8d58-537622d4378d-container.json","mimeType":"application/json","size":437},{"artifactId":"0bbc2b9b04e1fafe2886b7bb8e7287e4","relativePath":"df3bb6be-02a9-4550-9954-2fb5659c1f2c-result.json","name":"df3bb6be-02a9-4550-9954-2fb5659c1f2c-result.json","mimeType":"application/json","size":4011}]},"status":"ok"}}}
```

The client then closed stdin. The server process exited with code `0`.

## What this run measured

- **Duration**: 22.6 seconds, computed from the server's own timestamps
  (`finishedAt` − `startedAt`: `2026-08-22T09:30:15.094616100Z` −
  `2026-08-22T09:29:52.482044700Z`).
- **Poll cadence**: every 3 seconds.
- **Polls that returned `RUNNING`**: 7, out of 8 total polls (the 8th was the
  terminal `PASSED` observation). No poll observed `QUEUED` — the very first
  poll, sent 3 seconds after the start request, already found the run
  `RUNNING`.
- **Exit code**: `0`.
- **stderr**: the server wrote nothing to stderr for the entire session —
  not during `initialize`, not during any poll, and not during the run
  itself.

## Known characteristics of this response shape (not new findings)

These were observed directly in the recording above. They are documented
here so a reader recognizes them as known behavior rather than treating them
as something they discovered:

- **`stdoutBytes`/`stderrBytes` are not a live progress signal.** Every
  `RUNNING` poll in this session reported `"stdoutBytes":0,"stderrBytes":0`;
  real totals (`4781`/`518`) appeared only in the terminal snapshot. The
  `RUNNING` snapshot's byte counts are fixed at zero when the state
  transitions, and the periodic background update that keeps a `RUNNING`
  run's persisted record alive does not recompute them from the live
  process output — only the terminal snapshot does. A client cannot use
  these two fields to tell whether a run is making progress.
- **`reason` duplicated `state` at every observation in this session.**
  Every `RUNNING` poll showed `"state":"RUNNING","reason":"RUNNING"`, and the
  terminal poll showed `"state":"PASSED","reason":"PASSED"`. `reason` did not
  carry information beyond repeating the state name in either case observed
  here.
- **`regression_get_failure_artifacts` returned 8 artifacts on a run with
  zero failures.** It lists the server's entire published Allure result set
  for the run, not failure-specific evidence — on a run with nothing to
  fail, that set is still non-empty (Allure container/result files that
  Cucumber writes for every scenario, passing or not).
- **`regression_get_test_summary` reported `"detailsTruncated":true` for a
  run with 0 failures and 0 errors, while `regression_get_failure_summary`
  reported `"detailsTruncated":false` for the identical run.** The two
  fields are computed differently: the summary's flag is `true` whenever any
  suite has parsed per-testcase detail that this particular endpoint's
  output doesn't expose — which is true for essentially every real run,
  regardless of whether anything failed — while the failure-summary's flag
  reflects `summary.detailsTruncated()` directly, which tracks a suite/
  testcase count bound and a failure-detail byte bound, neither of which was
  hit here. See `docs/TECHNICAL_DEBT.md` item 8 for the full detail.
- **The effective tag filter excluded nothing from this run.** Every
  response above shows `"tags":"not @wip"` even though the client sent no
  `tags` argument: the server always appends a `not @wip` filter server-side
  (wrapping a client-supplied expression as `(<expression>) and not @wip`
  when one is given). In this session that filter had no effect —
  `regression-nextjs-commerce` currently implements exactly two Cucumber
  scenarios (one in `catalog_search.feature`, tagged `@ui @catalog @smoke`;
  one in `cart_management.feature`, tagged `@ui @cart @smoke`) and neither is
  tagged `@wip`. This run therefore executed the module's entire
  currently-implemented suite, not a filtered subset of a larger one.

## Reproducing this

Prerequisites: Java 21; a local Apache Maven installation, with its path set
as `REGRESSION_MAVEN_HOME`; `REGRESSION_ROOT` pointed at a checkout of this
reactor; a freshly built `regression-mcp-server.jar` (`mvn -pl
regression-mcp-server -am clean verify`); and outbound network access to
`https://demo.vercel.store`, the commerce module's live target.

The request sequence above is: `initialize`, then
`notifications/initialized`, then `tools/list`, then one `tools/call` for
`regression_start_test_run` (optionally, as shown here, first with an
invalid argument to see the validation error), then repeated `tools/call`
requests for `regression_get_test_run` with the returned `runId` — sent on
whatever interval the client chooses — until `state` is one of `PASSED`,
`FAILED`, `CANCELLED`, `TIMED_OUT`, or `ERROR`, and finally one `tools/call`
each for `regression_get_test_summary`, `regression_get_failure_summary`,
and `regression_get_failure_artifacts` with that same `runId`.

No driver script ships with this repository. The session above was produced
with a throwaway script kept outside the repository entirely, purely to
drive and capture this one recording; it is not part of any build, test
suite, or committed tooling. A reader who wants to reproduce this drives the
server with their own MCP client (an IDE's built-in MCP support, a
general-purpose MCP inspector, or a small script of their own) sending the
same sequence of requests described above.
