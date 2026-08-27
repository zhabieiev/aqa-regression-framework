# Test map

Every test file under `regression-mcp-server/src/test/java`, what it pins,
which production class it guards, and — the load-bearing column — what
change to that class would pass the whole suite unnoticed. 48 files: 42
`@Test`-bearing classes and 6 test-classpath-only fixtures/mains used by
other tests (not themselves tests).

**Type legend**: UNIT (pure, no filesystem/process), FS (TempDir-based,
filesystem I/O, no process), PROC (spawns/controls a real child process),
STDIO (spawns a full server process, talks JSON-RPC over its stdio),
FIXTURE (not a test; a helper/main other tests depend on).
**Pins legend**: CONTRACT (schema/shape/error-code a client depends on)
vs DETAIL (internal implementation shape, not itself part of the public
contract).

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the production classes named
below and [`docs/TECHNICAL_DEBT.md`](../../docs/TECHNICAL_DEBT.md) for the
debt items this map's gaps feed into.

## Root package (com.aqa.mcp) — 9 files

| File | Type | Pins | Guards | What would pass unnoticed |
|---|---|---|---|---|
| `ReadOnlyProductionBoundaryTest` | UNIT (string scan) | CONTRACT — the isolation invariant itself | every root/execution/validation source file | A new `ProcessBuilder`/`Files.write`/network call anywhere *inside* an existing allowed file would not be caught — the scan is per-file existence of the string, not call-site analysis |
| `RepositoryRootResolverTest` | FS | CONTRACT — the four distinct `REGRESSION_ROOT` error messages | `RepositoryRootResolver` | A resolution success on an input that should have failed one of the four checks, if the wrong check happened to also reject it with a similar message |
| `ModuleListTest` | FS | CONTRACT (shape/ordering/security) + DETAIL (exact error text) | `ModuleList`, `ModuleDescriptor`, `ModuleTypeClassifier` | A change to `ModuleTypeClassifier`'s switch that classified an existing module name differently than its neighbors expect (no exhaustive cross-check against the real 5-module list in this file alone) |
| `FeatureDiscoveryTest` | FS | CONTRACT — discovery shape, tag-expression evaluation, all bounds | `FeatureDiscovery` | The largest, broadest file in this package (18 tests); little would pass unnoticed here |
| `ExecutionPlanningFactoryTest` | FS | CONTRACT — `UNSUPPORTED_MODULE` fail-closed behavior | `ExecutionPlanningFactory` | A change to which modules are treated as executable that happened to still reject the two cases this file checks |
| `RegressionMcpServerContractTest` | FS + in-process tool invocation | CONTRACT — 10 of 11 `RegressionMcpServer`-native tool factories' exact schemas/annotations | `RegressionMcpServer` | **`startTestRunTool` is never invoked by name in this file at all** — its schema and annotations are not asserted here (see the STDIO row below, which covers it instead) |
| `RegressionMcpServerStdioIntegrationTest` | STDIO | CONTRACT — real JSON-RPC wire behavior for all 14 tools, including every execution tool's 4 annotation booleans | `RegressionMcpServer` end-to-end via a real spawned process | This is the **only** file that asserts `regression_start_test_run`'s `openWorldHint(true)` — confirmed via `assertExecutionToolContracts`; a regression there would be caught only here. `docs/TECHNICAL_DEBT.md` item A3 records that `docs/TOOLS.md` documents the opposite value |
| `ControlledMcpServerMain` | FIXTURE | n/a | wires a waiting coordinator into a real server main | used by the STDIO test above |
| `FailingWithArtifactsMcpServerMain` | FIXTURE | n/a | wires a failing-with-artifacts coordinator into a real server main | used by the STDIO test and the contract test above |

## execution package — 19 files

