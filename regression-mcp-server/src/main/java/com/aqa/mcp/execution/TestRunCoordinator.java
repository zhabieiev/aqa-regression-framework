package com.aqa.mcp.execution;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/** One-run coordinator. Cleanup and terminal persistence precede lock release and publication. */
public final class TestRunCoordinator implements AutoCloseable {
    private final Path root;
    private final Supplier<TestRunRequestValidator> validator;
    private final RunStore store;
    private final MavenProcessLauncher launcher;
    private final Function<Map<String, String>, MavenRuntimeConfiguration> runtimeLoader;
    private final ExecutorService worker;
    private final TimeoutScheduler timeouts;
    private final ScheduledExecutorService ownershipObserver;
    private final ProcessView processView;
    private final AtomicReference<Active> active = new AtomicReference<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean recoveryBlocked = new AtomicBoolean();

    public TestRunCoordinator(Path root, Supplier<TestRunRequestValidator> validator) {
        this(root, validator, new DirectMavenProcessLauncher(), new SystemTimeoutScheduler(), MavenRuntimeConfigurationLoader::load,
                new SystemProcessView());
    }

    TestRunCoordinator(Path root, Supplier<TestRunRequestValidator> validator, MavenProcessLauncher launcher) {
        this(root, validator, launcher, new SystemTimeoutScheduler(), MavenRuntimeConfigurationLoader::load, new SystemProcessView());
    }

    TestRunCoordinator(Path root, Supplier<TestRunRequestValidator> validator, MavenProcessLauncher launcher,
            TimeoutScheduler timeouts, Function<Map<String, String>, MavenRuntimeConfiguration> runtimeLoader) {
        this(root, validator, launcher, timeouts, runtimeLoader, new SystemProcessView());
    }

    TestRunCoordinator(Path root, Supplier<TestRunRequestValidator> validator, MavenProcessLauncher launcher,
            TimeoutScheduler timeouts, Function<Map<String, String>, MavenRuntimeConfiguration> runtimeLoader, ProcessView processView) {
        this(root, validator, launcher, timeouts, runtimeLoader, processView,
                Executors.newFixedThreadPool(3, runnable -> new Thread(runnable, "regression-mcp-run-worker")));
    }

    TestRunCoordinator(Path root, Supplier<TestRunRequestValidator> validator, MavenProcessLauncher launcher,
            TimeoutScheduler timeouts, Function<Map<String, String>, MavenRuntimeConfiguration> runtimeLoader, ProcessView processView,
            ExecutorService worker) {
        this.root = root;
        this.validator = validator;
        this.launcher = launcher;
        this.timeouts = timeouts;
        this.runtimeLoader = runtimeLoader;
        this.processView = processView;
        this.worker = worker;
        this.ownershipObserver = Executors.newSingleThreadScheduledExecutor(runnable -> new Thread(runnable, "regression-mcp-ownership"));
        this.store = new RunStore(root);
        recoverIfUnowned();
    }

    public RunSnapshot start(StartTestRunRequest request, Map<String, String> environment) {
        if (closed.get()) throw new ExecutionPlanningException("MAVEN_RUNTIME_UNAVAILABLE", "The execution service is closed.");
        if (recoveryBlocked.get()) throw new ExecutionPlanningException("STALE_RUN_RECOVERY_REQUIRED", "Existing execution ownership cannot be proven safe.");
        ValidatedTestRunRequest validated = validator.get().validate(request);
        MavenRuntimeConfiguration runtime = runtimeLoader.apply(environment);
        RunStore.Lock lock = store.acquireActiveLock();
        String id = RunId.generate();
        Instant now = Instant.now();
        RunSnapshot queued = snapshot(id, validated, TestRunState.QUEUED, now, null, null, null, null, 0, 0, false, false);
        Active next = new Active(queued, validated, runtime, lock);
        if (!active.compareAndSet(null, next)) {
            lock.close();
            throw new ExecutionPlanningException("RUN_ALREADY_ACTIVE", "A test run is already active.");
        }
        try {
            store.create(queued);
            worker.submit(() -> execute(next));
            return queued;
        } catch (RuntimeException exception) {
            active.compareAndSet(next, null);
            lock.close();
            throw exception;
        }
    }

