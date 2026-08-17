package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SurefireSummaryParserTest {
    @TempDir Path root;

    @Test
    void aggregatesPassingFailureErrorSkippedMultipleFilesAndDecimalDurationsDeterministically() throws Exception {
        write("TEST-a.xml", suite("a", "0.10", testcase("A", "pass", "0.10", ""), testcase("A", "failure", "0.20", "failure")));
        write("TEST-b.xml", suite("b", "0.30", testcase("B", "error", "0.15", "error"), testcase("B", "skip", "0.15", "skipped")));

        SurefireSummary summary = parse();

        assertThat(summary.tests()).isEqualTo(4); assertThat(summary.passed()).isEqualTo(1); assertThat(summary.failures()).isEqualTo(1);
        assertThat(summary.errors()).isEqualTo(1); assertThat(summary.skipped()).isEqualTo(1); assertThat(summary.duration().toPlainString()).isEqualTo("0.60");
        assertThat(summary.suites()).extracting(SurefireSummary.Suite::id).containsExactly(":a", ":b");
    }

    @Test
    void exactDuplicateTestAndSuiteAreDeduplicatedButContradictionsAreRejected() throws Exception {
        String duplicate = suite("same", "0.2", testcase("Same", "test", "0.2", "")); write("TEST-one.xml", duplicate); write("TEST-two.xml", duplicate);
        assertThat(parse().tests()).isEqualTo(1);
        Files.writeString(root.resolve("TEST-two.xml"), suite("same", "0.2", testcase("Same", "test", "0.2", "failure")));
        assertThatThrownBy(this::parse).isInstanceOf(SurefireSummaryParser.MalformedReportException.class);
    }

    @Test
    void capturesSeparatedFailureAndErrorDiagnosticsInDeterministicOrderAndSanitizesPaths() throws Exception {
        write("TEST-z.xml", suite("z", "0.2", testcase("Z", "error", "0.1", "error message='C:\\work\\secret.txt'>at C:\\work\\secret.txt</error>")));
        write("TEST-a.xml", suite("a", "0.2", testcase("A", "failure", "0.1", "failure message='/home/me/repo/file'>bad /home/me/repo/file</failure>")));

        SurefireSummary summary = parse();

        assertThat(summary.failures()).isEqualTo(1); assertThat(summary.errors()).isEqualTo(1);
        assertThat(summary.failureRecords()).extracting(SurefireSummary.FailureRecord::type).containsExactly("FAILURE", "ERROR");
        assertThat(summary.failureRecords()).allSatisfy(record -> {
            assertThat(record.failureId()).hasSize(32); assertThat(record.message()).doesNotContain("C:\\work", "/home/me");
            assertThat(record.stackTrace()).doesNotContain("C:\\work", "/home/me");
        });
    }

    @Test
    void boundsDiagnosticTextWithoutChangingAuthoritativeCounts() throws Exception {
        String longText = "x".repeat(SurefireSummaryParser.MAX_STACK_TRACE_LENGTH + 100);
        write("TEST-one.xml", suite("one", "0.1", testcase("One", "fails", "0.1", "failure message='" + "m".repeat(SurefireSummaryParser.MAX_MESSAGE_LENGTH + 10) + "'>" + longText + "</failure>")));

        SurefireSummary summary = parse();

        assertThat(summary.tests()).isEqualTo(1); assertThat(summary.failures()).isEqualTo(1); assertThat(summary.failureRecords()).hasSize(1);
        assertThat(summary.failureRecords().getFirst().recordTruncated()).isTrue(); assertThat(summary.detailsTruncated()).isTrue();
    }

    @Test
    void sanitizerReplacesControlAndInvalidTextDeterministically() {
        boolean[] truncated = { false };
        assertThat(PublicDiagnosticSanitizer.bound("before\u0001\ufffdafter", 128, truncated)).contains("before ").contains("[invalid-text]").contains("after");
        assertThat(truncated[0]).isTrue();
    }

    @Test
    void acceptsZeroTestSuitesAndRejectsCountersDurationsAndXmlAttacks() throws Exception {
        write("TEST-zero.xml", "<testsuite name='zero' tests='0' failures='0' errors='0' skipped='0' time='0.25'/>");
        assertThat(parse().duration().toPlainString()).isEqualTo("0.25");
        for (String invalid : List.of("<testsuite name='bad' tests='-1' failures='0' errors='0' skipped='0' time='0'/>",
                "<testsuite name='bad' tests='1' failures='0' errors='0' skipped='0' time='NaN'><testcase classname='c' name='n' time='NaN'/></testsuite>",
                "<testsuite name='bad' tests='1' failures='0' errors='0' skipped='0' time='Infinity'><testcase classname='c' name='n' time='Infinity'/></testsuite>",
                "<testsuite name='bad' tests='9223372036854775808' failures='0' errors='0' skipped='0' time='0'/>",
                "<testsuite name='bad' tests='2' failures='0' errors='0' skipped='0' time='0'><testcase classname='c' name='n' time='0'/></testsuite>",
                "<!DOCTYPE x [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><testsuite name='bad' tests='0' failures='0' errors='0' skipped='0' time='0'/>")) {
            Files.writeString(root.resolve("TEST-zero.xml"), invalid);
            assertThatThrownBy(this::parse).isInstanceOf(SurefireSummaryParser.MalformedReportException.class);
        }
    }

    private SurefireSummary parse() throws Exception {
        try (var paths = Files.list(root)) {
            return SurefireSummaryParser.parse(root, paths.sorted().map(path -> new CaptureMetadata.IndexedFile(path.getFileName().toString(), 0, "x")).toList());
        }
    }
    private void write(String name, String xml) throws Exception { Files.writeString(root.resolve(name), xml); }
    private static String suite(String name, String time, String... cases) { long failures = java.util.Arrays.stream(cases).filter(value -> value.contains("<failure")).count(); long errors = java.util.Arrays.stream(cases).filter(value -> value.contains("<error")).count(); long skipped = java.util.Arrays.stream(cases).filter(value -> value.contains("<skipped")).count(); return "<testsuite name='" + name + "' tests='" + cases.length + "' failures='" + failures + "' errors='" + errors + "' skipped='" + skipped + "' time='" + time + "'>" + String.join("", cases) + "</testsuite>"; }
    private static String testcase(String clazz, String name, String time, String result) { return "<testcase classname='" + clazz + "' name='" + name + "' time='" + time + "'>" + (result.isBlank() ? "" : result.contains("</") ? "<" + result : "<" + result + "/>") + "</testcase>"; }
}