| File | Type | Pins | Guards | What would pass unnoticed |
|---|---|---|---|---|
| `TestRunStateTest` | UNIT | CONTRACT — which states are terminal | `TestRunState` | Nothing meaningful — 3 lines, fully exhaustive |
| `CloseAwareInputStreamTest` | UNIT | DETAIL | `CloseAwareInputStream` | — |
| `TestRunRequestValidatorTest` | UNIT | CONTRACT — validation order, every error code, the `not @wip` invariant, `ExecutionProfileRegistry`'s exact contents | `TestRunRequestValidator`, `ExecutionProfileRegistry`, `ValidatedTestRunRequest` | The most thorough file in the package (12 tests); little passes unnoticed |
| `MavenInvocationFactoryTest` | UNIT/FS | CONTRACT — the exact, ordered Classworlds argument list | `MavenInvocationFactory` | **Neither test varies `environment`'s value** — both hardcode `"dev"`. A regression that let an unvalidated `environment` string reach the command line unescaped would not be caught here (`docs/TECHNICAL_DEBT.md` item D13) |
| `ProcessOwnershipTrackerTest` | UNIT (fake `ProcessView`) | CONTRACT — deepest-first cleanup order, reused-PID rejection, graceful-then-forced fallback | `ProcessOwnershipTracker` | — |
| `ControlledProcessFixture` | FIXTURE | n/a | executable child-process program with PASS/FAIL/WAIT/SPAWN_CHILD/LARGE_OUTPUT/FAIL_WITH_ARTIFACTS modes | used by nearly every PROC test below |
| `ControlledProcessFixtureTest` | PROC | DETAIL — the fixture itself | `ControlledProcessFixture` (a test fixture, not production code) | — |
| `ControlledProcessLauncher` | FIXTURE | n/a | a `MavenProcessLauncher` launching the fixture above, with launch-hold/force-destroy hooks | used by `TestRunCoordinatorTest`, `BoundedLogDrainerLimitTest`, `CrossJvmLockTest` |
| `ControlledCoordinatorFactory` | FIXTURE | n/a | builds a real `TestRunCoordinator` wired to the controlled launcher | used by the STDIO/contract tests for real execution coverage without real Maven/browser |
| `RunStoreLockHolderFixture` | FIXTURE | n/a | separate-JVM main holding `RunStore`'s active-run lock | used by `CrossJvmLockTest` |
| `CrossJvmLockTest` | PROC | CONTRACT — the active-run lock is a genuine cross-process OS file lock | `RunStore.acquireActiveLock` | The only test proving cross-JVM lock behavior with a second real JVM |
| `BoundedLogDrainerLimitTest` | PROC | CONTRACT — 16 MiB cap, 64 KiB tail, dropped-byte accounting under concurrent 17 MiB writes | `BoundedLogDrainer` | — |
| *(no file)* | — | — | `MavenRuntimeConfigurationLoader` | **No test directly exercises `MavenRuntimeConfigurationLoader.load`'s own environment-variable/`java.home` validation logic.** It is only ever exercised indirectly through fixtures (`MavenInvocationFactoryTest.runtime()`, `ControlledCoordinatorFactory`'s `runtime()`) that always supply a valid environment — a regression in its own failure paths would pass the whole suite unnoticed (`docs/TECHNICAL_DEBT.md` item B9) |
| `FailureArtifactStoreTest` | FS | CONTRACT — artifact listing/reading, MIME allow-list, the tamper/traversal defense-in-depth check | `RunStore.artifacts`/`readArtifact` | — |
| `ReportCaptureTest` | FS | CONTRACT — every capture-status transition, all bounds, XXE/symlink rejection, atomic-move-failure fail-closed, Allure-Surefire ambiguity handling. Contains one genuine frozen-literal fixture (`executionRecordsRemainReadableAndAreNotUpgradedWhenTheirStatusChanges`) | `ReportCapture`, `SurefireSummaryParser`, `AllureResultParser` | — |
| `RunStoreTest` | FS + concurrency | CONTRACT — `run.json` immutability, atomic replace with no temp-file leakage, corrupted-state handling, symlink rejection, 1000-iteration concurrent race safety | `RunStore` | — |
| `StaleRunRecoveryTest` | UNIT (fake `ProcessView`) | CONTRACT — every recovery branch | `TestRunCoordinator.recoverIfUnowned` | **None of its 8 tests reference `skippedTests`** — the recovery path's skipped-count carry-through has no value assertion (a narrower, already-known gap, distinct from the two path gaps below) |
| `SurefireSummaryParserTest` | UNIT | CONTRACT — aggregation, dedup-vs-contradiction rejection, sanitization, bounded truncation, XXE rejection | `SurefireSummaryParser` | — |
| `SurefireSummaryStoreTest` | FS | CONTRACT — published-index-only reads, digest/schema/wrong-run rejection. **Its `legacy`/`old` fixture is not a frozen literal** — built by live Jackson serialization of a current-shape `RunSnapshot`, not a hand-written historical shape | `RunStore.summary`/`failureSummary` | Does not corroborate backward-compatible deserialization of a genuinely historical `RunSnapshot` shape, despite appearing to; the assertion it does make (`NOT_FOUND`) is unaffected either way |
| `TestRunCoordinatorTest` | PROC + concurrency | CONTRACT — terminal-path transitions, ownership/observer safety, timeout-boundary races, lock lifecycle, log-cap persistence | `TestRunCoordinator` | **Two of its four terminal paths in `execute()` have zero coverage** (`docs/TECHNICAL_DEBT.md` item B8): the early-cause return (cancellation completing before the worker thread even begins `execute()` — no fixture can force this race deterministically) and the `InterruptedException` catch (no test interrupts the worker thread mid-`waitFor()`). A regression in either would pass the whole suite unnoticed. |

## validation package — 20 files

