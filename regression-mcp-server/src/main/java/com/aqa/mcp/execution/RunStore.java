package com.aqa.mcp.execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Server-owned persistence below a normalized repository root. All status replacement is same-directory atomic. */
final class RunStore {
    private static final int STAGE_14_SCHEMA = 3;
    private static final int TRANSIENT_ACCESS_DENIED_ATTEMPTS = 4;
    private static final long TRANSIENT_ACCESS_DENIED_BACKOFF_NANOS = 2_000_000L;
    private static final Set<String> ALLOWED_ARTIFACT_MIME_TYPES = Set.of(
            "image/png", "image/jpeg", "text/plain", "application/json", "text/xml");
    /** Coordinates status readers and atomic replacers across all RunStore instances in this JVM. */
    private static final Object STATUS_IO = new Object();
    private static final SecureRandom NONCES = new SecureRandom();
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
            CaptureMetadata capture = CaptureMetadata.pending(nonce());
            prepareCaptureDirectories(layout(directory, capture));
            PersistedRun record = new PersistedRun(STAGE_14_SCHEMA, snapshot, List.of(), 0, 0, 0, 0, capture);
            writeNew(directory.resolve("run.json"), record);
            replace(directory.resolve("status.json"), record);
            Files.createFile(directory.resolve("stdout.log"));
            Files.createFile(directory.resolve("stderr.log"));
        } catch (IOException exception) { throw unavailable(exception); }
    }

    synchronized void update(RunSnapshot snapshot, List<OwnedProcessIdentity> identities) {
        try {
            PersistedRun current = persisted(snapshot.runId());
            replace(status(snapshot.runId()), new PersistedRun(current.schemaVersion(), snapshot, List.copyOf(identities), 0, 0, 0, 0,
                    current.capture()));
        }
        catch (IOException exception) { throw unavailable(exception); }
    }
    synchronized void update(RunSnapshot snapshot, List<OwnedProcessIdentity> identities, long stdoutObserved, long stdoutDropped,
            long stderrObserved, long stderrDropped) {
        try {
            PersistedRun current = persisted(snapshot.runId());
            replace(status(snapshot.runId()), new PersistedRun(current.schemaVersion(), snapshot, List.copyOf(identities), stdoutObserved,
                    stdoutDropped, stderrObserved, stderrDropped, current.capture()));
        }
        catch (IOException exception) { throw unavailable(exception); }
    }

    synchronized RunCaptureLayout captureLayout(String runId) {
        PersistedRun record = persisted(runId);
        if (record.capture() == null) throw new ExecutionPlanningException("CAPTURE_UNAVAILABLE", "This run predates report capture.");
        try { return layout(directory(runId), record.capture()); }
        catch (IOException exception) { throw unavailable(exception); }
    }

    synchronized void updateCapture(String runId, CaptureMetadata capture) {
        try {
            PersistedRun current = persisted(runId);
            if (current.schemaVersion() < STAGE_14_SCHEMA || current.capture() == null) {
                throw new ExecutionPlanningException("CAPTURE_UNAVAILABLE", "This run predates report capture.");
            }
            replace(status(runId), new PersistedRun(current.schemaVersion(), current.snapshot(), current.ownedProcesses(),
                    current.stdoutObservedBytes(), current.stdoutDroppedBytes(), current.stderrObservedBytes(), current.stderrDroppedBytes(), capture));
        } catch (IOException exception) { throw unavailable(exception); }
    }

    synchronized RunSnapshot get(String runId) {
        if (!RunId.valid(runId)) throw new ExecutionPlanningException("RUN_NOT_FOUND", "The requested run was not found.");
        return persisted(runId).snapshot();
    }

    synchronized PersistedRun persisted(String runId) {
        Path target = status(runId);
        synchronized (STATUS_IO) {
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new ExecutionPlanningException("RUN_NOT_FOUND", "The requested run was not found.");
            }
            try { return JSON.readValue(readStatus(target), PersistedRun.class); }
            catch (IOException | RuntimeException exception) {
                throw new ExecutionPlanningException("RUN_STATE_CORRUPT", "The persisted execution state cannot be read.", exception);
            }
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

    synchronized SurefireSummary summary(String runId) { return readSummary(runId, false); }

    synchronized SurefireSummary failureSummary(String runId) { return readSummary(runId, true); }

    private SurefireSummary readSummary(String runId, boolean requireFailureRecords) {
        if (!RunId.valid(runId)) throw new ExecutionPlanningException("INVALID_ARGUMENTS", "runId has an invalid format.");
        PersistedRun record = persisted(runId);
        if (!record.snapshot().terminal()) throw new ExecutionPlanningException("RUN_NOT_TERMINAL", "The requested run is not terminal.");
        if (record.capture() == null) throw new ExecutionPlanningException("NOT_FOUND", "PRE_STAGE14_RUN");
        CaptureMetadata.CaptureSet capture = record.capture().surefire();
        if (capture == null || !"AVAILABLE".equals(capture.status())) {
            if (capture != null && "MALFORMED".equals(capture.status())) throw new ExecutionPlanningException("REPORT_MALFORMED", "Surefire report is malformed.");
            throw new ExecutionPlanningException("NOT_FOUND", "SUREFIRE_UNAVAILABLE");
        }
        if (capture.indexSha256() == null || !capture.indexSha256().matches("[0-9a-f]{64}")) {
            throw new ExecutionPlanningException("REPORT_INDEX_CORRUPT", "The published report index is invalid.");
        }
        try {
            Path index = layout(directory(runId), record.capture()).surefireIndex();
            if (!Files.isRegularFile(index, LinkOption.NOFOLLOW_LINKS)) {
                throw new ExecutionPlanningException("NOT_FOUND", "SUREFIRE_UNAVAILABLE");
            }
            if (Files.isSymbolicLink(index) || !sha256(index).equals(capture.indexSha256())) throw corrupt();
            PublishedReportIndex parsed = JSON.readValue(Files.readString(index, StandardCharsets.UTF_8), PublishedReportIndex.class);
            if ((requireFailureRecords ? parsed.schemaVersion() != 3 : parsed.schemaVersion() != 2 && parsed.schemaVersion() != 3)
                    || !runId.equals(parsed.runId()) || !"surefire".equals(parsed.kind())
                    || parsed.summary() == null || !parsed.files().equals(capture.files())) throw corrupt();
            if (requireFailureRecords && parsed.summary().failureRecords().size() != parsed.summary().failures() + parsed.summary().errors()) {
                throw new ExecutionPlanningException("REPORT_MALFORMED", "Surefire failure details are malformed.");
            }
            return parsed.summary();
        } catch (ExecutionPlanningException exception) { throw exception; }
        catch (IOException | RuntimeException exception) { throw corrupt(); }
    }

    /**
     * Listing requires the run to be terminal, the same as {@link #readSummary}: terminal run state must never
     * expose partially published capture data. Absence of an available Allure capture set is a legitimate empty
     * result, not an error.
     */
    synchronized List<FailureArtifact> artifacts(String runId) {
        PersistedRun record = terminalRecordForArtifacts(runId);
        CaptureMetadata.CaptureSet allure = record.capture().allure();
        if (allure == null || !"AVAILABLE".equals(allure.status())) return List.of();
        Path allureRoot = artifactLayout(runId, record).allureFinal();
        List<CaptureMetadata.IndexedFile> files = verifiedAllureFiles(runId, record, allure);
        List<FailureArtifact> artifacts = new ArrayList<>();
        for (CaptureMetadata.IndexedFile file : files) {
            // Mirrors readArtifact()'s containment check: an index entry that resolves outside allureRoot or
            // through a symlink can only reach here via direct on-disk tampering, never a client. Omit it from
            // the listing rather than probing it or failing every other legitimate entry in the same run.
            Path resolved = allureRoot.resolve(file.path()).normalize();
            if (!resolved.startsWith(allureRoot) || Files.isSymbolicLink(resolved)) continue;
            artifacts.add(toArtifact(runId, file, allureRoot));
        }
        return List.copyOf(artifacts);
    }

    synchronized ArtifactContent readArtifact(String runId, String artifactId) {
        if (artifactId == null || !artifactId.matches("[0-9a-f]{32}")) {
            throw new ExecutionPlanningException("INVALID_ARGUMENTS", "artifactId has an invalid format.");
        }
        PersistedRun record = terminalRecordForArtifacts(runId);
        CaptureMetadata.CaptureSet allure = record.capture().allure();
        if (allure == null || !"AVAILABLE".equals(allure.status())) throw artifactNotFound();
        Path allureRoot = artifactLayout(runId, record).allureFinal();
        List<CaptureMetadata.IndexedFile> files = verifiedAllureFiles(runId, record, allure);
        for (CaptureMetadata.IndexedFile file : files) {
            if (!artifactId.equals(artifactId(runId, file.path()))) continue;
            Path candidate = allureRoot.resolve(file.path()).normalize();
            try {
                if (!candidate.startsWith(allureRoot) || Files.isSymbolicLink(candidate)
                        || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                        || Files.size(candidate) != file.size() || !sha256(candidate).equals(file.sha256())) {
                    throw corrupt();
                }
                String mimeType = mimeTypeOf(candidate, file.path());
                if (!ALLOWED_ARTIFACT_MIME_TYPES.contains(mimeType)) {
                    throw new ExecutionPlanningException("UNSUPPORTED_MIME_TYPE", "This artifact's media type cannot be served.");
                }
                byte[] content = Files.readAllBytes(candidate);
                return new ArtifactContent(toArtifact(runId, file, allureRoot), content);
            } catch (ExecutionPlanningException exception) { throw exception; }
            catch (IOException exception) { throw corrupt(); }
        }
        throw artifactNotFound();
    }

    private PersistedRun terminalRecordForArtifacts(String runId) {
        if (!RunId.valid(runId)) throw new ExecutionPlanningException("INVALID_ARGUMENTS", "runId has an invalid format.");
        PersistedRun record = persisted(runId);
        if (!record.snapshot().terminal()) throw new ExecutionPlanningException("RUN_NOT_TERMINAL", "The requested run is not terminal.");
        if (record.capture() == null) throw new ExecutionPlanningException("NOT_FOUND", "PRE_STAGE14_RUN");
        return record;
    }

    private RunCaptureLayout artifactLayout(String runId, PersistedRun record) {
        try { return layout(directory(runId), record.capture()); }
        catch (IOException exception) { throw unavailable(exception); }
    }

    private List<CaptureMetadata.IndexedFile> verifiedAllureFiles(String runId, PersistedRun record, CaptureMetadata.CaptureSet allure) {
        if (allure.indexSha256() == null || !allure.indexSha256().matches("[0-9a-f]{64}")) throw corrupt();
        try {
            Path index = artifactLayout(runId, record).allureIndex();
            if (!Files.isRegularFile(index, LinkOption.NOFOLLOW_LINKS)) throw artifactNotFound();
            if (Files.isSymbolicLink(index) || !sha256(index).equals(allure.indexSha256())) throw corrupt();
            PublishedReportIndex parsed = JSON.readValue(Files.readString(index, StandardCharsets.UTF_8), PublishedReportIndex.class);
            if (parsed.schemaVersion() != 2 || !runId.equals(parsed.runId()) || !"allure".equals(parsed.kind())
                    || !parsed.files().equals(allure.files())) throw corrupt();
            return parsed.files();
        } catch (ExecutionPlanningException exception) { throw exception; }
        catch (IOException | RuntimeException exception) { throw corrupt(); }
    }

    private static FailureArtifact toArtifact(String runId, CaptureMetadata.IndexedFile file, Path allureRoot) {
        String name = Path.of(file.path()).getFileName().toString();
        Path resolved = allureRoot.resolve(file.path()).normalize();
        String mimeType = mimeTypeOf(resolved, file.path());
        return new FailureArtifact(artifactId(runId, file.path()), name, mimeType, file.size(), file.path());
    }

    private static String artifactId(String runId, String relativePath) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((runId + ":" + relativePath).getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static String mimeTypeOf(Path resolvedFile, String relativePath) {
        try {
            if (resolvedFile != null && Files.isRegularFile(resolvedFile, LinkOption.NOFOLLOW_LINKS)) {
                String probed = Files.probeContentType(resolvedFile);
                if (probed != null) return probed;
            }
        } catch (IOException ignored) { }
        String lower = relativePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "text/xml";
        if (lower.endsWith(".txt") || lower.endsWith(".log")) return "text/plain";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        return "application/octet-stream";
    }

    private static ExecutionPlanningException artifactNotFound() {
        return new ExecutionPlanningException("NOT_FOUND", "ARTIFACT_NOT_FOUND");
    }

    private RunCaptureLayout layout(Path directory, CaptureMetadata capture) throws IOException {
        Path staging = contained(directory, "staging");
        Path reports = contained(directory, "reports");
        Path artifacts = contained(directory, "artifacts");
        Path surefireStaging = contained(staging, "surefire-" + capture.nonce());
        Path allureStaging = contained(staging, "allure-" + capture.nonce());
        Path surefireRoot = contained(reports, "surefire");
        Path allureRoot = contained(artifacts, "allure");
        return new RunCaptureLayout(directory, surefireStaging, allureStaging, contained(surefireRoot, "data"),
                contained(allureRoot, "data"), contained(surefireRoot, "index.json"), contained(allureRoot, "index.json"));
    }

    private void prepareCaptureDirectories(RunCaptureLayout layout) throws IOException {
        Files.createDirectories(layout.surefireStaging().getParent());
        Files.createDirectory(layout.surefireStaging());
        Files.createDirectory(layout.allureStaging());
        Files.createDirectories(layout.surefireIndex().getParent());
        Files.createDirectories(layout.allureIndex().getParent());
        verifyCapturePath(layout.runDirectory(), layout.surefireStaging());
        verifyCapturePath(layout.runDirectory(), layout.allureStaging());
    }

    private static Path contained(Path parent, String child) throws IOException {
        Path result = parent.resolve(child).normalize();
        if (!result.startsWith(parent) || Files.isSymbolicLink(result)) throw new IOException("Capture path is outside its run directory.");
        return result;
    }

    private static void verifyCapturePath(Path runDirectory, Path path) throws IOException {
        if (!path.toRealPath().startsWith(runDirectory.toRealPath()) || Files.isSymbolicLink(path)) {
            throw new IOException("Capture path is outside its run directory.");
        }
    }

    private static String nonce() {
        byte[] bytes = new byte[16];
        NONCES.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] bytes = new byte[8192]; int read;
            while ((read = input.read(bytes)) >= 0) digest.update(bytes, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static ExecutionPlanningException corrupt() { return new ExecutionPlanningException("REPORT_INDEX_CORRUPT", "The published report index is invalid."); }

    private void replace(Path target, PersistedRun value) throws IOException {
        synchronized (STATUS_IO) {
            rejectSymlink(target);
            Path directory = target.getParent();
            Path temporary = Files.createTempFile(directory, target.getFileName() + ".", ".tmp");
            try {
                rejectSymlink(temporary);
                Files.writeString(temporary, JSON.writeValueAsString(value), StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
                moveAtomically(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static String readStatus(Path target) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try { return Files.readString(target, StandardCharsets.UTF_8); }
            catch (AccessDeniedException exception) {
                if (attempt >= TRANSIENT_ACCESS_DENIED_ATTEMPTS) throw exception;
                LockSupport.parkNanos(TRANSIENT_ACCESS_DENIED_BACKOFF_NANOS);
            }
        }
    }

    private void moveAtomically(Path temporary, Path target) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try { mover.move(temporary, target); return; }
            catch (AccessDeniedException exception) {
                if (attempt >= TRANSIENT_ACCESS_DENIED_ATTEMPTS) throw exception;
                LockSupport.parkNanos(TRANSIENT_ACCESS_DENIED_BACKOFF_NANOS);
            }
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
            long stdoutDroppedBytes, long stderrObservedBytes, long stderrDroppedBytes, CaptureMetadata capture) {
        PersistedRun { ownedProcesses = List.copyOf(ownedProcesses == null ? List.of() : ownedProcesses); }
    }
}
