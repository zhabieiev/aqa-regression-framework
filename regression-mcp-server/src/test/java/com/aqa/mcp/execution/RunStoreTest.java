package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

class RunStoreTest {
    @TempDir Path root;

    @Test
    void runMetadataIsImmutableAndStatusReplacementLeavesNoTemporaryFiles() throws Exception {
        RunStore store = new RunStore(root);
        RunSnapshot queued = snapshot(TestRunState.QUEUED);
        store.create(queued);
        Path directory = root.resolve(".regression-mcp/runs").resolve(queued.runId());
        String immutable = Files.readString(directory.resolve("run.json"));
        RunSnapshot passed = new RunSnapshot(queued.runId(), queued.module(), queued.environment(), queued.headless(), queued.tags(),
                queued.timeoutSeconds(), TestRunState.PASSED, queued.createdAt(), Instant.now(), Instant.now(), 0, "PASSED", 0, 0, false, false, null);

        store.update(passed, List.of());

        assertThat(Files.readString(directory.resolve("run.json"))).isEqualTo(immutable);
        assertThat(store.get(queued.runId()).state()).isEqualTo(TestRunState.PASSED);
        try (var files = Files.list(directory)) { assertThat(files.map(path -> path.getFileName().toString())).noneMatch(name -> name.endsWith(".tmp")); }
    }

    @Test
    void terminalSkippedTestsCountSurvivesAWriteReadRoundTripThroughStatusJson() throws Exception {
        RunStore store = new RunStore(root);
        RunSnapshot queued = snapshot(TestRunState.QUEUED);
        store.create(queued);
        RunSnapshot terminalWithCount = new RunSnapshot(queued.runId(), queued.module(), queued.environment(), queued.headless(), queued.tags(),
                queued.timeoutSeconds(), TestRunState.PASSED, queued.createdAt(), Instant.now(), Instant.now(), 0, "PASSED", 0, 0, false, false, 3);

        store.update(terminalWithCount, List.of());

        assertThat(store.get(queued.runId()).skippedTests()).isEqualTo(3);
        assertThat(store.persisted(queued.runId()).snapshot().skippedTests()).isEqualTo(3);
    }

    @Test
    void failedAtomicMoveFailsClosedAndCleansSameDirectoryTemporaryFile() throws Exception {
        RunStore initial = new RunStore(root);
        RunSnapshot queued = snapshot(TestRunState.QUEUED);
        initial.create(queued);
        RunStore failing = new RunStore(root, (temporary, target) -> { throw new IOException("simulated atomic move failure"); });

        RunSnapshot running = new RunSnapshot(queued.runId(), queued.module(), queued.environment(), queued.headless(), queued.tags(),
                queued.timeoutSeconds(), TestRunState.RUNNING, queued.createdAt(), Instant.now(), null, null, "RUNNING", 0, 0, false, false, null);
        assertThatThrownBy(() -> failing.update(running, List.of()))
                .isInstanceOf(ExecutionPlanningException.class)
                .extracting(error -> ((ExecutionPlanningException) error).code()).isEqualTo("MAVEN_RUNTIME_UNAVAILABLE");
        Path directory = root.resolve(".regression-mcp/runs").resolve(queued.runId());
        try (var files = Files.list(directory)) { assertThat(files.map(path -> path.getFileName().toString())).noneMatch(name -> name.endsWith(".tmp")); }
    }

    @Test
    void corruptedStateAndMalformedRunIdProduceStructuredFailures() throws Exception {
        RunStore store = new RunStore(root);
        RunSnapshot queued = snapshot(TestRunState.QUEUED);
        store.create(queued);
        Files.writeString(root.resolve(".regression-mcp/runs").resolve(queued.runId()).resolve("status.json"), "{");

        assertThatThrownBy(() -> store.get(queued.runId())).extracting(error -> ((ExecutionPlanningException) error).code())
                .isEqualTo("RUN_STATE_CORRUPT");
        assertThatThrownBy(() -> store.get("../foreign")).extracting(error -> ((ExecutionPlanningException) error).code())
                .isEqualTo("RUN_NOT_FOUND");
    }

    @Test
    void symlinkedStatusTargetIsRejectedWithoutFollowingIt() throws Exception {
        RunStore store = new RunStore(root); RunSnapshot queued = snapshot(TestRunState.QUEUED); store.create(queued);
        Path directory = root.resolve(".regression-mcp/runs").resolve(queued.runId());
        Path status = directory.resolve("status.json"); Path foreign = root.resolve("foreign.json");
        Files.writeString(foreign, "foreign"); Files.delete(status);
        try { Files.createSymbolicLink(status, foreign); }
        catch (UnsupportedOperationException | java.io.IOException exception) { Assumptions.assumeTrue(false, "Local account cannot create symbolic links."); return; }

        assertThatThrownBy(() -> store.update(queued, List.of())).extracting(error -> ((ExecutionPlanningException) error).code())
                .isEqualTo("MAVEN_RUNTIME_UNAVAILABLE");
        assertThat(Files.readString(foreign)).isEqualTo("foreign");
    }

