package com.aqa.mcp.execution;

import java.time.Instant;
import java.util.Objects;

/** Immutable server-owned process identity. A PID is never actionable without its creation instant. */
record OwnedProcessIdentity(long pid, Instant startInstant, Long parentPid, int depth, Instant observedAt) {
    OwnedProcessIdentity {
        if (pid <= 0 || startInstant == null || depth < 0 || observedAt == null) {
            throw new IllegalArgumentException("A process identity requires pid, start instant, depth, and observation time.");
        }
    }

    boolean sameProcess(ObservedProcess observed) {
        return observed != null && pid == observed.pid() && startInstant.equals(observed.startInstant());
    }
}
