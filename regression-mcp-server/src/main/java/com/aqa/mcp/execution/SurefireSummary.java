package com.aqa.mcp.execution;

import java.math.BigDecimal;
import java.util.List;

/** Immutable authoritative Surefire aggregate and its bounded, public-safe failed testcase records. */
public record SurefireSummary(long tests, long passed, long failures, long errors, long skipped, BigDecimal duration,
        List<Suite> suites, List<FailureRecord> failureRecords, String allureAvailability, boolean detailsTruncated) {
    public SurefireSummary {
        if (tests < 0 || passed < 0 || failures < 0 || errors < 0 || skipped < 0 || duration == null
                || duration.signum() < 0 || passed != tests - failures - errors - skipped
                || allureAvailability == null || allureAvailability.isBlank()) {
            throw new IllegalArgumentException("Surefire summary is internally inconsistent.");
        }
        suites = List.copyOf(suites == null ? List.of() : suites);
        failureRecords = List.copyOf(failureRecords == null ? List.of() : failureRecords);
        if (failureRecords.size() != failures + errors) throw new IllegalArgumentException("Failure records do not match authoritative counts.");
    }

    public SurefireSummary(long tests, long passed, long failures, long errors, long skipped, BigDecimal duration,
            List<Suite> suites, boolean detailsTruncated) {
        this(tests, passed, failures, errors, skipped, duration, suites, List.of(), "UNAVAILABLE", detailsTruncated);
    }

    public SurefireSummary withAllure(List<FailureRecord> records, String availability, boolean truncated) {
        return new SurefireSummary(tests, passed, failures, errors, skipped, duration, suites, records, availability,
                detailsTruncated || truncated);
    }

    public record Suite(String id, long tests, long failures, long errors, long skipped, BigDecimal duration, List<Testcase> testcases) {
        public Suite { testcases = List.copyOf(testcases == null ? List.of() : testcases); }
    }
    public record Testcase(String id, String outcome, BigDecimal duration) { }
    public record FailureRecord(String failureId, String type, String suite, String testCase, String message,
            String stackTrace, Allure allure, boolean recordTruncated) {
        public FailureRecord {
            if (!"FAILURE".equals(type) && !"ERROR".equals(type)) throw new IllegalArgumentException("Unknown failure type.");
            allure = allure == null ? Allure.none() : allure;
        }
        public FailureRecord withAllure(Allure value, boolean truncated) {
            return new FailureRecord(failureId, type, suite, testCase, message, stackTrace, value, recordTruncated || truncated);
        }
    }
    public record Allure(String availability, String scenario, String statusDetails, List<Step> steps,
            boolean attachmentsPresent, boolean truncated) {
        public Allure { steps = List.copyOf(steps == null ? List.of() : steps); }
        static Allure none() { return new Allure("UNAVAILABLE", null, null, List.of(), false, false); }
    }
    public record Step(String name, String status, List<Step> steps) {
        public Step { steps = List.copyOf(steps == null ? List.of() : steps); }
    }
}
