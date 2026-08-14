package com.aqa.mcp.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded retained ownership; observed processes remain candidates even after re-parenting. */
final class ProcessOwnershipTracker {
    static final int MAX_IDENTITIES = 128;
    private static final Duration GRACE = Duration.ofSeconds(2);
    private final ProcessView view;
    private final Map<Key, OwnedProcessIdentity> owned = new LinkedHashMap<>();
    private final long rootPid;

    ProcessOwnershipTracker(ProcessView view, ObservedProcess root) {
        this.view = view;
        this.rootPid = root.pid();
        retain(root);
    }

    ProcessOwnershipTracker(ProcessView view, long rootPid, List<OwnedProcessIdentity> identities) {
        this.view = view;
        this.rootPid = rootPid;
        identities.forEach(this::retain);
    }

    synchronized void observe() {
        view.find(rootPid).ifPresent(this::retain);
        for (ObservedProcess descendant : view.descendants(rootPid, MAX_IDENTITIES)) retain(descendant);
    }

    synchronized List<OwnedProcessIdentity> identities() { return List.copyOf(owned.values()); }

    synchronized boolean cleanupDescendants() {
        return cleanup(false);
    }

    synchronized boolean cleanupAll() {
        return cleanup(true);
    }

    synchronized boolean hasSurvivor() {
        return owned.values().stream().anyMatch(identity -> view.find(identity.pid()).filter(identity::sameProcess).isPresent());
    }

    private boolean cleanup(boolean includeRoot) {
        List<OwnedProcessIdentity> ordered = new ArrayList<>(owned.values());
        ordered.sort(Comparator.comparingInt(OwnedProcessIdentity::depth).reversed()
                .thenComparing(OwnedProcessIdentity::observedAt));
        boolean clean = true;
        for (OwnedProcessIdentity identity : ordered) {
            if (!includeRoot && identity.pid() == rootPid) continue;
            if (view.find(identity.pid()).filter(identity::sameProcess).isEmpty()) continue;
            if (!view.destroy(identity, false, GRACE) && !view.destroy(identity, true, GRACE)) clean = false;
        }
        return clean && !hasSurvivor();
    }

    private void retain(ObservedProcess observed) {
        Key key = new Key(observed.pid(), observed.startInstant());
        if (owned.containsKey(key)) return;
        if (owned.size() >= MAX_IDENTITIES) throw new ExecutionPlanningException("PROCESS_IDENTITY_LIMIT_EXCEEDED", "Owned process identity limit exceeded.");
        owned.put(key, new OwnedProcessIdentity(observed.pid(), observed.startInstant(), observed.parentPid(), observed.depth(), Instant.now()));
    }

    private void retain(OwnedProcessIdentity identity) { owned.putIfAbsent(new Key(identity.pid(), identity.startInstant()), identity); }
    private record Key(long pid, Instant start) { }
}
