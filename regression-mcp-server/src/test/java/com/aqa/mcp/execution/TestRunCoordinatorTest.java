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
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestRunCoordinatorTest {
    @TempDir Path root;
    private final List<TestRunCoordinator> coordinators = new ArrayList<>();
    private final List<ControlledProcessLauncher> launchers = new ArrayList<>();

    @BeforeEach
    void setUpRepository() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
    }

    @AfterEach
    void tearDown() {
        coordinators.forEach(TestRunCoordinator::close);
        launchers.forEach(this::assertNoSurvivor);
    }

    @Test
    void passTransitionsFromPersistedQueuedToPassedWithRootIdentity() throws Exception {
        ControlledProcessLauncher launcher = launcher("PASS", true);
        TestRunCoordinator coordinator = coordinator(launcher, new ManualTimeoutScheduler());

        RunSnapshot queued = coordinator.start(request(), Map.of());

        assertThat(launcher.awaitLaunch(5, TimeUnit.SECONDS)).isTrue();
        assertThat(queued.state()).isEqualTo(TestRunState.QUEUED);
        assertThat(new RunStore(root).get(queued.runId()).state()).isEqualTo(TestRunState.QUEUED);
        launcher.releaseLaunch();
        RunSnapshot terminal = awaitTerminal(coordinator, queued.runId());
        RunStore.PersistedRun persisted = new RunStore(root).persisted(queued.runId());
        assertThat(terminal.state()).isEqualTo(TestRunState.PASSED);
        assertThat(persisted.snapshot().state()).isEqualTo(TestRunState.PASSED);
        assertThat(rootIdentity(persisted).pid()).isEqualTo(launcher.process().pid());
        assertThat(rootIdentity(persisted).startInstant()).isNotNull();
        assertThat(terminal.finishedAt()).isNotNull();
    }

    @Test
    void failTransitionsToFailed() {
        TestRunCoordinator coordinator = coordinator(launcher("FAIL"), new ManualTimeoutScheduler());

        RunSnapshot terminal = awaitTerminal(coordinator, coordinator.start(request(), Map.of()).runId());

        assertThat(terminal.state()).isEqualTo(TestRunState.FAILED);
        assertThat(terminal.exitCode()).isEqualTo(7);
    }

    @Test
    void cancellationIsIdempotentAndPersistsOnlyAfterCleanup() throws Exception {
        ControlledProcessLauncher launcher = launcher("WAIT");
        TestRunCoordinator coordinator = coordinator(launcher, new ManualTimeoutScheduler());
        RunSnapshot run = coordinator.start(request(), Map.of());
        RunSnapshot running = awaitState(coordinator, run.runId(), TestRunState.RUNNING);
        RunStore.PersistedRun runningPersistence = new RunStore(root).persisted(run.runId());
        assertThat(runningPersistence.snapshot()).isEqualTo(running);
        assertThat(rootIdentity(runningPersistence).pid()).isEqualTo(launcher.process().pid());
        assertThat(rootIdentity(runningPersistence).startInstant()).isNotNull();

        coordinator.cancel(run.runId());
        coordinator.cancel(run.runId());
        RunSnapshot terminal = awaitTerminal(coordinator, run.runId());

        assertThat(terminal.state()).isEqualTo(TestRunState.CANCELLED);
        assertThat(launcher.process().isAlive()).isFalse();
        assertThat(new RunStore(root).persisted(run.runId()).snapshot()).isEqualTo(terminal);
    }

    @Test
    void timeoutUsesInternalSchedulerSeamAndTransitionsToTimedOut() {
        ControlledProcessLauncher launcher = launcher("WAIT");
        ManualTimeoutScheduler scheduler = new ManualTimeoutScheduler();
        TestRunCoordinator coordinator = coordinator(launcher, scheduler);
        RunSnapshot run = coordinator.start(request(), Map.of());
        awaitState(coordinator, run.runId(), TestRunState.RUNNING);

        scheduler.fire();
        RunSnapshot terminal = awaitTerminal(coordinator, run.runId());

        assertThat(terminal.state()).isEqualTo(TestRunState.TIMED_OUT);
        assertThat(launcher.process().isAlive()).isFalse();
    }

    @Test
    void closeTerminatesAnActiveControlledProcess() {
        ControlledProcessLauncher launcher = launcher("WAIT");
        TestRunCoordinator coordinator = coordinator(launcher, new ManualTimeoutScheduler());
        RunSnapshot run = coordinator.start(request(), Map.of());
        awaitState(coordinator, run.runId(), TestRunState.RUNNING);

        coordinator.close();
        RunSnapshot terminal = awaitTerminal(coordinator, run.runId());

        assertThat(terminal.state()).isEqualTo(TestRunState.CANCELLED);
        assertThat(launcher.process().isAlive()).isFalse();
    }

    @Test
    void forcedTerminationHandlesAProcessThatIgnoresGracefulTermination() {
        ControlledProcessLauncher launcher = launcher("IGNORE_GRACEFUL_TERMINATION");
        TestRunCoordinator coordinator = coordinator(launcher, new ManualTimeoutScheduler());
        RunSnapshot run = coordinator.start(request(), Map.of());
        awaitState(coordinator, run.runId(), TestRunState.RUNNING);

        coordinator.cancel(run.runId());
        RunSnapshot terminal = awaitTerminal(coordinator, run.runId());

        assertThat(terminal.state()).isEqualTo(TestRunState.CANCELLED);
        assertThat(launcher.forceDestroyCalled()).isTrue();
        assertThat(launcher.process().isAlive()).isFalse();
    }

    @Test
    void repositoryLockRejectsAnotherCoordinatorThenReleasesAfterTerminalPersistence() throws Exception {
        ControlledProcessLauncher firstLauncher = launcher("WAIT");
        TestRunCoordinator first = coordinator(firstLauncher, new ManualTimeoutScheduler());
        RunSnapshot firstRun = first.start(request(), Map.of());
        awaitState(first, firstRun.runId(), TestRunState.RUNNING);

        ControlledProcessLauncher secondLauncher = launcher("PASS");
        TestRunCoordinator second = coordinator(secondLauncher, new ManualTimeoutScheduler());
        assertThatThrownBy(() -> second.start(request(), Map.of()))
                .isInstanceOf(ExecutionPlanningException.class)
                .extracting(exception -> ((ExecutionPlanningException) exception).code())
                .isEqualTo("RUN_ALREADY_ACTIVE");
        try (var runs = Files.list(root.resolve(".regression-mcp/runs"))) {
            assertThat(runs.filter(Files::isDirectory).count()).isEqualTo(1);
        }

        first.cancel(firstRun.runId());
        RunSnapshot firstTerminal = awaitTerminal(first, firstRun.runId());
        assertThat(new RunStore(root).persisted(firstRun.runId()).snapshot()).isEqualTo(firstTerminal);
        RunSnapshot secondRun = second.start(request(), Map.of());
        assertThat(awaitTerminal(second, secondRun.runId()).state()).isEqualTo(TestRunState.PASSED);
    }

    @Test
    void realChildAndGrandchildArePersistedAndRemovedDeepestFirstOnCancellation() {
        ControlledProcessLauncher launcher = launcher("SPAWN_GRANDCHILD");
        TestRunCoordinator coordinator = coordinator(launcher, new ManualTimeoutScheduler());
        RunSnapshot run = coordinator.start(request(), Map.of());
        awaitState(coordinator, run.runId(), TestRunState.RUNNING);
        await(() -> new RunStore(root).persisted(run.runId()).ownedProcesses().size() >= 3, "root child and grandchild persistence");

        coordinator.cancel(run.runId());
        RunSnapshot terminal = awaitTerminal(coordinator, run.runId());

        assertThat(terminal.state()).isEqualTo(TestRunState.CANCELLED);
        assertThat(new RunStore(root).persisted(run.runId()).ownedProcesses()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void retainedChildIsRemovedWhenParentExitsBeforeCoordinatorCleanup() {
        ControlledProcessLauncher launcher = launcher("SPAWN_CHILD_AND_EXIT_PARENT");
        TestRunCoordinator coordinator = coordinator(launcher, new ManualTimeoutScheduler());
        RunSnapshot run = coordinator.start(request(), Map.of());

        RunSnapshot terminal = awaitTerminal(coordinator, run.runId());

        assertThat(terminal.state()).isEqualTo(TestRunState.PASSED);
        assertThat(new RunStore(root).persisted(run.runId()).ownedProcesses()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void terminalSnapshotPersistsCappedLogSizesAndObservedDroppedCounters() {
        TestRunCoordinator coordinator = coordinator(launcher("LARGE_OUTPUT"), new ManualTimeoutScheduler());
        RunSnapshot terminal = awaitTerminal(coordinator, coordinator.start(request(), Map.of()).runId());
        RunStore.PersistedRun persisted = new RunStore(root).persisted(terminal.runId());

        assertThat(terminal.stdoutBytes()).isEqualTo(BoundedLogDrainer.FILE_LIMIT);
        assertThat(terminal.stderrBytes()).isEqualTo(BoundedLogDrainer.FILE_LIMIT);
        assertThat(terminal.stdoutTruncated()).isTrue();
        assertThat(terminal.stderrTruncated()).isTrue();
        assertThat(persisted.stdoutObservedBytes()).isGreaterThan(BoundedLogDrainer.FILE_LIMIT);
        assertThat(persisted.stderrObservedBytes()).isGreaterThan(BoundedLogDrainer.FILE_LIMIT);
        assertThat(persisted.stdoutDroppedBytes()).isEqualTo(persisted.stdoutObservedBytes() - BoundedLogDrainer.FILE_LIMIT);
        assertThat(persisted.stderrDroppedBytes()).isEqualTo(persisted.stderrObservedBytes() - BoundedLogDrainer.FILE_LIMIT);
    }

    private TestRunCoordinator coordinator(ControlledProcessLauncher launcher, ManualTimeoutScheduler scheduler) {
        launchers.add(launcher);
        TestRunCoordinator coordinator = new TestRunCoordinator(root, this::validator, launcher, scheduler, ignored -> runtime());
        coordinators.add(coordinator);
        return coordinator;
    }

    private TestRunRequestValidator validator() {
        return new TestRunRequestValidator(List.of(ExecutionProfileRegistry.COMMERCE_MODULE));
    }

    private StartTestRunRequest request() {
        return new StartTestRunRequest(ExecutionProfileRegistry.COMMERCE_MODULE, null, "dev", true, 30);
    }

    private ControlledProcessLauncher launcher(String mode) {
        return new ControlledProcessLauncher(mode);
    }

    private ControlledProcessLauncher launcher(String mode, boolean holdLaunch) {
        return new ControlledProcessLauncher(mode, holdLaunch);
    }

    private MavenRuntimeConfiguration runtime() {
        try {
            Path mavenHome = root.resolve("test-maven");
            Files.createDirectories(mavenHome.resolve("boot"));
            Files.createDirectories(mavenHome.resolve("bin"));
            Files.createDirectories(mavenHome.resolve("lib/jansi-native"));
            Files.writeString(mavenHome.resolve("bin/m2.conf"), "test");
            Files.writeString(mavenHome.resolve("boot/plexus-classworlds-test.jar"), "test");
            String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
            Path java = Path.of(System.getProperty("java.home"), "bin", executable).toRealPath();
            return MavenRuntimeConfiguration.fromTrustedPaths(java, mavenHome);
        } catch (Exception exception) {
            throw new AssertionError("Unable to create controlled runtime", exception);
        }
    }

    private static RunSnapshot awaitState(TestRunCoordinator coordinator, String runId, TestRunState state) {
        await(() -> coordinator.get(runId).state() == state, "state " + state);
        return coordinator.get(runId);
    }

    private static RunSnapshot awaitTerminal(TestRunCoordinator coordinator, String runId) {
        await(() -> coordinator.get(runId).terminal(), "terminal state");
        return coordinator.get(runId);
    }

    private static void await(BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            java.util.concurrent.locks.LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        assertThat(condition.getAsBoolean()).as("await %s", description).isTrue();
    }

    private void assertNoSurvivor(ControlledProcessLauncher launcher) {
        await(() -> ProcessHandle.allProcesses().noneMatch(process -> process.info().commandLine()
                .map(command -> command.contains(launcher.token)).orElse(false)), "fixture survivor " + launcher.token);
    }

    private static OwnedProcessIdentity rootIdentity(RunStore.PersistedRun persisted) {
        return persisted.ownedProcesses().stream().min(java.util.Comparator.comparingInt(OwnedProcessIdentity::depth)).orElseThrow();
    }

    private static final class ManualTimeoutScheduler implements TestRunCoordinator.TimeoutScheduler {
        private Runnable task;
        private final TestFuture future = new TestFuture();
        @Override public ScheduledFuture<?> schedule(Runnable scheduledTask, int timeoutSeconds) { task = scheduledTask; return future; }
        void fire() { assertThat(task).isNotNull(); task.run(); }
    }

    private static final class TestFuture implements ScheduledFuture<Object> {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(Delayed other) { return 0; }
        @Override public boolean cancel(boolean mayInterruptIfRunning) { return cancelled.compareAndSet(false, true); }
        @Override public boolean isCancelled() { return cancelled.get(); }
        @Override public boolean isDone() { return cancelled.get(); }
        @Override public Object get() { return null; }
        @Override public Object get(long timeout, TimeUnit unit) { return null; }
    }
}
