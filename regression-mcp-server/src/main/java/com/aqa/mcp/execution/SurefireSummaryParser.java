package com.aqa.mcp.execution;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/** Hardened Surefire parser. Required failure data is either complete (apart from explicit text truncation) or rejected. */
final class SurefireSummaryParser {
    static final int MAX_SUITES = 100;
    static final int MAX_TESTCASES = 500;
    static final int MAX_FAILURE_RECORDS = 40;
    static final int MAX_MESSAGE_LENGTH = 1_024;
    static final int MAX_STACK_TRACE_LENGTH = 6_144;
    static final int MAX_FAILURE_DETAIL_BYTES = 28 * 1024;
    private SurefireSummaryParser() { }

    static SurefireSummary parse(Path root, List<CaptureMetadata.IndexedFile> files, String runId) throws MalformedReportException {
        try {
            Map<String, SurefireSummary.Suite> suites = new LinkedHashMap<>();
            Map<String, SurefireSummary.Testcase> cases = new LinkedHashMap<>();
            Map<String, SurefireSummary.FailureRecord> failures = new LinkedHashMap<>();
            int[] detailBytes = { 0 }; boolean[] truncated = { false };
            for (CaptureMetadata.IndexedFile file : files) {
                if (!file.path().endsWith(".xml")) continue;
                Path xml = root.resolve(file.path()).normalize();
                if (!xml.startsWith(root)) throw malformed();
                NodeList elements;
                try (InputStream input = Files.newInputStream(xml)) { elements = builder().parse(input).getElementsByTagName("testsuite"); }
                for (int index = 0; index < elements.getLength(); index++) {
                    Element suite = (Element) elements.item(index);
                    SuiteData data = suite(suite, runId, detailBytes, truncated);
                    SurefireSummary.Suite existing = suites.get(data.id());
                    if (existing != null && !existing.equals(data.suite())) throw malformed();
                    suites.putIfAbsent(data.id(), data.suite());
                    for (SurefireSummary.Testcase testcase : data.suite().testcases()) {
                        SurefireSummary.Testcase prior = cases.putIfAbsent(testcase.id(), testcase);
                        if (prior != null && !prior.equals(testcase)) throw malformed();
                    }
                    for (SurefireSummary.FailureRecord record : data.failures()) {
                        SurefireSummary.FailureRecord prior = failures.putIfAbsent(record.failureId(), record);
                        if (prior != null && !prior.equals(record)) throw malformed();
                    }
                }
            }
            List<SurefireSummary.Suite> ordered = new ArrayList<>(suites.values()); ordered.sort(Comparator.comparing(SurefireSummary.Suite::id));
            long tests = cases.size(), failureCount = count(cases, "failure"), errors = count(cases, "error"), skipped = count(cases, "skipped");
            if (failures.size() != failureCount + errors || failures.size() > MAX_FAILURE_RECORDS) throw malformed();
            List<SurefireSummary.FailureRecord> records = failures.values().stream().sorted(Comparator.comparing(SurefireSummary.FailureRecord::suite)
                    .thenComparing(SurefireSummary.FailureRecord::testCase).thenComparing(SurefireSummary.FailureRecord::type)
                    .thenComparing(SurefireSummary.FailureRecord::failureId)).toList();
            BigDecimal duration = cases.values().stream().map(SurefireSummary.Testcase::duration).reduce(BigDecimal.ZERO, BigDecimal::add);
            for (SurefireSummary.Suite suite : ordered) if (suite.tests() == 0) duration = duration.add(suite.duration());
            boolean bounded = ordered.size() > MAX_SUITES || cases.size() > MAX_TESTCASES;
            List<SurefireSummary.Suite> limited = ordered.stream().limit(MAX_SUITES).map(suite -> new SurefireSummary.Suite(suite.id(), suite.tests(),
                    suite.failures(), suite.errors(), suite.skipped(), suite.duration(), suite.testcases().stream().limit(MAX_TESTCASES).toList())).toList();
            return new SurefireSummary(tests, tests - failureCount - errors - skipped, failureCount, errors, skipped, duration, limited,
                    records, "UNAVAILABLE", bounded || truncated[0]);
        } catch (MalformedReportException exception) { throw exception; }
        catch (Exception exception) { throw malformed(); }
    }

    static SurefireSummary parse(Path root, List<CaptureMetadata.IndexedFile> files) throws MalformedReportException { return parse(root, files, "run-parser"); }

