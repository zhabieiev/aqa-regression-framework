package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FailureArtifactStoreTest {
    @TempDir Path root;

    @Test
    void listsAndReadsPublishedArtifactsForATerminalRun() throws Exception {
        Fixture fixture = fixture(true);

        List<FailureArtifact> artifacts = fixture.store.artifacts(fixture.snapshot.runId());

        assertThat(artifacts).hasSize(2);
        assertThat(artifacts).extracting(FailureArtifact::relativePath).containsExactlyInAnyOrder("one-result.json", "failure-screenshot.png");
        FailureArtifact screenshot = artifacts.stream().filter(artifact -> artifact.relativePath().equals("failure-screenshot.png")).findFirst().orElseThrow();
        assertThat(screenshot.artifactId()).matches("[0-9a-f]{32}");
        assertThat(screenshot.mimeType()).isEqualTo("image/png");
        assertThat(screenshot.name()).isEqualTo("failure-screenshot.png");
        assertThat(screenshot.size()).isGreaterThan(0);

        ArtifactContent content = fixture.store.readArtifact(fixture.snapshot.runId(), screenshot.artifactId());
        assertThat(content.metadata()).isEqualTo(screenshot);
        assertThat(content.content()).isEqualTo(Files.readAllBytes(root.resolve("source-screenshot.png")));
    }

    @Test
    void artifactsIsEmptyNotNotFoundWhenAllureCaptureIsUnavailableButSurefireIsPublished() throws Exception {
        Fixture fixture = fixture(false);

        assertThat(fixture.store.artifacts(fixture.snapshot.runId())).isEmpty();
        assertThatThrownBy(() -> fixture.store.readArtifact(fixture.snapshot.runId(), "0".repeat(32)))
                .isInstanceOf(ExecutionPlanningException.class).extracting(error -> ((ExecutionPlanningException) error).code()).isEqualTo("NOT_FOUND");
    }

    @Test
    void artifactsAndReadArtifactRequireATerminalAndKnownRun() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        RunStore store = new RunStore(root);
        RunSnapshot queued = snapshot(TestRunState.QUEUED);
        store.create(queued);

        code("RUN_NOT_TERMINAL", () -> store.artifacts(queued.runId()));
        code("RUN_NOT_TERMINAL", () -> store.readArtifact(queued.runId(), "0".repeat(32)));
        code("RUN_NOT_FOUND", () -> store.artifacts("run-00000000000000000000000000000000"));
        code("INVALID_ARGUMENTS", () -> store.artifacts("not-a-run-id"));
    }

    @Test
    void readArtifactRejectsForeignAndMalformedArtifactIds() throws Exception {
        Fixture fixture = fixture(true);
        Fixture foreign = fixture(true);

        code("INVALID_ARGUMENTS", () -> fixture.store.readArtifact(fixture.snapshot.runId(), "not-32-hex"));
        code("NOT_FOUND", () -> fixture.store.readArtifact(fixture.snapshot.runId(), "0".repeat(32)));
        List<FailureArtifact> foreignArtifacts = foreign.store.artifacts(foreign.snapshot.runId());
        String foreignArtifactId = foreignArtifacts.getFirst().artifactId();
        code("NOT_FOUND", () -> fixture.store.readArtifact(fixture.snapshot.runId(), foreignArtifactId));
    }

    @Test
    void artifactsAndReadArtifactSucceedForEveryTerminalOutcomeStateNotJustFailed() throws Exception {
        for (TestRunState state : List.of(TestRunState.CANCELLED, TestRunState.TIMED_OUT, TestRunState.ERROR)) {
            Fixture fixture = fixture(true, state);

            List<FailureArtifact> artifacts = fixture.store.artifacts(fixture.snapshot.runId());

            assertThat(artifacts).as("terminal state %s must not be rejected as non-terminal", state).hasSize(2);
            FailureArtifact screenshot = artifacts.stream().filter(artifact -> artifact.relativePath().equals("failure-screenshot.png")).findFirst().orElseThrow();
            ArtifactContent content = fixture.store.readArtifact(fixture.snapshot.runId(), screenshot.artifactId());
            assertThat(content.content()).as("terminal state %s must not be rejected as non-terminal", state)
                    .isEqualTo(Files.readAllBytes(root.resolve("source-screenshot.png")));
        }
    }

    @Test
    void readArtifactRejectsMimeTypesOutsideTheAllowListButStillListsThem() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        RunStore store = new RunStore(root);
        RunSnapshot snapshot = snapshot(TestRunState.QUEUED);
        store.create(snapshot);
        RunCaptureLayout layout = store.captureLayout(snapshot.runId());
        Files.writeString(layout.surefireStaging().resolve("TEST-one.xml"), failingSurefire());
        Files.writeString(layout.allureStaging().resolve("one-result.json"),
                "{\"name\":\"one#fails\",\"status\":\"failed\",\"attachments\":[{\"source\":\"page.html\"}]}");
        Files.writeString(layout.allureStaging().resolve("page.html"), "<html><body>page source</body></html>");
        store.updateCapture(snapshot.runId(), new ReportCapture().capture(layout, store.persisted(snapshot.runId()).capture()).metadata());
        store.update(terminal(snapshot), List.of());

        List<FailureArtifact> artifacts = store.artifacts(snapshot.runId());
        assertThat(artifacts).extracting(FailureArtifact::relativePath).contains("page.html");
        FailureArtifact page = artifacts.stream().filter(artifact -> artifact.relativePath().equals("page.html")).findFirst().orElseThrow();
        assertThat(page.mimeType()).isEqualTo("text/html");

        code("UNSUPPORTED_MIME_TYPE", () -> store.readArtifact(snapshot.runId(), page.artifactId()));
    }

    /** Simulates a corrupted/tampered published index containing a traversal-shaped relativePath (never possible
     * from a client, which only ever supplies an opaque artifactId; this proves the defense-in-depth containment
     * check that must hold if that upstream ReportCapture validation were ever bypassed). Both the index.json file
     * and its sibling status.json CaptureSet.files() must agree, or verifiedAllureFiles()'s own mismatch check
     * rejects the tampering before the containment check under test is ever reached. */
    @Test
    void readArtifactRejectsATraversalShapedRelativePathHandCraftedIntoAPublishedIndex() throws Exception {
        Fixture fixture = fixture(true);
        Path outside = root.resolve("outside-secret.txt");
        Files.writeString(outside, "must never be disclosed");
        RunCaptureLayout layout = fixture.store.captureLayout(fixture.snapshot.runId());
        Path allureRoot = layout.allureFinal();
        int depthBelowRoot = allureRoot.getNameCount() - root.getNameCount();
        String maliciousRelativePath = "../".repeat(depthBelowRoot) + "outside-secret.txt";
        Path outsideReal = outside.toRealPath();
        Path resolvedReal = allureRoot.resolve(maliciousRelativePath).normalize().toRealPath();
        assertThat(resolvedReal).as("fixture must actually escape allureRoot").isEqualTo(outsideReal);

        CaptureMetadata.IndexedFile maliciousEntry = new CaptureMetadata.IndexedFile(maliciousRelativePath, Files.size(outside), sha256Hex(outside));
        CaptureMetadata capture = fixture.store.persisted(fixture.snapshot.runId()).capture();
        List<CaptureMetadata.IndexedFile> taintedFiles = new java.util.ArrayList<>(capture.allure().files());
        taintedFiles.add(maliciousEntry);

        com.fasterxml.jackson.databind.json.JsonMapper json = com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
        PublishedReportIndex taintedIndex = new PublishedReportIndex(2, fixture.snapshot.runId(), "allure", taintedFiles, null);
        Files.writeString(layout.allureIndex(), json.writeValueAsString(taintedIndex));
        CaptureMetadata.CaptureSet taintedCaptureSet = new CaptureMetadata.CaptureSet("AVAILABLE", taintedFiles, sha256Hex(layout.allureIndex()));
        fixture.store.updateCapture(fixture.snapshot.runId(),
                new CaptureMetadata(capture.schemaVersion(), capture.status(), capture.nonce(), capture.surefire(), taintedCaptureSet));

        List<FailureArtifact> artifacts = fixture.store.artifacts(fixture.snapshot.runId());
        assertThat(artifacts).as("the tampered entry must be omitted from the listing, not probed or returned")
                .extracting(FailureArtifact::relativePath).doesNotContain(maliciousRelativePath);
        assertThat(artifacts).as("the two legitimate entries must remain unaffected by the tampered third entry").hasSize(2);

        String maliciousArtifactId = artifactIdFor(fixture.snapshot.runId(), maliciousRelativePath);
        code("REPORT_INDEX_CORRUPT", () -> fixture.store.readArtifact(fixture.snapshot.runId(), maliciousArtifactId));
        assertThat(Files.readString(outside)).isEqualTo("must never be disclosed");
    }

    private static String sha256Hex(Path file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(file));
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    /** Independently reproduces RunStore's own private artifactId scheme (SHA-256(runId:relativePath), 32 hex
     * chars) purely so this test can address the tampered entry once artifacts() correctly stops listing it. */
    private static String artifactIdFor(String runId, String relativePath) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        digest.update((runId + ":" + relativePath).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest.digest()).substring(0, 32);
    }

    private void code(String expected, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ExecutionPlanningException.class).extracting(error -> ((ExecutionPlanningException) error).code()).isEqualTo(expected);
    }

    private static String failingSurefire() {
        return "<testsuite name='one' tests='1' failures='1' errors='0' skipped='0' time='0.1'><testcase classname='one' name='fails' time='0.1'>"
                + "<failure message='bad'>stack</failure></testcase></testsuite>";
    }

    private Fixture fixture(boolean withAllure) throws Exception { return fixture(withAllure, TestRunState.FAILED); }

    private Fixture fixture(boolean withAllure, TestRunState terminalState) throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        RunStore store = new RunStore(root);
        RunSnapshot snapshot = snapshot(TestRunState.QUEUED);
        store.create(snapshot);
        RunCaptureLayout layout = store.captureLayout(snapshot.runId());
        Files.writeString(layout.surefireStaging().resolve("TEST-one.xml"), failingSurefire());
        if (withAllure) {
            byte[] png = MINIMAL_PNG;
            Files.write(root.resolve("source-screenshot.png"), png);
            Files.writeString(layout.allureStaging().resolve("one-result.json"),
                    "{\"name\":\"one#fails\",\"status\":\"failed\",\"attachments\":[{\"source\":\"failure-screenshot.png\"}]}");
            Files.write(layout.allureStaging().resolve("failure-screenshot.png"), png);
        }
        store.updateCapture(snapshot.runId(), new ReportCapture().capture(layout, store.persisted(snapshot.runId()).capture()).metadata());
        store.update(terminal(snapshot, terminalState), List.of());
        return new Fixture(store, snapshot);
    }

    private static final byte[] MINIMAL_PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    private static RunSnapshot snapshot(TestRunState state) {
        Instant now = Instant.now();
        return new RunSnapshot(RunId.generate(), ExecutionProfileRegistry.COMMERCE_MODULE, "dev", true, "not @wip", 30,
                state, now, state == TestRunState.QUEUED ? null : now, state.isTerminal() ? now : null, 0, state.name(), 0, 0, false, false, null);
    }

    private static RunSnapshot terminal(RunSnapshot source) { return terminal(source, TestRunState.FAILED); }

    private static RunSnapshot terminal(RunSnapshot source, TestRunState state) {
        Instant now = Instant.now();
        return new RunSnapshot(source.runId(), source.module(), source.environment(), source.headless(), source.tags(), source.timeoutSeconds(),
                state, source.createdAt(), now, now, 1, state.name(), 0, 0, false, false, null);
    }

    private record Fixture(RunStore store, RunSnapshot snapshot) { }
}
