package com.aqa.mcp.execution;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

/** Separate test JVM used only to prove the operating-system file lock boundary. */
public final class RunStoreLockHolderFixture {
    public static void main(String[] args) {
        try (RunStore.Lock ignored = new RunStore(Path.of(args[0])).acquireActiveLock()) {
            System.out.println("LOCK_HELD " + args[1]);
            System.out.flush();
            LockSupport.parkNanos(Duration.ofSeconds(10).toNanos());
        }
    }
}
