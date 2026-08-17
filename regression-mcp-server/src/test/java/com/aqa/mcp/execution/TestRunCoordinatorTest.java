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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

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
        await(scheduler::installed, "timeout scheduler installation");

        scheduler.fire();
        RunSnapshot terminal = awaitTerminal(coordinator, run.runId());

        assertThat(terminal.state()).isEqualTo(TestRunState.TIMED_OUT);
        assertThat(launcher.process().isAlive()).isFalse();
    }

    @Test
    void runningIsNotObservableUntilTheOwnedTimeoutHandleIsInstalled() throws Exception {
        ControlledProcessLauncher launcher = launcher("WAIT");
        ManualTimeoutScheduler scheduler = ManualTimeoutScheduler.blocking();
        TestRunCoordinator coordinator = coordinator(launcher, scheduler);
        RunSnapshot run = coordinator.start(request(), Map.of());

        assertThat(launcher.awaitLaunch(5, TimeUnit.SECONDS)).isTrue();
        assertThat(scheduler.awaitSchedule(5, TimeUnit.SECONDS)).isTrue();
        RunStore.PersistedRun queued = new RunStore(root).persisted(run.runId());
        assertThat(coordinator.get(run.runId()).state()).isEqualTo(TestRunState.QUEUED);
        assertThat(queued.snapshot().state()).isEqualTo(TestRunState.QUEUED);
        assertThat(rootIdentity(queued).pid()).isEqualTo(launcher.process().pid());

        scheduler.releaseSchedule();
        awaitState(coordinator, run.runId(), TestRunState.RUNNING);
        assertThat(scheduler.successfulInstallations()).isEqualTo(1);
        assertThat(scheduler.future().isCancelled()).isFalse();

        coordinator.cancel(run.runId());
        awaitTerminal(coordinator, run.runId());
    }

    @Test
    void timeoutSchedulingFailureNeverPublishesRunningAndCleansProcessAndLock() throws Exception {
        ControlledProcessLauncher launcher = launcher("SPAWN_GRANDCHILD");
        ManualTimeoutScheduler scheduler = ManualTimeoutScheduler.blockingFailure();
        TestRunCoordinator coordinator = coordinator(launcher, scheduler);
        RunSnapshot run = coordinator.start(request(), Map.of());

        assertThat(launcher.awaitLaunch(5, TimeUnit.SECONDS)).isTrue();
        assertThat(scheduler.awaitSchedule(5, TimeUnit.SECONDS)).isTrue();
        assertThat(coordinator.get(run.runId()).state()).isEqualTo(TestRunState.QUEUED);
        long rootPid = launcher.process().pid();
        await(() -> ProcessHandle.of(rootPid).map(handle -> handle.descendants().count()).orElse(0L) >= 2,
                "child and grandchild fixture processes visible as OS-level descendants of the root process");
        scheduler.releaseSchedule();

        RunSnapshot terminal = awaitTerminal(coordinator, run.runId());
        assertThat(terminal.state()).isEqualTo(TestRunState.ERROR);
        assertThat(scheduler.successfulInstallations()).isZero();
        assertThat(launcher.process().isAlive()).isFalse();
        RunStore.PersistedRun persisted = new RunStore(root).persisted(run.runId());
        assertThat(persisted.snapshot().state()).isEqualTo(TestRunState.ERROR);
        assertThat(persisted.ownedProcesses()).hasSizeGreaterThanOrEqualTo(3);
        TestRunCoordinator second = coordinator(launcher("PASS"), new ManualTimeoutScheduler());
        assertThat(awaitTerminal(second, second.start(request(), Map.of()).runId()).state()).isEqualTo(TestRunState.PASSED);
    }

    @Test
    void immediateTimeoutAtPublicationBoundaryProducesOnlyTimedOutTerminalState() {
        ControlledProcessLauncher launcher = launcher("WAIT");
        TestRunCoordinator coordinator = coordinator(launcher, ManualTimeoutScheduler.immediate());

        RunSnapshot terminal = awaitTerminal(coordinator, coordinator.start(request(), Map.of()).runId());

        assertThat(terminal.state()).isEqualTo(TestRunState.TIMED_OUT);
        assertThat(launcher.process().isAlive()).isFalse();
    }

    @Test
    void cancellationAtSchedulingPublicationBoundaryStaysQueuedUntilCleanup() throws Exception {
        ControlledProcessLauncher launcher = launcher("WAIT");
        ManualTimeoutScheduler scheduler = ManualTimeoutScheduler.blocking();
        TestRunCoordinator coordinator = coordinator(launcher, scheduler);
        RunSnapshot run = coordinator.start(request(), Map.of());

        assertThat(scheduler.awaitSchedule(5, TimeUnit.SECONDS)).isTrue();
        coordinator.cancel(run.runId());
        assertThat(coordinator.get(run.runId()).state()).isEqualTo(TestRunState.QUEUED);
        scheduler.releaseSchedule();

        assertThat(awaitTerminal(coordinator, run.runId()).state()).isEqualTo(TestRunState.CANCELLED);
        assertThat(scheduler.successfulInstallations()).isEqualTo(1);
    }

    @Test
    void closeAtSchedulingPublicationBoundaryLeavesNoSurvivor() throws Exception {
        ControlledProcessLauncher launcher = launcher("WAIT");
        ManualTimeoutScheduler scheduler = ManualTimeoutScheduler.blocking();
        TestRunCoordinator coordinator = coordinator(launcher, scheduler);
        RunSnapshot run = coordinator.start(request(), Map.of());

        assertThat(scheduler.awaitSchedule(5, TimeUnit.SECONDS)).isTrue();
        Thread closer = new Thread(coordinator::close, "close-at-timeout-boundary");
        closer.start();
        await(() -> !launcher.process().isAlive(), "close termination while scheduling is blocked");
        scheduler.releaseSchedule();
        closer.join(TimeUnit.SECONDS.toMillis(10));

        assertThat(closer.isAlive()).isFalse();
        assertThat(awaitTerminal(coordinator, run.runId()).state()).isEqualTo(TestRunState.CANCELLED);
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
    void realChildAndGrandchildArePersistedAndRemovedDeepestFirstOnCancellation() throws Exception {
        ControlledProcessLauncher launcher = launcher("SPAWN_GRANDCHILD");
        ManualTimeoutScheduler scheduler = new ManualTimeoutScheduler();
        TestRunCoordinator coordinator = coordinator(launcher, scheduler);
        RunSnapshot run = coordinator.start(request(), Map.of());
        awaitState(coordinator, run.runId(), TestRunState.RUNNING);
        await(() -> coordinator.ownedProcessCount(run.runId()) >= 3, "root child and grandchild ownership retention");

        coordinator.cancel(run.runId());
        RunSnapshot terminal = awaitTerminal(coordinator, run.runId());

        assertThat(terminal.state()).isEqualTo(TestRunState.CANCELLED);
        assertThat(new RunStore(root).persisted(run.runId()).ownedProcesses()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(scheduler.future().isCancelled()).isTrue();
        assertNoSurvivor(launcher);
        assertNoCaptureLeftovers(run.runId());
        try (RunStore.Lock ignored = new RunStore(root).acquireActiveLock()) {
            assertThat(ignored).isNotNull();
        }
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

    @Test
    void terminalPublicationAndLockReleaseFollowRequiredCapturePublication() throws Exception {
        ControlledProcessLauncher launcher = launcher("PASS", true);
        TestRunCoordinator coordinator = coordinator(launcher, new ManualTimeoutScheduler());
        RunSnapshot run = coordinator.start(request(), Map.of());
        assertThat(launcher.awaitLaunch(5, TimeUnit.SECONDS)).isTrue();
        String reports = launcher.invocation().arguments().stream()
                .filter(argument -> argument.startsWith("-Dmcp.surefire.reportsDirectory=")).findFirst().orElseThrow()
                .substring("-Dmcp.surefire.reportsDirectory=".length());
        Path staging = Path.of(reports);
        Files.writeString(staging.resolve("TEST-capture.xml"), "<testsuite name='capture' tests='1' failures='0' errors='0' skipped='0' time='0.1'><testcase classname='capture' name='passes' time='0.1'/></testsuite>");
        launcher.releaseLaunch();

        RunSnapshot terminal = awaitTerminal(coordinator, run.runId());
        RunStore.PersistedRun persisted = new RunStore(root).persisted(run.runId());
        assertThat(terminal.terminal()).isTrue();
        assertThat(persisted.capture().status()).isEqualTo(CaptureStatus.PARTIAL);
        assertThat(Files.isRegularFile(root.resolve(".regression-mcp/runs").resolve(run.runId()).resolve("reports/surefire/index.json"))).isTrue();
        TestRunCoordinator second = coordinator(launcher("PASS"), new ManualTimeoutScheduler());
        assertThat(awaitTerminal(second, second.start(request(), Map.of()).runId()).state()).isEqualTo(TestRunState.PASSED);
    }

    @Test
    void normalCompletionCancelsTimeoutAndLateCallbackCannotOverwriteTerminalState() {
        ManualTimeoutScheduler scheduler = new ManualTimeoutScheduler();
        TestRunCoordinator coordinator = coordinator(launcher("PASS"), scheduler);
        RunSnapshot run = coordinator.start(request(), Map.of());

        RunSnapshot terminal = awaitTerminal(coordinator, run.runId());
        assertThat(terminal.state()).isEqualTo(TestRunState.PASSED);
        assertThat(scheduler.future().isCancelled()).isTrue();
        scheduler.fire();

        assertThat(coordinator.get(run.runId()).state()).isEqualTo(TestRunState.PASSED);
    }

    private TestRunCoordinator coordinator(ControlledProcessLauncher launcher, TestRunCoordinator.TimeoutScheduler scheduler) {
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

    private void assertNoCaptureLeftovers(String runId) throws Exception {
        Path runDirectory = root.resolve(".regression-mcp/runs").resolve(runId);
        try (Stream<Path> staging = Files.list(runDirectory.resolve("staging"));
                Stream<Path> files = Files.walk(runDirectory)) {
            assertThat(staging.toList()).isEmpty();
            assertThat(files.map(Path::getFileName).filter(java.util.Objects::nonNull).map(Path::toString)
                    .noneMatch(name -> name.endsWith(".tmp"))).isTrue();
        }
    }

    @Test
    void publicStatusPollingAndPersistedOwnershipPollingRemainSafeDuringObserverUpdates() throws Exception {
        ControlledProcessLauncher launcher = launcher("SPAWN_GRANDCHILD");
        ManualTimeoutScheduler scheduler = new ManualTimeoutScheduler();
        TestRunCoordinator coordinator = coordinator(launcher, scheduler);
        RunSnapshot run = coordinator.start(request(), Map.of());
        RunStore pollingStore = new RunStore(root);

        await(() -> coordinator.get(run.runId()).state() == TestRunState.RUNNING
                && pollingStore.persisted(run.runId()).ownedProcesses().size() >= 3,
                "public RUNNING status and persisted root child grandchild ownership");
        coordinator.cancel(run.runId());

        RunSnapshot terminal = awaitTerminal(coordinator, run.runId());
        assertThat(terminal.state()).isEqualTo(TestRunState.CANCELLED);
        assertThat(scheduler.future().isCancelled()).isTrue();
        assertNoSurvivor(launcher);
        assertNoCaptureLeftovers(run.runId());
        try (RunStore.Lock ignored = new RunStore(root).acquireActiveLock()) {
            assertThat(ignored).isNotNull();
        }
    }

    private static OwnedProcessIdentity rootIdentity(RunStore.PersistedRun persisted) {
        return persisted.ownedProcesses().stream().min(java.util.Comparator.comparingInt(OwnedProcessIdentity::depth)).orElseThrow();
    }

    private static final class ManualTimeoutScheduler implements TestRunCoordinator.TimeoutScheduler {
        private Runnable task;
        private final TestFuture future = new TestFuture();
        private final CountDownLatch scheduleEntered = new CountDownLatch(1);
        private final CountDownLatch scheduleRelease;
        private final boolean fail;
        private final boolean immediate;
        private final AtomicInteger successfulInstallations = new AtomicInteger();
        ManualTimeoutScheduler() { this(false, false, false); }
        private ManualTimeoutScheduler(boolean blocked, boolean fail, boolean immediate) {
            this.scheduleRelease = blocked ? new CountDownLatch(1) : null;
            this.fail = fail;
            this.immediate = immediate;
        }
        static ManualTimeoutScheduler blocking() { return new ManualTimeoutScheduler(true, false, false); }
        static ManualTimeoutScheduler blockingFailure() { return new ManualTimeoutScheduler(true, true, false); }
        static ManualTimeoutScheduler immediate() { return new ManualTimeoutScheduler(false, false, true); }
        @Override public ScheduledFuture<?> schedule(Runnable scheduledTask, int timeoutSeconds) {
            task = scheduledTask;
            scheduleEntered.countDown();
            if (scheduleRelease != null) {
                try { scheduleRelease.await(); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            }
            if (fail) throw new IllegalStateException("fixture timeout scheduling failure");
            if (immediate) task.run();
            successfulInstallations.incrementAndGet();
            return future;
        }
        boolean installed() { return task != null; }
        boolean awaitSchedule(long timeout, TimeUnit unit) throws InterruptedException { return scheduleEntered.await(timeout, unit); }
        void releaseSchedule() { if (scheduleRelease != null) scheduleRelease.countDown(); }
        int successfulInstallations() { return successfulInstallations.get(); }
        TestFuture future() { return future; }
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
