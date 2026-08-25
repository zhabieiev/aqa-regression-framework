package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportCaptureTest {
    @TempDir Path root;

    @Test
    void completeSurefireWithUnavailableOptionalAllureIsNarrowlyPartialAndPublishesOnlyAnIndexedSet() throws Exception {
        Fixture fixture = fixture();
        Files.writeString(fixture.layout.surefireStaging().resolve("TEST-one.xml"), validSurefire());

        CaptureMetadata capture = new ReportCapture().capture(fixture.layout, fixture.pending).metadata();

        assertThat(capture.status()).isEqualTo(CaptureStatus.PARTIAL);
        assertThat(capture.surefire().files()).hasSize(1);
        assertThat(capture.allure()).isNull();
        assertThat(fixture.layout.surefireIndex()).isRegularFile();
        assertThat(fixture.layout.surefireFinal().resolve("TEST-one.xml")).isRegularFile();
        assertThat(fixture.layout.allureIndex()).doesNotExist();
        assertThat(fixture.layout.surefireStaging()).doesNotExist();
        assertThat(fixture.layout.allureStaging()).doesNotExist();
        assertThat(capture.surefire().files().getFirst().path()).doesNotContain(root.toString()).isEqualTo("TEST-one.xml");
    }

    @Test
    void requiredSurefireIsNeverPublishedWhenAbsentMalformedOrXxe() throws Exception {
        for (String content : List.of("<not-closed>", "<!DOCTYPE x [ <!ENTITY leak SYSTEM 'file:///etc/passwd'> ]><testsuite>&leak;</testsuite>")) {
            Fixture fixture = fixture();
            Files.writeString(fixture.layout.surefireStaging().resolve("TEST-bad.xml"), content);

            CaptureMetadata capture = new ReportCapture().capture(fixture.layout, fixture.pending).metadata();

            assertThat(capture.status()).isEqualTo(CaptureStatus.UNAVAILABLE);
            assertThat(fixture.layout.surefireIndex()).doesNotExist();
            assertThat(fixture.layout.surefireFinal()).doesNotExist();
        }
        Fixture absent = fixture();
        assertThat(new ReportCapture().capture(absent.layout, absent.pending).metadata().status()).isEqualTo(CaptureStatus.UNAVAILABLE);
        assertThat(absent.layout.surefireIndex()).doesNotExist();
    }

    @Test
    void rejectsSurefireDepthCountAndSizeLimitsWithoutPublishingAnIndex() throws Exception {
        Fixture depth = fixture();
        Path nested = depth.layout.surefireStaging();
        for (int index = 0; index <= ReportCapture.MAX_DEPTH; index++) nested = Files.createDirectory(nested.resolve("d" + index));
        Files.writeString(nested.resolve("TEST-deep.xml"), "<testsuite/>");
        assertUnavailable(depth);

        Fixture count = fixture();
        for (int index = 0; index <= ReportCapture.MAX_FILES; index++) {
            Files.writeString(count.layout.surefireStaging().resolve("f" + index + ".txt"), "x");
        }
        assertUnavailable(count);

        Fixture size = fixture();
        Files.write(size.layout.surefireStaging().resolve("TEST-large.xml"), new byte[(int) ReportCapture.MAX_FILE_BYTES + 1]);
        assertUnavailable(size);

        Fixture aggregate = fixture();
        for (int index = 0; index < 8; index++) {
            Files.write(aggregate.layout.surefireStaging().resolve("part" + index + ".txt"), new byte[(int) ReportCapture.MAX_FILE_BYTES]);
        }
        Files.writeString(aggregate.layout.surefireStaging().resolve("overflow.txt"), "x");
        assertUnavailable(aggregate);
    }

    @Test
    void rejectsSymlinkEscapesAndCleansOwnedStaging() throws Exception {
        Fixture fixture = fixture();
        Path foreign = Files.writeString(root.resolve("foreign.xml"), validSurefire());
        try { Files.createSymbolicLink(fixture.layout.surefireStaging().resolve("TEST-link.xml"), foreign); }
        catch (UnsupportedOperationException | IOException exception) { Assumptions.assumeTrue(false, "Local account cannot create symbolic links."); return; }

        assertUnavailable(fixture);
        assertThat(Files.readString(foreign)).isEqualTo(validSurefire());
        assertThat(fixture.layout.surefireStaging()).doesNotExist();
    }

    @Test
    void validatesOptionalAllureJsonAndMarksOnlyOptionalFailuresPartial() throws Exception {
        Fixture valid = fixture();
        writeSurefire(valid); Files.writeString(valid.layout.allureStaging().resolve("one-result.json"), "{\"name\":\"one#fails\",\"status\":\"failed\"}");
        CaptureMetadata complete = new ReportCapture().capture(valid.layout, valid.pending).metadata();
        assertThat(complete.status()).isEqualTo(CaptureStatus.COMPLETE);
        assertThat(valid.layout.allureIndex()).isRegularFile();

        Fixture malformed = fixture();
        writeSurefire(malformed); Files.writeString(malformed.layout.allureStaging().resolve("result.json"), "{");
        CaptureMetadata partial = new ReportCapture().capture(malformed.layout, malformed.pending).metadata();
        assertThat(partial.status()).isEqualTo(CaptureStatus.PARTIAL);
        assertThat(malformed.layout.allureIndex()).doesNotExist();

        Fixture oversized = fixture();
        writeSurefire(oversized); Files.write(oversized.layout.allureStaging().resolve("result.json"), new byte[(int) ReportCapture.MAX_FILE_BYTES + 1]);
        assertThat(new ReportCapture().capture(oversized.layout, oversized.pending).metadata().status()).isEqualTo(CaptureStatus.PARTIAL);
        assertThat(oversized.layout.allureIndex()).doesNotExist();
    }

    @Test
    void persistsOnlyUnambiguousAllureEnrichmentAlongsideAuthoritativeSurefireFailure() throws Exception {
        Fixture fixture = fixture();
        Files.writeString(fixture.layout.surefireStaging().resolve("TEST-one.xml"), failingSurefire());
        Files.writeString(fixture.layout.allureStaging().resolve("one-result.json"), "{\"name\":\"one#fails\",\"status\":\"failed\",\"statusDetails\":{\"message\":\"detail\"},\"steps\":[{\"name\":\"step\",\"status\":\"failed\"}],\"attachments\":[{\"source\":\"private.bin\"}]}");

        fixture.store.updateCapture(fixture.snapshot.runId(), new ReportCapture().capture(fixture.layout, fixture.pending).metadata());
        fixture.store.update(terminal(fixture.snapshot), List.of());

        SurefireSummary summary = fixture.store.failureSummary(fixture.snapshot.runId());
        assertThat(summary.failures()).isEqualTo(1); assertThat(summary.failureRecords()).hasSize(1);
        assertThat(summary.allureAvailability()).isEqualTo("AVAILABLE");
        assertThat(summary.failureRecords().getFirst().allure().scenario()).isEqualTo("one#fails");
        assertThat(summary.failureRecords().getFirst().allure().attachmentsPresent()).isTrue();
        assertThat(summary.failureRecords().getFirst().allure().toString()).doesNotContain("private.bin");
    }

    @Test
    void ambiguousOrConflictingAllureDoesNotEnrichOrChangeSurefireClassification() throws Exception {
        Fixture fixture = fixture(); Files.writeString(fixture.layout.surefireStaging().resolve("TEST-one.xml"), failingSurefire());
        Files.writeString(fixture.layout.allureStaging().resolve("one-result.json"), "{\"name\":\"one#fails\",\"status\":\"failed\"}");
        Files.writeString(fixture.layout.allureStaging().resolve("two-result.json"), "{\"name\":\"one#fails\",\"status\":\"failed\"}");
        fixture.store.updateCapture(fixture.snapshot.runId(), new ReportCapture().capture(fixture.layout, fixture.pending).metadata()); fixture.store.update(terminal(fixture.snapshot), List.of());

        SurefireSummary.FailureRecord record = fixture.store.failureSummary(fixture.snapshot.runId()).failureRecords().getFirst();
        assertThat(record.type()).isEqualTo("FAILURE"); assertThat(record.allure().availability()).isEqualTo("UNMATCHED");
        assertThat(record.allure().scenario()).isNull();
    }

    @Test
    void atomicMoveFailureFailsClosedAndLeavesNoTemporaryOrPublishedIndex() throws Exception {
        Fixture fixture = fixture(); writeSurefire(fixture);
        ReportCapture capture = new ReportCapture((source, target) -> { throw new IOException("simulated no atomic move"); });

        assertThat(capture.capture(fixture.layout, fixture.pending).metadata().status()).isEqualTo(CaptureStatus.UNAVAILABLE);
        assertThat(fixture.layout.surefireIndex()).doesNotExist();
        assertThat(fixture.layout.surefireFinal()).doesNotExist();
        try (var paths = Files.walk(fixture.layout.runDirectory())) {
            assertThat(paths.map(path -> path.getFileName().toString())).noneMatch(name -> name.endsWith(".tmp"));
        }
    }

    @Test
    void executionRecordsRemainReadableAndAreNotUpgradedWhenTheirStatusChanges() throws Exception {
        RunStore store = new RunStore(root); RunSnapshot snapshot = snapshot();
        Path run = root.resolve(".regression-mcp/runs").resolve(snapshot.runId()); Files.createDirectories(run);
        String old = "{\"schemaVersion\":2,\"snapshot\":{\"runId\":\"" + snapshot.runId() + "\",\"module\":\"regression-nextjs-commerce\",\"environment\":\"dev\",\"headless\":true,\"tags\":\"not @wip\",\"timeoutSeconds\":30,\"state\":\"QUEUED\",\"createdAt\":\"2026-01-01T00:00:00Z\",\"startedAt\":null,\"finishedAt\":null,\"exitCode\":null,\"reason\":\"QUEUED\",\"stdoutBytes\":0,\"stderrBytes\":0,\"stdoutTruncated\":false,\"stderrTruncated\":false},\"ownedProcesses\":[],\"stdoutObservedBytes\":0,\"stdoutDroppedBytes\":0,\"stderrObservedBytes\":0,\"stderrDroppedBytes\":0}";
        Files.writeString(run.resolve("status.json"), old); Files.writeString(run.resolve("run.json"), old);

        store.update(snapshot, List.of());

        assertThat(store.persisted(snapshot.runId()).schemaVersion()).isEqualTo(2);
        assertThat(store.persisted(snapshot.runId()).capture()).isNull();
    }

    private void assertUnavailable(Fixture fixture) {
        assertThat(new ReportCapture().capture(fixture.layout, fixture.pending).metadata().status()).isEqualTo(CaptureStatus.UNAVAILABLE);
        assertThat(fixture.layout.surefireIndex()).doesNotExist();
        assertThat(fixture.layout.surefireFinal()).doesNotExist();
    }
    private static void writeSurefire(Fixture fixture) throws IOException { Files.writeString(fixture.layout.surefireStaging().resolve("TEST-one.xml"), validSurefire()); }
    private static String validSurefire() { return "<testsuite name='one' tests='1' failures='0' errors='0' skipped='0' time='0.1'><testcase classname='one' name='passes' time='0.1'/></testsuite>"; }
    private static String failingSurefire() { return "<testsuite name='one' tests='1' failures='1' errors='0' skipped='0' time='0.1'><testcase classname='one' name='fails' time='0.1'><failure message='bad'>stack</failure></testcase></testsuite>"; }
    private Fixture fixture() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        RunStore store = new RunStore(root); RunSnapshot snapshot = snapshot(); store.create(snapshot);
        CaptureMetadata pending = store.persisted(snapshot.runId()).capture();
        return new Fixture(store, snapshot, store.captureLayout(snapshot.runId()), pending);
    }
    private static RunSnapshot snapshot() {
        java.time.Instant now = java.time.Instant.now();
        return new RunSnapshot(RunId.generate(), ExecutionProfileRegistry.COMMERCE_MODULE, "dev", true, "not @wip", 30,
                TestRunState.QUEUED, now, null, null, null, "QUEUED", 0, 0, false, false, null);
    }
    private static RunSnapshot terminal(RunSnapshot source) { java.time.Instant now = java.time.Instant.now(); return new RunSnapshot(source.runId(), source.module(), source.environment(), source.headless(), source.tags(), source.timeoutSeconds(), TestRunState.FAILED, source.createdAt(), now, now, 1, "FAILED", 0, 0, false, false, null); }
    private record Fixture(RunStore store, RunSnapshot snapshot, RunCaptureLayout layout, CaptureMetadata pending) { }
}
