package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ProcessOwnershipTrackerTest {
    @Test
    void retainedReparentedIdentityIsStillCleanedDeepestFirst() {
        FakeProcessView view = new FakeProcessView();
        ObservedProcess root = view.add(10, 1, null, 1);
        view.add(11, 2, 10L, 2);
        view.add(12, 3, 11L, 3);
        ProcessOwnershipTracker tracker = new ProcessOwnershipTracker(view, root);
        tracker.observe();
        view.reparent(11, null);
        view.reparent(12, null);

        assertThat(tracker.cleanupAll()).isTrue();

        assertThat(view.terminated).containsExactly("12:graceful", "11:graceful", "10:graceful");
        assertThat(tracker.identities()).hasSize(3);
    }

    @Test
    void reusedPidWithDifferentStartInstantIsSkipped() {
        FakeProcessView view = new FakeProcessView();
        ObservedProcess root = view.add(10, 1, null, 1);
        ProcessOwnershipTracker tracker = new ProcessOwnershipTracker(view, root);
        view.replace(10, 99, null, 1);

        assertThat(tracker.cleanupAll()).isTrue();

        assertThat(view.terminated).isEmpty();
    }

    @Test
    void failedGracefulTerminationFallsBackToForcedTermination() {
        FakeProcessView view = new FakeProcessView();
        ObservedProcess root = view.add(10, 1, null, 1);
        view.add(11, 2, 10L, 2);
        view.forceOnly.add(11L);
        ProcessOwnershipTracker tracker = new ProcessOwnershipTracker(view, root);
        tracker.observe();

        assertThat(tracker.cleanupAll()).isTrue();

        assertThat(view.terminated).containsExactly("11:graceful", "11:forced", "10:graceful");
    }

    @Test
    void incompleteIdentityIsRejectedBeforeItCanBecomeOwned() {
        assertThatThrownBy(() -> new OwnedProcessIdentity(1, null, null, 0, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class FakeProcessView implements ProcessView {
        private final Map<Long, ObservedProcess> processes = new LinkedHashMap<>();
        private final List<String> terminated = new ArrayList<>();
        private final java.util.Set<Long> forceOnly = new java.util.HashSet<>();
        ObservedProcess add(long pid, long seconds, Long parent, int depth) {
            ObservedProcess process = new ObservedProcess(pid, Instant.ofEpochSecond(seconds), parent, depth);
            processes.put(pid, process); return process;
        }
        void reparent(long pid, Long parent) { ObservedProcess process = processes.get(pid); processes.put(pid, new ObservedProcess(pid, process.startInstant(), parent, process.depth())); }
        void replace(long pid, long seconds, Long parent, int depth) { add(pid, seconds, parent, depth); }
        @Override public Optional<ObservedProcess> find(long pid) { return Optional.ofNullable(processes.get(pid)); }
        @Override public List<ObservedProcess> descendants(long rootPid, int maximum) { return processes.values().stream().filter(process -> process.pid() != rootPid).limit(maximum).toList(); }
        @Override public boolean destroy(OwnedProcessIdentity identity, boolean forcibly, Duration grace) {
            terminated.add(identity.pid() + ":" + (forcibly ? "forced" : "graceful"));
            if (forceOnly.contains(identity.pid()) && !forcibly) return false;
            processes.remove(identity.pid()); return true;
        }
    }
}