    @Test
    void separateInstancesAtomicallyReplaceAndReadCompleteStatusRecordsConcurrently() throws Exception {
        RunStore writer = new RunStore(root);
        RunSnapshot queued = snapshot(TestRunState.QUEUED);
        writer.create(queued);
        RunStore reader = new RunStore(root);
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var write = workers.submit(() -> {
                await(start, failures);
                for (int iteration = 0; iteration < 1_000; iteration++) {
                    try { writer.update(replacement(queued, iteration % 2 == 0 ? TestRunState.RUNNING : TestRunState.QUEUED), List.of()); }
                    catch (Throwable failure) { failures.add(failure); return; }
                }
            });
            var read = workers.submit(() -> {
                await(start, failures);
                for (int iteration = 0; iteration < 1_000; iteration++) {
                    try {
                        RunStore.PersistedRun persisted = reader.persisted(queued.runId());
                        assertThat(persisted.schemaVersion()).isEqualTo(3);
                        assertThat(persisted.snapshot().runId()).isEqualTo(queued.runId());
                        assertThat(persisted.snapshot().state()).isIn(TestRunState.QUEUED, TestRunState.RUNNING);
                    } catch (Throwable failure) { failures.add(failure); return; }
                }
            });
            start.countDown();
            write.get(30, TimeUnit.SECONDS);
            read.get(30, TimeUnit.SECONDS);
        } finally { workers.shutdownNow(); }

        assertThat(failures).withFailMessage(() -> "concurrent filesystem failures: " + failures.stream()
                .map(RunStoreTest::describe).toList()).isEmpty();
    }

    @Test
    void transientAccessDeniedDuringAtomicReplacementIsBoundedAndThenSucceeds() {
        RunStore initial = new RunStore(root);
        RunSnapshot queued = snapshot(TestRunState.QUEUED);
        initial.create(queued);
        AtomicInteger attempts = new AtomicInteger();
        RunStore retrying = new RunStore(root, (temporary, target) -> {
            if (attempts.incrementAndGet() < 3) throw new java.nio.file.AccessDeniedException(temporary.toString(), target.toString(), "simulated sharing conflict");
            Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        });

        retrying.update(replacement(queued, TestRunState.RUNNING), List.of());

        assertThat(attempts).hasValue(3);
        assertThat(retrying.get(queued.runId()).state()).isEqualTo(TestRunState.RUNNING);
    }

    @Test
    void permanentAccessDeniedDuringAtomicReplacementFailsClosedAfterBoundedAttempts() {
        RunStore initial = new RunStore(root);
        RunSnapshot queued = snapshot(TestRunState.QUEUED);
        initial.create(queued);
        AtomicInteger attempts = new AtomicInteger();
        RunStore retrying = new RunStore(root, (temporary, target) -> {
            attempts.incrementAndGet();
            throw new java.nio.file.AccessDeniedException(temporary.toString(), target.toString(), "simulated sharing conflict");
        });

        assertThatThrownBy(() -> retrying.update(replacement(queued, TestRunState.RUNNING), List.of()))
                .isInstanceOf(ExecutionPlanningException.class)
                .extracting(error -> ((ExecutionPlanningException) error).code()).isEqualTo("MAVEN_RUNTIME_UNAVAILABLE");
        assertThat(attempts).hasValue(4);
    }

    private static void await(CountDownLatch latch, List<Throwable> failures) {
        try { latch.await(); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); failures.add(exception); }
    }

    private static RunSnapshot replacement(RunSnapshot source, TestRunState state) {
        Instant now = Instant.now();
        return new RunSnapshot(source.runId(), source.module(), source.environment(), source.headless(), source.tags(),
                source.timeoutSeconds(), state, source.createdAt(), state == TestRunState.QUEUED ? null : now, null,
                null, state.name(), 0, 0, false, false, null);
    }

    private static String describe(Throwable failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    private static RunSnapshot snapshot(TestRunState state) {
        String id = RunId.generate(); Instant now = Instant.now();
        return new RunSnapshot(id, ExecutionProfileRegistry.COMMERCE_MODULE, "dev", true, "not @wip", 30, state, now,
                state == TestRunState.QUEUED ? null : now, state.isTerminal() ? now : null, null, state.name(), 0, 0, false, false, null);
    }
}
