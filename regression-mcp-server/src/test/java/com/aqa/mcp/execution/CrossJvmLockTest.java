package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrossJvmLockTest {
    @TempDir Path root;

    @Test
    void separateJvmOwnsLockUntilItExitsAndCreatesNoSecondRunDirectory() throws Exception {
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        String token = "execution-lock-" + UUID.randomUUID();
        Process holder = new ProcessBuilder(javaExecutable(), "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                RunStoreLockHolderFixture.class.getName(), root.toString(), token).start();
        try {
            String output = new java.io.BufferedReader(new java.io.InputStreamReader(holder.getInputStream())).readLine();
            assertThat(output).contains("LOCK_HELD " + token);
            TestRunCoordinator second = new TestRunCoordinator(root, CrossJvmLockTest::validator, new ControlledProcessLauncher("PASS"),
                    new ManualTimeoutScheduler(), ignored -> runtime());
            try {
                assertThatThrownBy(() -> second.start(request(), Map.of())).isInstanceOf(ExecutionPlanningException.class)
                        .extracting(error -> ((ExecutionPlanningException) error).code()).isEqualTo("RUN_ALREADY_ACTIVE");
                try (var paths = Files.list(root.resolve(".regression-mcp/runs"))) {
                    assertThat(paths.filter(Files::isDirectory).count()).isZero();
                }
            } finally { second.close(); }
        } finally {
            holder.destroyForcibly();
            assertThat(holder.waitFor(5, TimeUnit.SECONDS)).isTrue();
        }
        RunStore.Lock released = awaitLock(root);
        released.close();
    }

    private static TestRunRequestValidator validator() { return new TestRunRequestValidator(List.of(ExecutionProfileRegistry.COMMERCE_MODULE)); }
    private static StartTestRunRequest request() { return new StartTestRunRequest(ExecutionProfileRegistry.COMMERCE_MODULE, null, "dev", true, 30); }
    private MavenRuntimeConfiguration runtime() {
        try {
            Path home = root.resolve("maven"); Files.createDirectories(home.resolve("boot")); Files.createDirectories(home.resolve("bin")); Files.createDirectories(home.resolve("lib/jansi-native"));
            Files.writeString(home.resolve("bin/m2.conf"), "x"); Files.writeString(home.resolve("boot/plexus-classworlds-x.jar"), "x");
            Path java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toRealPath();
            return MavenRuntimeConfiguration.fromTrustedPaths(java, home);
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
    private static String javaExecutable() { return Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString(); }
    private static RunStore.Lock awaitLock(Path root) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try { return new RunStore(root).acquireActiveLock(); }
            catch (ExecutionPlanningException ignored) { java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25)); }
        }
        throw new AssertionError("External JVM did not release the repository lock.");
    }
    private static final class ManualTimeoutScheduler implements TestRunCoordinator.TimeoutScheduler { @Override public java.util.concurrent.ScheduledFuture<?> schedule(Runnable task, int seconds) { return new java.util.concurrent.ScheduledThreadPoolExecutor(1).schedule(task, seconds, TimeUnit.SECONDS); } }
}
