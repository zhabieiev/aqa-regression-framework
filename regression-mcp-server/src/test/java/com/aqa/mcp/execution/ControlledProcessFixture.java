package com.aqa.mcp.execution;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

/** Test-only executable process tree. All children use the current test JVM and expire without external services. */
public final class ControlledProcessFixture {
    private static final long DEADLINE_NANOS = Duration.ofSeconds(10).toNanos();
    private static final int LARGE_OUTPUT_BYTES = 17 * 1024 * 1024;

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String token = args[1];
        long deadline = System.nanoTime() + DEADLINE_NANOS;
        switch (mode) {
            case "PASS" -> { return; }
            case "FAIL" -> System.exit(7);
            case "WAIT", "IGNORE_GRACEFUL_TERMINATION" -> waitUntil(deadline);
            case "LARGE_OUTPUT" -> largeOutput();
            case "SPAWN_CHILD" -> { report(start("WAIT", token)); waitUntil(deadline); }
            case "SPAWN_CHILD_AND_EXIT_PARENT" -> { report(start("WAIT", token)); waitBriefly(); }
            case "SPAWN_GRANDCHILD" -> { report(start("CHILD_SPAWN_CHILD", token)); waitUntil(deadline); }
            case "CHILD_SPAWN_CHILD" -> { report(start("WAIT", token)); waitUntil(deadline); }
            default -> throw new IllegalArgumentException("Unknown controlled mode: " + mode);
        }
    }

    private static Process start(String mode, String token) throws java.io.IOException {
        return new ProcessBuilder(javaExecutable(), "-cp", System.getProperty("java.class.path"),
                ControlledProcessFixture.class.getName(), mode, token).start();
    }

    private static void report(Process child) {
        System.out.println("CONTROLLED_CHILD " + child.pid() + " " + child.info().startInstant().map(Object::toString).orElse("missing"));
        System.out.flush();
    }

    private static void largeOutput() throws InterruptedException {
        Thread stdout = Thread.ofVirtual().start(() -> write(System.out, (byte) 'O'));
        Thread stderr = Thread.ofVirtual().start(() -> write(System.err, (byte) 'E'));
        stdout.join();
        stderr.join();
    }

    private static void write(java.io.PrintStream stream, byte marker) {
        byte[] block = new byte[64 * 1024];
        java.util.Arrays.fill(block, marker);
        int remaining = LARGE_OUTPUT_BYTES;
        while (remaining > 0) {
            int count = Math.min(block.length, remaining);
            stream.write(block, 0, count);
            remaining -= count;
        }
        stream.write(marker == 'O' ? "TAIL-OUT".getBytes(StandardCharsets.UTF_8) : "TAIL-ERR".getBytes(StandardCharsets.UTF_8), 0, 8);
        stream.flush();
    }

    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java").toString();
    }

    private static void waitBriefly() { LockSupport.parkNanos(Duration.ofMillis(500).toNanos()); }
    private static void waitUntil(long deadline) {
        while (System.nanoTime() < deadline) LockSupport.parkNanos(Duration.ofMillis(25).toNanos());
    }
}
