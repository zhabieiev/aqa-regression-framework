package com.aqa.mcp.execution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Test-classpath-only factory. No production MCP request can select this launcher or runtime. */
public final class ControlledCoordinatorFactory {
    private ControlledCoordinatorFactory() { }

    public static TestRunCoordinator waitingCoordinator(Path root) {
        return new TestRunCoordinator(root, () -> new TestRunRequestValidator(List.of(ExecutionProfileRegistry.COMMERCE_MODULE)),
                new ControlledProcessLauncher("WAIT"), new TestTimeouts(), ignored -> runtime(root));
    }

    /** Produces a genuine terminal FAILED run with real, published Surefire/Allure capture data, for exercising
     * Gate 14.4's failure-artifact tools end-to-end over real STDIO without a real Maven/browser dependency. */
    public static TestRunCoordinator failingCoordinatorWithArtifacts(Path root) {
        return new TestRunCoordinator(root, () -> new TestRunRequestValidator(List.of(ExecutionProfileRegistry.COMMERCE_MODULE)),
                new ControlledProcessLauncher("FAIL_WITH_ARTIFACTS"), new TestTimeouts(), ignored -> runtime(root));
    }

    private static MavenRuntimeConfiguration runtime(Path root) {
        try {
            Path home = root.resolve("test-maven");
            Files.createDirectories(home.resolve("boot")); Files.createDirectories(home.resolve("bin"));
            Files.createDirectories(home.resolve("lib/jansi-native"));
            Files.writeString(home.resolve("bin/m2.conf"), "test"); Files.writeString(home.resolve("boot/plexus-classworlds-test.jar"), "test");
            String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
            return MavenRuntimeConfiguration.fromTrustedPaths(Path.of(System.getProperty("java.home"), "bin", executable), home);
        } catch (Exception exception) { throw new AssertionError("Unable to create controlled runtime", exception); }
    }

    private static final class TestTimeouts implements TestRunCoordinator.TimeoutScheduler {
        private final java.util.concurrent.ScheduledExecutorService executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
                runnable -> new Thread(runnable, "controlled-mcp-test-timeout"));
        @Override public java.util.concurrent.ScheduledFuture<?> schedule(Runnable task, int seconds) {
            return executor.schedule(task, seconds, java.util.concurrent.TimeUnit.SECONDS);
        }
        @Override public void close() { executor.shutdownNow(); }
    }
}
