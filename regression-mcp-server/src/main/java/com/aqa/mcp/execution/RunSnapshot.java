package com.aqa.mcp.execution;

import java.time.Instant;
import java.util.List;

/** Immutable, client-safe run status; it deliberately contains no filesystem paths or raw process diagnostics. */
public record RunSnapshot(String runId, String module, String environment, boolean headless, String tags,
        int timeoutSeconds, TestRunState state, Instant createdAt, Instant startedAt, Instant finishedAt,
        Integer exitCode, String reason, long stdoutBytes, long stderrBytes, boolean stdoutTruncated,
        boolean stderrTruncated) {
    public RunSnapshot {
        if (runId == null || module == null || environment == null || tags == null || state == null || createdAt == null) {
            throw new IllegalArgumentException("Run snapshot fields are required.");
        }
    }

    public boolean terminal() { return state.isTerminal(); }

    public List<String> safeFields() { return List.of(runId, module, environment, tags, state.name()); }
}
