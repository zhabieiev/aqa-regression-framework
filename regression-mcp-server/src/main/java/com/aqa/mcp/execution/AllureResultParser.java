package com.aqa.mcp.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;

/** Parses only bounded Allure result JSON at capture time; it never creates authoritative failures. */
final class AllureResultParser {
    static final int MAX_RESULT_FILES = 20;
    static final int MAX_STEPS = 4;
    static final int MAX_STEP_DEPTH = 3;
    private static final JsonFactory JSON = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
            .maxNestingDepth(16).maxStringLength(4_096).maxNumberLength(64).maxTokenCount(4_000).build()).build();
    private AllureResultParser() { }

    static List<Result> parse(Path root, List<CaptureMetadata.IndexedFile> files) throws IOException {
        List<Result> results = new ArrayList<>();
        for (CaptureMetadata.IndexedFile file : files) if (file.path().endsWith("-result.json")) {
            if (results.size() >= MAX_RESULT_FILES) throw new IOException("Allure result count limit exceeded.");
            Path candidate = root.resolve(file.path()).normalize();
            if (!candidate.startsWith(root)) throw new IOException("Allure result escapes capture.");
            results.add(result(candidate));
        }
        results.sort(Comparator.comparing(Result::name).thenComparing(Result::fullName).thenComparing(Result::status));
        return List.copyOf(results);
    }
    private static Result result(Path file) throws IOException {
        String name = "", fullName = "", status = "", details = ""; List<SurefireSummary.Step> steps = List.of(); boolean attachments = false; boolean[] truncated = { false };
        try (JsonParser parser = JSON.createParser(Files.newInputStream(file))) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw new IOException("Allure result must be an object.");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName(); JsonToken token = parser.nextToken();
                if ("name".equals(field) && token == JsonToken.VALUE_STRING) name = text(parser.getText(), 128, truncated);
                else if ("fullName".equals(field) && token == JsonToken.VALUE_STRING) fullName = text(parser.getText(), 256, truncated);
                else if ("status".equals(field) && token == JsonToken.VALUE_STRING) status = text(parser.getText(), 64, truncated);
                else if ("statusDetails".equals(field) && token == JsonToken.START_OBJECT) details = details(parser, truncated);
                else if ("steps".equals(field) && token == JsonToken.START_ARRAY) steps = steps(parser, 1, truncated);
                else if ("attachments".equals(field) && token == JsonToken.START_ARRAY) {
                    boolean present = false;
                    while (parser.nextToken() != JsonToken.END_ARRAY) { present = true; parser.skipChildren(); }
                    attachments = present;
                }
                else parser.skipChildren();
            }
        }
        if (name.isBlank() && fullName.isBlank()) throw new IOException("Allure result identity is absent.");
        return new Result(name, fullName, status, details, steps, attachments, truncated[0]);
    }
    private static String details(JsonParser parser, boolean[] truncated) throws IOException {
        String message = "", trace = "";
        while (parser.nextToken() != JsonToken.END_OBJECT) { String field = parser.currentName(); JsonToken token = parser.nextToken();
            if ("message".equals(field) && token == JsonToken.VALUE_STRING) message = text(parser.getText(), 256, truncated);
            else if ("trace".equals(field) && token == JsonToken.VALUE_STRING) trace = text(parser.getText(), 256, truncated); else parser.skipChildren(); }
        return message.isBlank() ? trace : trace.isBlank() ? message : message + "\n" + trace;
    }
    private static List<SurefireSummary.Step> steps(JsonParser parser, int depth, boolean[] truncated) throws IOException {
        List<SurefireSummary.Step> values = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) { parser.skipChildren(); continue; }
            if (values.size() >= MAX_STEPS || depth > MAX_STEP_DEPTH) { parser.skipChildren(); truncated[0] = true; continue; }
            String name = "", status = ""; List<SurefireSummary.Step> nested = List.of();
            while (parser.nextToken() != JsonToken.END_OBJECT) { String field = parser.currentName(); JsonToken token = parser.nextToken();
                if ("name".equals(field) && token == JsonToken.VALUE_STRING) name = text(parser.getText(), 96, truncated);
                else if ("status".equals(field) && token == JsonToken.VALUE_STRING) status = text(parser.getText(), 64, truncated);
                else if ("steps".equals(field) && token == JsonToken.START_ARRAY) nested = steps(parser, depth + 1, truncated); else parser.skipChildren(); }
            values.add(new SurefireSummary.Step(name, status, nested));
        }
        return List.copyOf(values);
    }
    private static String text(String value, int maximum, boolean[] truncated) { return PublicDiagnosticSanitizer.bound(value, maximum, truncated); }
    record Result(String name, String fullName, String status, String details, List<SurefireSummary.Step> steps, boolean attachments, boolean truncated) { }
}