| File | Type | Pins | Guards | What would pass unnoticed |
|---|---|---|---|---|
| `BasePackagesTest` | UNIT | CONTRACT — longest-common-prefix derivation | `BasePackages` | — |
| `EvaluationContextTest` | UNIT | DETAIL — record invariants | `EvaluationContext` | — |
| `ModuleProfileTest` | UNIT | DETAIL | `ModuleProfile` | — |
| `ModuleValidationResultTest` | UNIT | DETAIL — proves the record can hold `true`/`false`, not that production code ever produces `true` | `ModuleValidationResult` | **Would not catch `truncated`'s hardcoded-`false` finding** (`docs/TECHNICAL_DEBT.md` item D12) — this test only exercises the constructor directly |
| `RuleProfileResolverTest` | UNIT | CONTRACT — every `ModuleType`->`RuleProfile` mapping, the `UNKNOWN_MODULE_TYPE` fail path | `RuleProfileResolver` | — |
| `SourceUnitTest` | UNIT | DETAIL | `SourceUnit` | — |
| `ValidatedValidationScopeTest` | UNIT | DETAIL | `ValidatedValidationScope` | — |
| `ValidationReportTest` | UNIT | DETAIL | `ValidationReport` | — |
| `ValidationScopeValidatorTest` | UNIT | CONTRACT — filter resolution order and every error code | `ValidationScopeValidator` | — |
| `ViolationTest` | UNIT | DETAIL | `Violation` | — |
| `ModuleBoundaryRulesTest` | UNIT (Tier 1, no filesystem) | CONTRACT — each of MOD-001..004's positive/negative cases, exact rule-id set | `ModuleBoundaryRules` | — |
| `ArchitectureRulesTest` | UNIT (Tier 1) | CONTRACT — each of ARCH-001..004's positive/negative cases, the two-hop accepted gap, multi-package-cycle sequencing | `ArchitectureRules` | The most thorough single rule-set test file (448 lines) |
| `FrameworkConventionRulesTest` | UNIT (Tier 1) | CONTRACT — each of FC-001/-001-PW/-002/-002-PW/-003/-004/-005's cases | `FrameworkConventionRules` | Equally thorough (457 lines) |
| `JavaSourceScannerTest` | FS | CONTRACT — both source roots scanned, deterministic ordering, all bounds, symlink rejection, plus the cross-thread language-level regression test | `JavaSourceScanner` | The cross-thread test's own javadoc states it would pass vacuously if `regression-core` ever stopped containing JAVA_21-only syntax (records, pattern-matching `instanceof`, switch expressions) |
| `ModuleBoundariesToolContractTest` | FS (schema-only) | CONTRACT — exact input schema (structural equality), output `oneOf` branch count, all 4 annotations | `ModuleBoundariesTool` | Output schema is only checked for `oneOf` presence and branch count, not exact field/type contents |
| `FrameworkConventionsToolContractTest` | FS (schema-only) | Same, plus `violations`/`advisoryViolations` presence in the required list | `FrameworkConventionsTool` | Same strength profile |
| `ArchitectureToolContractTest` | FS (schema-only) | Same shape | `ArchitectureTool` | Same strength profile |
| `ModuleBoundariesToolTest` | FS (functional) | CONTRACT — a real MOD-001 violation end-to-end, module+profile filtering, `UNKNOWN_MODULE` rejection | `ModuleBoundariesTool.evaluate` | **Never asserts `truncated`'s value** |
| `FrameworkConventionsToolTest` | FS (functional) | Same shape, plus the `advisoryViolations`/FC-004 partition | `FrameworkConventionsTool.evaluate` | **Never asserts `truncated`'s value** |
| `ArchitectureToolTest` | FS (functional) + 2 real-reactor tests | Same shape, plus ARCH-004 partition, plus two tests against the **real repository filesystem** (zero ARCH-002 cycles across all 5 real modules; exactly 18 real ARCH-003 violations across exactly 7 named `regression-jhipster` files) | `ArchitectureTool.evaluate` | **Never asserts `truncated`'s value.** The real-reactor tests are the only place in the suite that would catch a real ARCH-002/ARCH-003 regression against this repository's actual current source, not just synthetic fixtures |

## Cross-cutting gaps, named once here rather than repeated per row

1. **`TestRunCoordinator`'s early-cause and `InterruptedException` terminal
   paths have zero test coverage** — see the `TestRunCoordinatorTest` row.
   Tracked as `docs/TECHNICAL_DEBT.md` item B8.
2. **`MavenRuntimeConfigurationLoader.load` has no dedicated direct test**
   — see the row between `BoundedLogDrainerLimitTest` and
   `FailureArtifactStoreTest`. Tracked as item B9.
3. **`ModuleValidationResult.truncated` is asserted nowhere as a real,
   production-computed value** — neither the record's own unit test nor
   any of the three validator functional tests (`ModuleBoundariesToolTest`,
   `FrameworkConventionsToolTest`, `ArchitectureToolTest`) reads it.
   Tracked as item D12.
4. **`SurefireSummaryStoreTest`'s `legacy`/`old` fixture is not a frozen
   literal** — see the `SurefireSummaryStoreTest` row above. It is built
   by live Jackson serialization of a current-shape `RunSnapshot` at
   test-run time, so it silently tracks the current record shape rather
   than the historical, pre-Stage-14-schema shape it appears to prove,
   unlike `ReportCaptureTest`'s genuinely hand-written literal for the
   same kind of claim. Does not currently affect either test's pass/fail
   outcome, since the one assertion both fixtures support (`NOT_FOUND`)
   short-circuits before the difference could matter. Tracked as
   `docs/TECHNICAL_DEBT.md` item D9.
