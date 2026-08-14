package com.aqa.mcp.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** JDK-only process view: no shell, process-name matching, WMI, or client-controlled target exists. */
final class SystemProcessView implements ProcessView {
    @Override public Optional<ObservedProcess> find(long pid) {
        return ProcessHandle.of(pid).flatMap(this::observed);
    }

    @Override public List<ObservedProcess> descendants(long rootPid, int maximum) {
        Optional<ProcessHandle> root = ProcessHandle.of(rootPid);
        if (root.isEmpty()) return List.of();
        List<ObservedProcess> result = new ArrayList<>();
        root.get().descendants().limit(maximum).forEach(handle -> observed(handle).ifPresent(result::add));
        result.sort(Comparator.comparingInt(ObservedProcess::depth).reversed().thenComparingLong(ObservedProcess::pid));
        return List.copyOf(result);
    }

    @Override public boolean destroy(OwnedProcessIdentity identity, boolean forcibly, Duration grace) {
        Optional<ProcessHandle> current = ProcessHandle.of(identity.pid());
        if (current.isEmpty() || observed(current.get()).filter(identity::sameProcess).isEmpty()) return true;
        ProcessHandle handle = current.get();
        if (forcibly) handle.destroyForcibly(); else handle.destroy();
        try { handle.onExit().get(grace.toMillis(), TimeUnit.MILLISECONDS); return true; }
        catch (Exception ignored) { return !handle.isAlive(); }
    }

    private Optional<ObservedProcess> observed(ProcessHandle handle) {
        return handle.info().startInstant().map(start -> new ObservedProcess(handle.pid(), start,
                handle.parent().map(ProcessHandle::pid).orElse(null), depth(handle)));
    }

    private static int depth(ProcessHandle handle) {
        int depth = 0;
        for (Optional<ProcessHandle> parent = handle.parent(); parent.isPresent(); parent = parent.get().parent()) depth++;
        return depth;
    }
}
