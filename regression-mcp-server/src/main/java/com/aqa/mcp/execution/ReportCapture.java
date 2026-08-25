package com.aqa.mcp.execution;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Validates server-owned result staging and publishes only complete, atomically moved sets. */
final class ReportCapture {
    static final int MAX_DEPTH = 8;
    static final int MAX_FILES = 2_000;
    static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private final RunStore.AtomicMover mover;

    ReportCapture() { this((source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)); }
    ReportCapture(RunStore.AtomicMover mover) { this.mover = mover; }

    /** Carries the persisted capture status alongside the skipped-test count already computed while parsing the
     * Surefire report, so callers never need to read the published report back off disk to learn it. */
    record CaptureOutcome(CaptureMetadata metadata, Integer skippedTests) { }

    CaptureOutcome capture(RunCaptureLayout layout, CaptureMetadata pending) {
        String runId = layout.runDirectory().getFileName().toString();
        SurefireSummary summary;
        List<CaptureMetadata.IndexedFile> surefireFiles;
        try {
            surefireFiles = validate(layout.surefireStaging(), true);
            if (surefireFiles.isEmpty() || surefireFiles.stream().noneMatch(file -> file.path().startsWith("TEST-") && file.path().endsWith(".xml"))) throw new IOException("Required Surefire XML is absent.");
            summary = SurefireSummaryParser.parse(layout.surefireStaging(), surefireFiles, runId);
        } catch (SurefireSummaryParser.MalformedReportException exception) {
            cleanup(layout.surefireStaging()); cleanup(layout.allureStaging()); return new CaptureOutcome(new CaptureMetadata(1, CaptureStatus.UNAVAILABLE, pending.nonce(), new CaptureMetadata.CaptureSet("MALFORMED", List.of(), null), null), null);
        } catch (Exception exception) {
            cleanup(layout.surefireStaging());
            cleanup(layout.allureStaging());
            return new CaptureOutcome(new CaptureMetadata(1, CaptureStatus.UNAVAILABLE, pending.nonce(), null, null), null);
        }
        OptionalAllure optional = publishOptional(layout.allureStaging(), layout.allureFinal(), layout.allureIndex(), runId);
        summary = enrich(summary, optional.results(), optional.availability());
        CaptureMetadata.CaptureSet surefire = publishRequired(layout.surefireStaging(), layout.surefireFinal(), layout.surefireIndex(), runId, surefireFiles, summary);
        if (surefire == null || !surefire.status().equals("AVAILABLE")) return new CaptureOutcome(new CaptureMetadata(1, CaptureStatus.UNAVAILABLE, pending.nonce(), surefire, optional.capture()), null);
        CaptureMetadata.CaptureSet allure = optional.capture();
        return new CaptureOutcome(new CaptureMetadata(1, allure == null ? CaptureStatus.PARTIAL : CaptureStatus.COMPLETE, pending.nonce(), surefire, allure), (int) summary.skipped());
    }

    private CaptureMetadata.CaptureSet publishRequired(Path staging, Path target, Path index, String runId, List<CaptureMetadata.IndexedFile> files, SurefireSummary summary) {
        try {
            String digest = publish(staging, target, index, new PublishedReportIndex(3, runId, "surefire", files, summary));
            return new CaptureMetadata.CaptureSet("AVAILABLE", files, digest);
        } catch (Exception exception) {
            cleanup(staging);
            cleanup(target);
            delete(index);
            return null;
        }
    }

    private OptionalAllure publishOptional(Path staging, Path target, Path index, String runId) {
        try {
            List<CaptureMetadata.IndexedFile> files = validate(staging, false);
            if (files.isEmpty()) throw new OptionalUnavailable();
            List<AllureResultParser.Result> results = AllureResultParser.parse(staging, files);
            if (results.isEmpty()) throw new OptionalUnavailable();
            String digest = publish(staging, target, index, new PublishedReportIndex(2, runId, "allure", files, null));
            return new OptionalAllure(new CaptureMetadata.CaptureSet("AVAILABLE", files, digest), results, "AVAILABLE");
        } catch (OptionalUnavailable exception) {
            cleanup(staging); cleanup(target); delete(index);
            return new OptionalAllure(null, List.of(), "UNAVAILABLE");
        } catch (Exception exception) {
            cleanup(staging);
            cleanup(target);
            delete(index);
            return new OptionalAllure(null, List.of(), "REJECTED");
        }
    }