    private static SuiteData suite(Element element, String runId, int[] total, boolean[] summaryTruncated) throws MalformedReportException {
        String name = value(element, "name"); String id = normalize(optional(element, "package") + ":" + name);
        List<SurefireSummary.Testcase> testcases = new ArrayList<>(); List<SurefireSummary.FailureRecord> failures = new ArrayList<>();
        NodeList children = element.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) if (children.item(index) instanceof Element child && child.getTagName().equals("testcase")) {
            Element failure = direct(child, "failure"), error = direct(child, "error"), skipped = direct(child, "skipped");
            if ((failure != null ? 1 : 0) + (error != null ? 1 : 0) + (skipped != null ? 1 : 0) > 1) throw malformed();
            String outcome = failure != null ? "failure" : error != null ? "error" : skipped != null ? "skipped" : "passed";
            String classname = normalize(value(child, "classname")); String testName = normalize(value(child, "name"));
            String testcaseId = id + ":" + classname + ":" + testName;
            testcases.add(new SurefireSummary.Testcase(testcaseId, outcome, duration(child, "time")));
            Element diagnostic = failure != null ? failure : error;
            if (diagnostic != null) failures.add(record(runId, id, classname + "#" + testName, failure == null ? "ERROR" : "FAILURE", diagnostic, total, summaryTruncated));
        }
        testcases.sort(Comparator.comparing(SurefireSummary.Testcase::id));
        failures.sort(Comparator.comparing(SurefireSummary.FailureRecord::failureId));
        long tests = testcases.size(), failuresCount = testcases.stream().filter(test -> test.outcome().equals("failure")).count();
        long errors = testcases.stream().filter(test -> test.outcome().equals("error")).count();
        long skippedCount = testcases.stream().filter(test -> test.outcome().equals("skipped")).count();
        verifyCounter(element, "tests", tests); verifyCounter(element, "failures", failuresCount); verifyCounter(element, "errors", errors); verifyCounter(element, "skipped", skippedCount);
        return new SuiteData(id, new SurefireSummary.Suite(id, tests, failuresCount, errors, skippedCount, duration(element, "time"), testcases), failures);
    }

    private static SurefireSummary.FailureRecord record(String runId, String suite, String testCase, String type, Element element, int[] total, boolean[] summaryTruncated) throws MalformedReportException {
        boolean[] recordTruncated = { false };
        String message = PublicDiagnosticSanitizer.bound(element.getAttribute("message"), MAX_MESSAGE_LENGTH, recordTruncated);
        String stack = PublicDiagnosticSanitizer.bound(element.getTextContent(), MAX_STACK_TRACE_LENGTH, recordTruncated);
        int available = Math.max(0, MAX_FAILURE_DETAIL_BYTES - total[0]);
        if (message.getBytes(StandardCharsets.UTF_8).length + stack.getBytes(StandardCharsets.UTF_8).length > available) {
            message = PublicDiagnosticSanitizer.bound(message, Math.min(message.length(), available), recordTruncated);
            available = Math.max(0, available - message.getBytes(StandardCharsets.UTF_8).length);
            stack = PublicDiagnosticSanitizer.bound(stack, available, recordTruncated);
        }
        total[0] += message.getBytes(StandardCharsets.UTF_8).length + stack.getBytes(StandardCharsets.UTF_8).length;
        if (recordTruncated[0]) summaryTruncated[0] = true;
        return new SurefireSummary.FailureRecord(id(runId, suite, testCase, type, message, stack), type, suite, testCase, message, stack,
                SurefireSummary.Allure.none(), recordTruncated[0]);
    }
    private static String id(String runId, String suite, String testCase, String type, String message, String stack) throws MalformedReportException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((runId + "\u0000" + suite + "\u0000" + testCase + "\u0000" + type + "\u0000" + message + "\u0000" + stack).getBytes(StandardCharsets.UTF_8))).substring(0, 32); }
        catch (Exception exception) { throw malformed(); }
    }
    private static Element direct(Element parent, String name) { NodeList nodes = parent.getChildNodes(); for (int index = 0; index < nodes.getLength(); index++) if (nodes.item(index) instanceof Element child && name.equals(child.getTagName())) return child; return null; }
    private static long count(Map<String, SurefireSummary.Testcase> cases, String outcome) { return cases.values().stream().filter(test -> test.outcome().equals(outcome)).count(); }
    private static void verifyCounter(Element element, String name, long actual) throws MalformedReportException { if (element.hasAttribute(name) && integer(element.getAttribute(name)) != actual) throw malformed(); }
    private static long integer(String value) throws MalformedReportException { try { long parsed = Long.parseLong(value); if (parsed < 0) throw malformed(); return parsed; } catch (NumberFormatException exception) { throw malformed(); } }
    private static BigDecimal duration(Element element, String name) throws MalformedReportException { if (!element.hasAttribute(name)) return BigDecimal.ZERO; try { String text = element.getAttribute(name); if (text.length() > 64) throw malformed(); BigDecimal value = new BigDecimal(text); if (value.signum() < 0 || value.precision() > 32 || Math.abs(value.scale()) > 12) throw malformed(); return value; } catch (NumberFormatException exception) { throw malformed(); } }
    private static String value(Element element, String name) throws MalformedReportException { if (!element.hasAttribute(name)) throw malformed(); return element.getAttribute(name); }
    private static String optional(Element element, String name) { return element.hasAttribute(name) ? element.getAttribute(name) : ""; }
    private static String normalize(String value) throws MalformedReportException { String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " "); if (normalized.isBlank() || normalized.length() > 256) throw malformed(); return normalized; }
    private static DocumentBuilderFactory factory() throws Exception { DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); factory.setFeature("http://xml.org/sax/features/external-general-entities", false); factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false); factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true); factory.setXIncludeAware(false); factory.setExpandEntityReferences(false); return factory; }
    private static javax.xml.parsers.DocumentBuilder builder() throws Exception { var builder = factory().newDocumentBuilder(); builder.setEntityResolver((publicId, systemId) -> new InputSource(new java.io.StringReader(""))); builder.setErrorHandler(new org.xml.sax.ErrorHandler() { @Override public void warning(SAXParseException exception) { } @Override public void error(SAXParseException exception) throws SAXParseException { throw exception; } @Override public void fatalError(SAXParseException exception) throws SAXParseException { throw exception; } }); return builder; }
    private static MalformedReportException malformed() { return new MalformedReportException(); }
    private record SuiteData(String id, SurefireSummary.Suite suite, List<SurefireSummary.FailureRecord> failures) { }
    static final class MalformedReportException extends Exception { private static final long serialVersionUID = 1L; }
}
