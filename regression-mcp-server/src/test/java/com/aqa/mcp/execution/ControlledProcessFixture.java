package com.aqa.mcp.execution;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.locks.LockSupport;

/** Test-only executable process tree. All children use the current test JVM and expire without external services. */
public final class ControlledProcessFixture {
    private static final long DEADLINE_NANOS = Duration.ofSeconds(10).toNanos();
    private static final int LARGE_OUTPUT_BYTES = 17 * 1024 * 1024;
    /** A minimal valid 1x1 transparent PNG, used so Files.probeContentType() genuinely resolves image/png. */
    private static final String MINIMAL_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=";

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
            case "FAIL_WITH_ARTIFACTS" -> failWithArtifacts(args[2], args[3]);
            default -> throw new IllegalArgumentException("Unknown controlled mode: " + mode);
        }
    }

    /** Writes a genuine failing Surefire suite plus a matching Allure result and screenshot attachment into the
     * server-owned staging directories, then exits non-zero, so Gate 14.4 tools have real capture data to serve. */
    private static void failWithArtifacts(String surefireStagingDirectory, String allureStagingDirectory) throws Exception {
        Path surefireStaging = Path.of(surefireStagingDirectory);
        Path allureStaging = Path.of(allureStagingDirectory);
        Files.writeString(surefireStaging.resolve("TEST-one.xml"),
                "<testsuite name='one' tests='1' failures='1' errors='0' skipped='0' time='0.1'>"
                        + "<testcase classname='one' name='fails' time='0.1'>"
                        + "<failure message='Element not found'>stack trace</failure></testcase></testsuite>");
        Files.writeString(allureStaging.resolve("one-result.json"),
                "{\"name\":\"one#fails\",\"status\":\"failed\",\"statusDetails\":{\"message\":\"Element not found\"},"
                        + "\"steps\":[{\"name\":\"navigate\",\"status\":\"passed\"},{\"name\":\"assert\",\"status\":\"failed\"}],"
                        + "\"attachments\":[{\"name\":\"screenshot\",\"source\":\"failure-screenshot.png\",\"type\":\"image/png\"}]}");
        Files.write(allureStaging.resolve("failure-screenshot.png"), Base64.getDecoder().decode(MINIMAL_PNG_BASE64));
        // A third, oversized allow-listed-MIME attachment, so tests can exercise the tool-layer ARTIFACT_TOO_LARGE bound
        // against a genuine published artifact rather than a synthetic one.
        byte[] oversized = new byte[3 * 1024 * 1024];
        java.util.Arrays.fill(oversized, (byte) 'x');
        Files.write(allureStaging.resolve("oversized-log.txt"), oversized);
        System.exit(7);
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
