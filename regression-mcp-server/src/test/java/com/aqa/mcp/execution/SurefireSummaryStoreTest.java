package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SurefireSummaryStoreTest {
    @TempDir Path root;

    @Test
    void readsOnlyPublishedIndexAndRejectsMissingDigestMismatchWrongRunAndActiveOrLegacyRuns() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        RunStore store = new RunStore(root); RunSnapshot queued = snapshot(TestRunState.QUEUED); store.create(queued);
        RunCaptureLayout layout = store.captureLayout(queued.runId());
        Files.writeString(layout.surefireStaging().resolve("TEST-summary.xml"), xml());
        store.updateCapture(queued.runId(), new ReportCapture().capture(layout, store.persisted(queued.runId()).capture()).metadata());
        RunSnapshot terminal = snapshot(queued, TestRunState.PASSED); store.update(terminal, List.of());
        assertThat(store.summary(queued.runId()).passed()).isEqualTo(1);

        Files.writeString(layout.surefireIndex(), "{}");
        code("REPORT_INDEX_CORRUPT", () -> store.summary(queued.runId()));
        Files.delete(layout.surefireIndex());
        code("NOT_FOUND", () -> store.summary(queued.runId()));
        code("INVALID_ARGUMENTS", () -> store.summary("bad"));

        RunSnapshot active = snapshot(TestRunState.QUEUED); store.create(active); code("RUN_NOT_TERMINAL", () -> store.summary(active.runId()));
        RunSnapshot legacy = snapshot(TestRunState.PASSED); Path legacyDir = root.resolve(".regression-mcp/runs").resolve(legacy.runId()); Files.createDirectories(legacyDir);
        String old = "{\"schemaVersion\":2,\"snapshot\":" + com.fasterxml.jackson.databind.json.JsonMapper.builder().addModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()).build().writeValueAsString(legacy) + ",\"ownedProcesses\":[],\"stdoutObservedBytes\":0,\"stdoutDroppedBytes\":0,\"stderrObservedBytes\":0,\"stderrDroppedBytes\":0}";
        Files.writeString(legacyDir.resolve("status.json"), old); Files.writeString(legacyDir.resolve("run.json"), old);
        code("NOT_FOUND", () -> store.summary(legacy.runId()));
    }

    @Test
    void summaryAndFailureSummarySucceedForEveryTerminalOutcomeStateNotJustPassed() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        for (TestRunState state : List.of(TestRunState.CANCELLED, TestRunState.TIMED_OUT, TestRunState.ERROR)) {
            RunStore store = new RunStore(root);
            RunSnapshot queued = snapshot(TestRunState.QUEUED);
            store.create(queued);
            RunCaptureLayout layout = store.captureLayout(queued.runId());
            Files.writeString(layout.surefireStaging().resolve("TEST-summary.xml"), xml());
            store.updateCapture(queued.runId(), new ReportCapture().capture(layout, store.persisted(queued.runId()).capture()).metadata());
            store.update(snapshot(queued, state), List.of());

            assertThat(store.summary(queued.runId()).passed()).as("state %s must not be rejected as non-terminal", state).isEqualTo(1);
            assertThat(store.failureSummary(queued.runId()).failureRecords()).as("state %s must not be rejected as non-terminal", state).isEmpty();
        }
    }

    private static void code(String expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ExecutionPlanningException.class).extracting(error -> ((ExecutionPlanningException) error).code()).isEqualTo(expected);
    }
    private static String xml() { return "<testsuite name='summary' tests='1' failures='0' errors='0' skipped='0' time='0.2'><testcase classname='summary' name='pass' time='0.2'/></testsuite>"; }
    private static RunSnapshot snapshot(TestRunState state) { Instant now = Instant.now(); return new RunSnapshot(RunId.generate(), ExecutionProfileRegistry.COMMERCE_MODULE, "dev", true, "not @wip", 30, state, now, state == TestRunState.QUEUED ? null : now, state.isTerminal() ? now : null, 0, state.name(), 0, 0, false, false, null); }
    private static RunSnapshot snapshot(RunSnapshot source, TestRunState state) { Instant now = Instant.now(); return new RunSnapshot(source.runId(), source.module(), source.environment(), source.headless(), source.tags(), source.timeoutSeconds(), state, source.createdAt(), now, now, 0, state.name(), 0, 0, false, false, null); }
}