    public RunSnapshot get(String id) {
        if (!RunId.valid(id)) throw notFound();
        Active current = active.get();
        return current != null && current.snapshot.runId().equals(id) ? current.snapshot : store.get(id);
    }

    /** Package-private deterministic ownership-observation seam for lifecycle tests. */
    int ownedProcessCount(String id) {
        Active current = active.get();
        if (current != null && current.snapshot.runId().equals(id)) {
            ProcessOwnershipTracker tracker = current.tracker;
            return tracker == null ? 0 : tracker.identities().size();
        }
        return store.persisted(id).ownedProcesses().size();
    }

    public RunSnapshot cancel(String id) {
        Active current = active.get();
        if (current == null || !current.snapshot.runId().equals(id)) return get(id);
        current.cause.compareAndSet(null, TestRunState.CANCELLED);
        Process process = current.process;
        if (process != null) terminate(process);
        return current.snapshot;
    }

    private void execute(Active run) {
        Process process = null;
        BoundedLogDrainer stdout = null;
        BoundedLogDrainer stderr = null;
        Integer skippedTests = null;
        try {
            if (run.cause.get() != null) {
                Integer captured = capture(run);
                if (captured != null) skippedTests = captured;
                persistTerminal(run, run.cause.get(), null, null, null, skippedTests);
                return;
            }
            MavenInvocation invocation = MavenInvocationFactory.create(run.runtime, root, run.request, store.captureLayout(run.snapshot.runId()));
            process = launcher.launch(invocation);
            run.process = process;
            long pid = process.pid();
            ObservedProcess rootIdentity = processView.find(pid).orElseThrow(() -> new ExecutionPlanningException(
                    "PROCESS_IDENTITY_UNAVAILABLE", "The started process did not expose a usable identity."));
            ProcessOwnershipTracker tracker = new ProcessOwnershipTracker(processView, rootIdentity);
            run.tracker = tracker;
            // Persist the launched root identity while this run is still QUEUED.  A scheduler failure must
            // therefore have enough durable ownership evidence to clean up without ever publishing RUNNING.
            store.update(run.snapshot, tracker.identities());

            stdout = new BoundedLogDrainer(process.getInputStream(), store.log(run.snapshot.runId(), false));
            stderr = new BoundedLogDrainer(process.getErrorStream(), store.log(run.snapshot.runId(), true));
            worker.submit(stdout);
            worker.submit(stderr);
            Process launched = process;
            ScheduledFuture<?> timeout = Objects.requireNonNull(timeouts.schedule(() -> {
                if (run.cause.compareAndSet(null, TestRunState.TIMED_OUT)) terminate(launched);
            }, run.request.timeoutSeconds()), "Timeout scheduler returned no handle.");
            if (timeout.isCancelled()) throw new ExecutionPlanningException("TIMEOUT_SCHEDULING_FAILED", "The timeout task was cancelled during installation.");
            run.timeout = timeout;
            // Do not make RUNNING externally observable until the timeout responsibility is both
            // successfully installed and retained by the active run.  If cancellation or an immediate
            // timeout won the race, stay in the deterministic cleanup path instead.
            if (run.cause.get() == null) {
                RunSnapshot running = replace(run.snapshot, TestRunState.RUNNING, Instant.now(), null, null, 0, 0, false, false, run.snapshot.skippedTests());
                store.update(running, tracker.identities());
                run.snapshot = running;
                run.observation = ownershipObserver.scheduleAtFixedRate(() -> observe(run), 100, 100, TimeUnit.MILLISECONDS);
            }
            if (run.cause.get() != null) terminate(process);
            process.waitFor();
            cleanup(run, process);
            awaitDrainers(stdout, stderr);
            TestRunState terminal = run.cause.get();
            if (terminal == null) terminal = process.exitValue() == 0 ? TestRunState.PASSED : TestRunState.FAILED;
            Integer captured = capture(run);
            if (captured != null) skippedTests = captured;
            persistTerminal(run, terminal, process, stdout, stderr, skippedTests);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) cleanup(run, process);
            awaitDrainers(stdout, stderr);
            Integer captured = capture(run);
            if (captured != null) skippedTests = captured;
            persistTerminal(run, firstCause(run, TestRunState.CANCELLED), process, stdout, stderr, skippedTests);
        } catch (RuntimeException exception) {
            if (process != null) cleanup(run, process);
            awaitDrainers(stdout, stderr);
            Integer captured = capture(run);
            if (captured != null) skippedTests = captured;
            persistTerminal(run, firstCause(run, TestRunState.ERROR), process, stdout, stderr, skippedTests);
        } finally {
            if (run.timeout != null) run.timeout.cancel(false);
            if (run.observation != null) run.observation.cancel(false);
            active.compareAndSet(run, null);
            run.lock.close();
        }
    }

    public SurefireSummary summary(String id) {
        if (!RunId.valid(id)) throw new ExecutionPlanningException("INVALID_ARGUMENTS", "runId has an invalid format.");
        Active current = active.get();
        if (current != null && current.snapshot.runId().equals(id) && !current.snapshot.terminal()) {
            throw new ExecutionPlanningException("RUN_NOT_TERMINAL", "The requested run is not terminal.");
        }
        return store.summary(id);
    }

    public SurefireSummary failureSummary(String id) {
        if (!RunId.valid(id)) throw new ExecutionPlanningException("INVALID_ARGUMENTS", "runId has an invalid format.");
        Active current = active.get();
        if (current != null && current.snapshot.runId().equals(id) && !current.snapshot.terminal()) {
            throw new ExecutionPlanningException("RUN_NOT_TERMINAL", "The requested run is not terminal.");
        }
        return store.failureSummary(id);
    }

    /** Deliberately gated the same way as {@link #summary}/{@link #failureSummary}: a still-RUNNING active run must
     * never expose its capture set, even if a stale on-disk record has not yet observed the in-memory terminal state. */
    public List<FailureArtifact> artifacts(String id) {
        if (!RunId.valid(id)) throw new ExecutionPlanningException("INVALID_ARGUMENTS", "runId has an invalid format.");
        Active current = active.get();
        if (current != null && current.snapshot.runId().equals(id) && !current.snapshot.terminal()) {
            throw new ExecutionPlanningException("RUN_NOT_TERMINAL", "The requested run is not terminal.");
        }
        return store.artifacts(id);
    }

    public ArtifactContent readArtifact(String id, String artifactId) {
        if (!RunId.valid(id)) throw new ExecutionPlanningException("INVALID_ARGUMENTS", "runId has an invalid format.");
        Active current = active.get();
        if (current != null && current.snapshot.runId().equals(id) && !current.snapshot.terminal()) {
            throw new ExecutionPlanningException("RUN_NOT_TERMINAL", "The requested run is not terminal.");
        }
        return store.readArtifact(id, artifactId);
    }

    private Integer capture(Active run) { synchronized (run) { return capture(run.snapshot.runId()); } }

    private Integer capture(String runId) {
        RunStore.PersistedRun persisted = store.persisted(runId);
        if (persisted.capture() == null || persisted.capture().status() != CaptureStatus.PENDING) return null;
        ReportCapture.CaptureOutcome outcome = new ReportCapture().capture(store.captureLayout(runId), persisted.capture());
        store.updateCapture(runId, outcome.metadata());
        return outcome.skippedTests();
    }

    private void persistTerminal(Active run, TestRunState state, Process process,
            BoundedLogDrainer stdout, BoundedLogDrainer stderr, Integer skippedTests) {
        synchronized (run) {
            TestRunState terminal = firstCause(run, state);
            Integer exitCode = process != null && !process.isAlive() ? process.exitValue() : null;
            long stdoutBytes = stdout == null ? 0 : stdout.bytes();
            long stderrBytes = stderr == null ? 0 : stderr.bytes();
            RunSnapshot terminalSnapshot = replace(run.snapshot, terminal, run.snapshot.startedAt(), Instant.now(), exitCode,
                    stdoutBytes, stderrBytes, stdout != null && stdout.truncated(), stderr != null && stderr.truncated(), skippedTests);
            store.update(terminalSnapshot, run.tracker == null ? java.util.List.of() : run.tracker.identities(),
                    stdout == null ? 0 : stdout.observedBytes(), stdout == null ? 0 : stdout.droppedBytes(),
                    stderr == null ? 0 : stderr.observedBytes(), stderr == null ? 0 : stderr.droppedBytes());
            run.snapshot = terminalSnapshot;
        }
    }

    private static TestRunState firstCause(Active run, TestRunState fallback) {
        while (true) {
            TestRunState current = run.cause.get();
            if (current != null) return current;
            if (run.cause.compareAndSet(null, fallback)) return fallback;
        }
    }

    private void recoverIfUnowned() {
        if (!store.exists()) return;
        try (RunStore.Lock ignored = store.acquireActiveLock()) {
            for (RunStore.PersistedRun stale : store.active()) {
                RunSnapshot snapshot = stale.snapshot();
                if (snapshot.state() == TestRunState.RUNNING && stale.ownedProcesses().isEmpty()) {
                    recoveryBlocked.set(true);
                    continue;
                }
                ProcessOwnershipTracker tracker = stale.ownedProcesses().isEmpty() ? null
                        : new ProcessOwnershipTracker(processView, stale.ownedProcesses().getFirst().pid(), stale.ownedProcesses());
                if (tracker != null && tracker.hasSurvivor() && !tracker.cleanupAll()) {
                    recoveryBlocked.set(true);
                    continue;
                }
                Integer captured = capture(snapshot.runId());
                store.update(replaceWithReason(snapshot, TestRunState.ERROR, "SERVER_RESTART_RECOVERY", snapshot.startedAt(), Instant.now(),
                        snapshot.exitCode(), snapshot.stdoutBytes(), snapshot.stderrBytes(), snapshot.stdoutTruncated(), snapshot.stderrTruncated(),
                        captured != null ? captured : snapshot.skippedTests()), stale.ownedProcesses());
            }
        } catch (ExecutionPlanningException exception) {
            if (!"RUN_ALREADY_ACTIVE".equals(exception.code())) recoveryBlocked.set(true);
            // Another coordinator owns the repository lock, so its non-terminal state is live rather than stale.
        }
    }

    private static void awaitDrainers(BoundedLogDrainer stdout, BoundedLogDrainer stderr) {
        waitFor(stdout);
        waitFor(stderr);
    }

    private void observe(Active run) {
        try {
            synchronized (run) {
                if (run.tracker != null && !run.snapshot.terminal()) {
                    run.tracker.observe();
                    store.update(run.snapshot, run.tracker.identities());
                }
            }
        } catch (RuntimeException exception) {
            run.cause.compareAndSet(null, TestRunState.ERROR);
            Process process = run.process;
            if (process != null) terminate(process);
        }
    }

    private boolean cleanup(Active run, Process rootProcess) {
        if (run.tracker == null) { terminate(rootProcess); return !rootProcess.isAlive(); }
        run.tracker.observe();
        boolean descendants = run.tracker.cleanupDescendants();
        boolean rootMatches = processView.find(rootProcess.pid()).filter(observed -> run.tracker.identities().stream()
                .anyMatch(identity -> identity.pid() == rootProcess.pid() && identity.sameProcess(observed))).isPresent();
        if (rootMatches) terminate(rootProcess);
        boolean all = run.tracker.cleanupAll();
        return descendants && all && !run.tracker.hasSurvivor();
    }

    private static void waitFor(BoundedLogDrainer drainer) {
        if (drainer == null) return;
        try {
            drainer.completion().get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // A blocked pipe is bounded by process termination; terminal status still records captured data.
        }
    }

    private static void terminate(Process process) {
        process.toHandle().descendants().sorted(Comparator.<ProcessHandle>comparingInt(handle -> handle.descendants().toList().size())
                .reversed()).forEach(TestRunCoordinator::terminateHandle);
        process.destroy();
        try {
            process.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        if (process.isAlive()) process.destroyForcibly();
    }

    private static void terminateHandle(ProcessHandle handle) {
        handle.destroy();
        try {
            handle.onExit().get(2, TimeUnit.SECONDS);
        } catch (Exception exception) {
            handle.destroyForcibly();
        }
    }

    private static RunSnapshot snapshot(String id, ValidatedTestRunRequest request, TestRunState state, Instant created,
            Instant started, Instant finished, Integer exit, String reason, long stdout, long stderr,
            boolean stdoutTruncated, boolean stderrTruncated) {
        return new RunSnapshot(id, request.profile().module(), request.environment(), request.headless(),
                request.effectiveTagExpression(), request.timeoutSeconds(), state, created, started, finished, exit,
                reason, stdout, stderr, stdoutTruncated, stderrTruncated, null);
    }

    private static RunSnapshot replace(RunSnapshot snapshot, TestRunState state, Instant started, Instant finished,
            Integer exit, long stdout, long stderr, boolean stdoutTruncated, boolean stderrTruncated, Integer skippedTests) {
        return new RunSnapshot(snapshot.runId(), snapshot.module(), snapshot.environment(), snapshot.headless(), snapshot.tags(),
                snapshot.timeoutSeconds(), state, snapshot.createdAt(), started, finished, exit, state.name(), stdout, stderr,
                stdoutTruncated, stderrTruncated, skippedTests);
    }

    private static RunSnapshot replaceWithReason(RunSnapshot snapshot, TestRunState state, String reason, Instant started,
            Instant finished, Integer exit, long stdout, long stderr, boolean stdoutTruncated, boolean stderrTruncated, Integer skippedTests) {
        return new RunSnapshot(snapshot.runId(), snapshot.module(), snapshot.environment(), snapshot.headless(), snapshot.tags(),
                snapshot.timeoutSeconds(), state, snapshot.createdAt(), started, finished, exit, reason, stdout, stderr,
                stdoutTruncated, stderrTruncated, skippedTests);
    }

    private static ExecutionPlanningException notFound() {
        return new ExecutionPlanningException("RUN_NOT_FOUND", "The requested run was not found.");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        Active run = active.get();
        if (run != null) cancel(run.snapshot.runId());
        worker.shutdown();
        try {
            if (!worker.awaitTermination(10, TimeUnit.SECONDS)) worker.shutdownNow();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            worker.shutdownNow();
        }
        timeouts.close();
        ownershipObserver.shutdownNow();
    }

    @FunctionalInterface
    interface TimeoutScheduler extends AutoCloseable {
        ScheduledFuture<?> schedule(Runnable task, int timeoutSeconds);
        @Override default void close() { }
    }

    private static final class SystemTimeoutScheduler implements TimeoutScheduler {
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
                runnable -> new Thread(runnable, "regression-mcp-timeout"));
        @Override public ScheduledFuture<?> schedule(Runnable task, int timeoutSeconds) {
            return executor.schedule(task, timeoutSeconds, TimeUnit.SECONDS);
        }
        @Override public void close() { executor.shutdownNow(); }
    }

    private static final class Active {
        private volatile RunSnapshot snapshot;
        private final ValidatedTestRunRequest request;
        private final MavenRuntimeConfiguration runtime;
        private final RunStore.Lock lock;
        private final AtomicReference<TestRunState> cause = new AtomicReference<>();
        private volatile Process process;
        private volatile ScheduledFuture<?> timeout;
        private volatile ScheduledFuture<?> observation;
        private volatile ProcessOwnershipTracker tracker;
        private Active(RunSnapshot snapshot, ValidatedTestRunRequest request, MavenRuntimeConfiguration runtime, RunStore.Lock lock) {
            this.snapshot = snapshot;
            this.request = request;
            this.runtime = runtime;
            this.lock = lock;
        }
    }
}
