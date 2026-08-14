package com.aqa.mcp.execution;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Package-private system seam used by deterministic ownership and reuse tests. */
interface ProcessView {
    Optional<ObservedProcess> find(long pid);
    List<ObservedProcess> descendants(long rootPid, int maximum);
    boolean destroy(OwnedProcessIdentity identity, boolean forcibly, Duration grace);
}
