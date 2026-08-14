package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaleRunRecoveryTest {
    @TempDir Path root;

    @Test
    void queuedRunRecoversToStructuredTerminalError() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        RunStore store = new RunStore(root); RunSnapshot queued = snapshot(TestRunState.QUEUED); store.create(queued);
        TestRunCoordinator coordinator = coordinator(new FakeView());
        try {
            RunSnapshot recovered = store.get(queued.runId());
            assertThat(recovered.state()).isEqualTo(TestRunState.ERROR);
            assertThat(recovered.reason()).isEqualTo("SERVER_RESTART_RECOVERY");
        } finally { coordinator.close(); }
    }

    @Test
    void matchingLiveIdentityIsCleanedDuringRecoveryBeforeTerminalPublication() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        RunStore store = new RunStore(root); RunSnapshot queued = snapshot(TestRunState.QUEUED); store.create(queued);
        FakeView view = new FakeView(); ObservedProcess rootProcess = view.add(41, 1, null, 1);
        store.update(running(queued), List.of(new OwnedProcessIdentity(41, rootProcess.startInstant(), null, 1, Instant.now())));
        TestRunCoordinator coordinator = coordinator(view);
        try {
            assertThat(view.destroyed).containsExactly(41L);
            assertThat(store.get(queued.runId()).reason()).isEqualTo("SERVER_RESTART_RECOVERY");
        } finally { coordinator.close(); }
    }

    @Test
    void reusedPidIsNotTerminatedAndRecoversSafely() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        RunStore store = new RunStore(root); RunSnapshot queued = snapshot(TestRunState.QUEUED); store.create(queued);
        FakeView view = new FakeView(); view.add(41, 99, null, 1);
        store.update(running(queued), List.of(new OwnedProcessIdentity(41, Instant.ofEpochSecond(1), null, 1, Instant.now())));
        TestRunCoordinator coordinator = coordinator(view);
        try {
            assertThat(view.destroyed).isEmpty();
            assertThat(store.get(queued.runId()).reason()).isEqualTo("SERVER_RESTART_RECOVERY");
        } finally { coordinator.close(); }
    }

    @Test
    void runningMetadataWithoutIdentityBlocksNewExecution() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        RunStore store = new RunStore(root); RunSnapshot queued = snapshot(TestRunState.QUEUED); store.create(queued); store.update(running(queued), List.of());
        TestRunCoordinator coordinator = coordinator(new FakeView());
        try {
            assertThatThrownBy(() -> coordinator.start(request(), Map.of())).extracting(error -> ((ExecutionPlanningException) error).code())
                    .isEqualTo("STALE_RUN_RECOVERY_REQUIRED");
        } finally { coordinator.close(); }
    }

    @Test
    void deadIdentityRecoversWithoutAttemptingTerminationAndTerminalMetadataIsUnchanged() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        RunStore store = new RunStore(root); RunSnapshot queued = snapshot(TestRunState.QUEUED); store.create(queued);
        OwnedProcessIdentity dead = new OwnedProcessIdentity(88, Instant.ofEpochSecond(1), null, 1, Instant.now());
        store.update(running(queued), List.of(dead));
        FakeView view = new FakeView(); TestRunCoordinator coordinator = coordinator(view);
        try {
            assertThat(view.destroyed).isEmpty();
            assertThat(store.get(queued.runId()).reason()).isEqualTo("SERVER_RESTART_RECOVERY");
            RunSnapshot terminal = new RunSnapshot(queued.runId(), queued.module(), queued.environment(), queued.headless(), queued.tags(), queued.timeoutSeconds(), TestRunState.PASSED, queued.createdAt(), Instant.now(), Instant.now(), 0, "PASSED", 0, 0, false, false);
            store.update(terminal, List.of());
            TestRunCoordinator second = coordinator(view);
            try { assertThat(store.get(queued.runId())).isEqualTo(terminal); } finally { second.close(); }
        } finally { coordinator.close(); }
    }

    @Test
    void corruptedActiveMetadataBlocksExecutionWithoutSpeculativeCleanup() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        RunStore store = new RunStore(root); RunSnapshot queued = snapshot(TestRunState.QUEUED); store.create(queued);
        Files.writeString(root.resolve(".regression-mcp/runs").resolve(queued.runId()).resolve("status.json"), "{");
        TestRunCoordinator coordinator = coordinator(new FakeView());
        try {
            assertThatThrownBy(() -> coordinator.start(request(), Map.of())).extracting(error -> ((ExecutionPlanningException) error).code())
                    .isEqualTo("STALE_RUN_RECOVERY_REQUIRED");
        } finally { coordinator.close(); }
    }

    @Test
    void descendantRecoveryUsesRetainedIdentityAfterRootIsGone() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        RunStore store = new RunStore(root); RunSnapshot queued = snapshot(TestRunState.QUEUED); store.create(queued);
        FakeView view = new FakeView(); ObservedProcess child = view.add(42, 2, null, 2);
        List<OwnedProcessIdentity> identities = List.of(new OwnedProcessIdentity(41, Instant.ofEpochSecond(1), null, 1, Instant.now()),
                new OwnedProcessIdentity(42, child.startInstant(), 41L, 2, Instant.now()));
        store.update(running(queued), identities);
        TestRunCoordinator coordinator = coordinator(view);
        try {
            assertThat(view.destroyed).containsExactly(42L);
            assertThat(store.get(queued.runId()).reason()).isEqualTo("SERVER_RESTART_RECOVERY");
        } finally { coordinator.close(); }
    }

    private TestRunCoordinator coordinator(ProcessView view) {
        return new TestRunCoordinator(root, () -> new TestRunRequestValidator(List.of(ExecutionProfileRegistry.COMMERCE_MODULE)),
                invocation -> { throw new AssertionError("Recovery must not launch a process."); }, new NoTimeouts(), ignored -> null, view);
    }
    private static StartTestRunRequest request() { return new StartTestRunRequest(ExecutionProfileRegistry.COMMERCE_MODULE, null, "dev", true, 30); }
    private static RunSnapshot snapshot(TestRunState state) { String id = RunId.generate(); Instant now = Instant.now(); return new RunSnapshot(id, ExecutionProfileRegistry.COMMERCE_MODULE, "dev", true, "not @wip", 30, state, now, null, null, null, state.name(), 0, 0, false, false); }
    private static RunSnapshot running(RunSnapshot snapshot) { return new RunSnapshot(snapshot.runId(), snapshot.module(), snapshot.environment(), snapshot.headless(), snapshot.tags(), snapshot.timeoutSeconds(), TestRunState.RUNNING, snapshot.createdAt(), Instant.now(), null, null, "RUNNING", 0, 0, false, false); }
    private static final class NoTimeouts implements TestRunCoordinator.TimeoutScheduler { @Override public ScheduledFuture<?> schedule(Runnable task, int timeoutSeconds) { throw new AssertionError("No timeout should be scheduled."); } }
    private static final class FakeView implements ProcessView {
        private final java.util.Map<Long, ObservedProcess> processes = new java.util.HashMap<>(); private final List<Long> destroyed = new ArrayList<>();
        ObservedProcess add(long pid, long second, Long parent, int depth) { ObservedProcess process = new ObservedProcess(pid, Instant.ofEpochSecond(second), parent, depth); processes.put(pid, process); return process; }
        @Override public Optional<ObservedProcess> find(long pid) { return Optional.ofNullable(processes.get(pid)); }
        @Override public List<ObservedProcess> descendants(long rootPid, int maximum) { return List.of(); }
        @Override public boolean destroy(OwnedProcessIdentity identity, boolean forcibly, Duration grace) { destroyed.add(identity.pid()); processes.remove(identity.pid()); return true; }
    }
}