    private List<CaptureMetadata.IndexedFile> validate(Path staging, boolean surefire) throws Exception {
        if (!Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(staging)) {
            throw new IOException("Capture staging directory is unavailable.");
        }
        Path realRoot = staging.toRealPath();
        List<Path> paths = new ArrayList<>();
        long[] total = { 0 };
        Files.walkFileTree(staging, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                rejectEscape(directory, realRoot);
                if (staging.relativize(directory).getNameCount() > MAX_DEPTH) throw new IOException("Capture depth limit exceeded.");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                rejectEscape(file, realRoot);
                if (!attributes.isRegularFile()) throw new IOException("Only regular capture files are allowed.");
                if (++count[0] > MAX_FILES) throw new IOException("Capture file limit exceeded.");
                long size = attributes.size();
                if (size > MAX_FILE_BYTES || (total[0] += size) > MAX_TOTAL_BYTES) throw new IOException("Capture size limit exceeded.");
                paths.add(file); return FileVisitResult.CONTINUE;
            }
            private final int[] count = { 0 };
        });
        paths.sort(Comparator.comparing(path -> display(staging.relativize(path))));
        List<CaptureMetadata.IndexedFile> files = new ArrayList<>();
        for (Path file : paths) {
            String relative = display(staging.relativize(file));
            if (relative.startsWith("../") || relative.isBlank()) throw new IOException("Capture traversal is not allowed.");
            if (surefire && relative.endsWith(".xml")) parseSurefireXml(file);
            files.add(new CaptureMetadata.IndexedFile(relative, Files.size(file), sha256(file)));
        }
        return List.copyOf(files);
    }

    private String publish(Path staging, Path target, Path index, PublishedReportIndex reportIndex) throws IOException {
        Path run = staging.getParent().getParent();
        verifyContained(run, staging); verifyContained(run, target); verifyContained(run, index);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.exists(index, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Capture publication target already exists.");
        }
        FileStore stagingStore = Files.getFileStore(staging);
        FileStore targetStore = Files.getFileStore(target.getParent());
        if (!stagingStore.equals(targetStore)) throw new IOException("Capture staging must share its publication filesystem.");
        mover.move(staging, target);
        try {
            verifyHashes(target, reportIndex.files());
            return writeIndex(index, reportIndex);
        } catch (IOException exception) {
            cleanup(target);
            throw exception;
        }
    }

    private String writeIndex(Path index, PublishedReportIndex reportIndex) throws IOException {
        if (Files.isSymbolicLink(index)) throw new IOException("Capture index cannot be a symbolic link.");
        Path temporary = Files.createTempFile(index.getParent(), "index.", ".tmp");
        try {
            Files.writeString(temporary, JSON.writeValueAsString(reportIndex), StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            mover.move(temporary, index);
            return sha256(index);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void parseSurefireXml(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        try (InputStream input = Files.newInputStream(file)) {
            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new java.io.StringReader("")));
            builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override public void warning(SAXParseException exception) { }
                @Override public void error(SAXParseException exception) throws SAXParseException { throw exception; }
                @Override public void fatalError(SAXParseException exception) throws SAXParseException { throw exception; }
            });
            builder.parse(input);
        }
    }

    private static SurefireSummary enrich(SurefireSummary summary, List<AllureResultParser.Result> results, String availability) {
        if (!"AVAILABLE".equals(availability)) return summary.withAllure(summary.failureRecords(), availability, false);
        List<SurefireSummary.FailureRecord> records = new ArrayList<>(); boolean[] used = new boolean[results.size()]; boolean truncated = false;
        for (SurefireSummary.FailureRecord record : summary.failureRecords()) {
            List<Integer> matches = new ArrayList<>();
            for (int index = 0; index < results.size(); index++) if (!used[index] && matches(record, results.get(index))) matches.add(index);
            if (matches.size() == 1) {
                AllureResultParser.Result result = results.get(matches.getFirst()); used[matches.getFirst()] = true;
                SurefireSummary.Allure allure = new SurefireSummary.Allure("AVAILABLE", result.name().isBlank() ? result.fullName() : result.name(),
                        result.details(), result.steps(), result.attachments(), result.truncated());
                records.add(record.withAllure(allure, result.truncated())); truncated |= result.truncated();
            } else records.add(record.withAllure(new SurefireSummary.Allure("UNMATCHED", null, null, List.of(), false, false), false));
        }
        return summary.withAllure(records, "AVAILABLE", truncated);
    }
    private static boolean matches(SurefireSummary.FailureRecord record, AllureResultParser.Result result) {
        if (!("failed".equalsIgnoreCase(result.status()) || "broken".equalsIgnoreCase(result.status()))) return false;
        String composite = record.suite() + ":" + record.testCase();
        return record.testCase().equals(result.name()) || record.testCase().equals(result.fullName()) || composite.equals(result.name()) || composite.equals(result.fullName());
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = new byte[8192]; int read;
            while ((read = input.read(bytes)) >= 0) digest.update(bytes, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static void rejectEscape(Path path, Path realRoot) throws IOException {
        if (Files.isSymbolicLink(path) || !path.toRealPath().startsWith(realRoot)) throw new IOException("Capture path escapes its staging directory.");
    }
    private static void verifyContained(Path run, Path path) throws IOException {
        Path realRun = run.toRealPath();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(realRun)) throw new IOException("Capture path is not run-bound.");
        Path current = realRun;
        for (Path component : realRun.relativize(normalized)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) throw new IOException("Capture paths cannot contain symbolic links.");
        }
    }
    private static void verifyHashes(Path root, List<CaptureMetadata.IndexedFile> files) throws IOException {
        for (CaptureMetadata.IndexedFile file : files) {
            Path candidate = root.resolve(file.path()).normalize();
            if (!candidate.startsWith(root) || Files.isSymbolicLink(candidate) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(candidate) != file.size() || !sha256(candidate).equals(file.sha256())) {
                throw new IOException("Captured file did not retain its validated contents.");
            }
        }
    }
    private static String display(Path relative) { return relative.toString().replace('\\', '/'); }

    private static void cleanup(Path path) {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) return;
        try { Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException { Files.deleteIfExists(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException { if (exception != null) throw exception; Files.deleteIfExists(directory); return FileVisitResult.CONTINUE; }
        }); } catch (IOException ignored) { }
    }
    private static void delete(Path path) { try { if (!Files.isSymbolicLink(path)) Files.deleteIfExists(path); } catch (IOException ignored) { } }

    private record OptionalAllure(CaptureMetadata.CaptureSet capture, List<AllureResultParser.Result> results, String availability) { }
    private static final class OptionalUnavailable extends IOException { private static final long serialVersionUID = 1L; }

}
