package com.aqa.mcp.execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Server-owned persistence below a normalized repository root. All status replacement is same-directory atomic. */
final class RunStore {
    private static final JsonMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final Path root;
    private final Path runs;
    private final AtomicMover mover;

    RunStore(Path repositoryRoot) { this(repositoryRoot, (temporary, target) -> Files.move(temporary, target,
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)); }

    RunStore(Path repositoryRoot, AtomicMover mover) {
        try {
            root = repositoryRoot.toRealPath();
            runs = root.resolve(".regression-mcp").resolve("runs").normalize();
            if (!runs.startsWith(root)) throw unavailable();
            this.mover = mover;
        } catch (IOException exception) { throw unavailable(exception); }
    }

    synchronized void create(RunSnapshot snapshot) {
        try {
            Files.createDirectories(runs);
            Path directory = directory(snapshot.runId());
            Files.createDirectory(directory);
            writeNew(directory.resolve("run.json"), new PersistedRun(2, snapshot, List.of(), 0, 0, 0, 0));
            replace(directory.resolve("status.json"), new PersistedRun(2, snapshot, List.of(), 0, 0, 0, 0));
            Files.createFile(directory.resolve("stdout.log"));
            Files.createFile(directory.resolve("stderr.log"));
        } catch (IOException exception) { throw unavailable(exception); }
    }

    synchronized void update(RunSnapshot snapshot, List<OwnedProcessIdentity> identities) {
        try { replace(status(snapshot.runId()), new PersistedRun(2, snapshot, List.copyOf(identities), 0, 0, 0, 0)); }
        catch (IOException exception) { throw unavailable(exception); }
    }
    synchronized void update(RunSnapshot snapshot, List<OwnedProcessIdentity> identities, long stdoutObserved, long stdoutDropped,
            long stderrObserved, long stderrDropped) {
        try { replace(status(snapshot.runId()), new PersistedRun(2, snapshot, List.copyOf(identities), stdoutObserved, stdoutDropped,
                stderrObserved, stderrDropped)); }
        catch (IOException exception) { throw unavailable(exception); }
    }

    synchronized RunSnapshot get(String runId) {
        if (!RunId.valid(runId)) throw new ExecutionPlanningException("RUN_NOT_FOUND", "The requested run was not found.");
        return persisted(runId).snapshot();
    }

    synchronized PersistedRun persisted(String runId) {
        Path target = status(runId);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new ExecutionPlanningException("RUN_NOT_FOUND", "The requested run was not found.");
        }
        try { return JSON.readValue(Files.readString(target, StandardCharsets.UTF_8), PersistedRun.class); }
        catch (IOException | RuntimeException exception) {
            throw new ExecutionPlanningException("RUN_STATE_CORRUPT", "The persisted execution state cannot be read.", exception);
        }
    }

    synchronized List<PersistedRun> active() {
        if (!Files.isDirectory(runs, LinkOption.NOFOLLOW_LINKS)) return List.of();
        try (var children = Files.list(runs)) {
            return children.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> RunId.valid(path.getFileName().toString())).map(path -> persisted(path.getFileName().toString()))
                    .filter(record -> !record.snapshot().terminal()).toList();
        } catch (IOException exception) { throw unavailable(exception); }
    }

    boolean exists() { return Files.isDirectory(runs, LinkOption.NOFOLLOW_LINKS); }
    Path log(String id, boolean error) { return directory(id).resolve(error ? "stderr.log" : "stdout.log"); }

    Lock acquireActiveLock() {
        try {
            Files.createDirectories(runs);
            Path lockPath = runs.resolve("active.lock");
            rejectSymlink(lockPath);
            FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException exception) { channel.close(); throw activeError(); }
            if (lock == null) { channel.close(); throw activeError(); }
            return new Lock(channel, lock);
        } catch (ExecutionPlanningException exception) { throw exception; }
        catch (IOException exception) { throw activeError(); }
    }

    private Path directory(String id) {
        if (!RunId.valid(id)) throw new ExecutionPlanningException("RUN_NOT_FOUND", "The requested run was not found.");
        Path result = runs.resolve(id).normalize();
        if (!result.startsWith(runs) || result.getNameCount() != runs.getNameCount() + 1 || Files.isSymbolicLink(result)) {
            throw new ExecutionPlanningException("RUN_NOT_FOUND", "The requested run was not found.");
        }
        return result;
    }

    private Path status(String id) {
        Path target = directory(id).resolve("status.json");
        try { rejectSymlink(target); return target; }
        catch (IOException exception) { throw unavailable(exception); }
    }

    private void writeNew(Path target, PersistedRun value) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Immutable run metadata already exists.");
        replace(target, value);
    }

    private void replace(Path target, PersistedRun value) throws IOException {
        rejectSymlink(target);
        Path directory = target.getParent();
        Path temporary = Files.createTempFile(directory, target.getFileName() + ".", ".tmp");
        try {
            rejectSymlink(temporary);
            Files.writeString(temporary, JSON.writeValueAsString(value), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
            mover.move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void rejectSymlink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) throw new IOException("Symbolic links are not accepted for execution state.");
    }

    private static ExecutionPlanningException unavailable() { return unavailable(null); }
    private static ExecutionPlanningException unavailable(Throwable cause) {
        return new ExecutionPlanningException("MAVEN_RUNTIME_UNAVAILABLE", "Execution state storage is unavailable.", cause);
    }
    private static ExecutionPlanningException activeError() {
        return new ExecutionPlanningException("RUN_ALREADY_ACTIVE", "A test run is already active.");
    }

    @FunctionalInterface interface AtomicMover { void move(Path temporary, Path target) throws IOException; }
    static final class Lock implements AutoCloseable {
        private final FileChannel channel; private final FileLock lock;
        private Lock(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }
        @Override public void close() { try { lock.release(); } catch (IOException ignored) { } try { channel.close(); } catch (IOException ignored) { } }
    }
    record PersistedRun(int schemaVersion, RunSnapshot snapshot, List<OwnedProcessIdentity> ownedProcesses, long stdoutObservedBytes,
            long stdoutDroppedBytes, long stderrObservedBytes, long stderrDroppedBytes) {
        PersistedRun { ownedProcesses = List.copyOf(ownedProcesses == null ? List.of() : ownedProcesses); }
    }
}
