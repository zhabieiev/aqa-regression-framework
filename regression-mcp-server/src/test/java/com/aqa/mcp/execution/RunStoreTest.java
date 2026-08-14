package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

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
                queued.timeoutSeconds(), TestRunState.PASSED, queued.createdAt(), Instant.now(), Instant.now(), 0, "PASSED", 0, 0, false, false);

        store.update(passed, List.of());

        assertThat(Files.readString(directory.resolve("run.json"))).isEqualTo(immutable);
        assertThat(store.get(queued.runId()).state()).isEqualTo(TestRunState.PASSED);
        try (var files = Files.list(directory)) { assertThat(files.map(path -> path.getFileName().toString())).noneMatch(name -> name.endsWith(".tmp")); }
    }

    @Test
    void failedAtomicMoveFailsClosedAndCleansSameDirectoryTemporaryFile() throws Exception {
        RunStore initial = new RunStore(root);
        RunSnapshot queued = snapshot(TestRunState.QUEUED);
        initial.create(queued);
        RunStore failing = new RunStore(root, (temporary, target) -> { throw new IOException("simulated atomic move failure"); });

        RunSnapshot running = new RunSnapshot(queued.runId(), queued.module(), queued.environment(), queued.headless(), queued.tags(),
                queued.timeoutSeconds(), TestRunState.RUNNING, queued.createdAt(), Instant.now(), null, null, "RUNNING", 0, 0, false, false);
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

    private static RunSnapshot snapshot(TestRunState state) {
        String id = RunId.generate(); Instant now = Instant.now();
        return new RunSnapshot(id, ExecutionProfileRegistry.COMMERCE_MODULE, "dev", true, "not @wip", 30, state, now,
                state == TestRunState.QUEUED ? null : now, state.isTerminal() ? now : null, null, state.name(), 0, 0, false, false);
    }
}
