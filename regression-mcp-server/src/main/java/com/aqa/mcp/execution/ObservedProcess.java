package com.aqa.mcp.execution;

import java.time.Instant;

/** A process-table observation whose start instant was available and therefore can be used safely. */
record ObservedProcess(long pid, Instant startInstant, Long parentPid, int depth) {
    ObservedProcess {
        if (pid <= 0 || startInstant == null || depth < 0) throw new IllegalArgumentException("Observed process identity is incomplete.");
    }
}
