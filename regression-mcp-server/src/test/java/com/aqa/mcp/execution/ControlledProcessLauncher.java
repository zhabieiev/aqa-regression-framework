package com.aqa.mcp.execution;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;

final class ControlledProcessLauncher implements MavenProcessLauncher {
    private final String mode; final String token = "execution-fixture-" + UUID.randomUUID();
    private final CountDownLatch launchEntered = new CountDownLatch(1);
    private final CountDownLatch launchRelease;
    private volatile Process process;
    private volatile MavenInvocation invocation;
    private volatile boolean forceDestroyCalled;
    ControlledProcessLauncher(String mode) { this(mode, false); }
    ControlledProcessLauncher(String mode, boolean holdLaunch) { this.mode = mode; this.launchRelease = holdLaunch ? new CountDownLatch(1) : null; }
    @Override public Process launch(MavenInvocation received) {
        invocation = received;
        launchEntered.countDown();
        if (launchRelease != null) {
            try { launchRelease.await(); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new ExecutionPlanningException("MAVEN_LAUNCH_FAILED", "Controlled fixture launch interrupted."); }
        }
        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        try {
            List<String> command = new ArrayList<>(List.of(java, "-cp",
                    System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                    ControlledProcessFixture.class.getName(), mode, token));
            if (mode.equals("FAIL_WITH_ARTIFACTS")) {
                command.add(argumentValue(received.arguments(), "-Dmcp.surefire.reportsDirectory="));
                command.add(argumentValue(received.arguments(), "-Dmcp.allure.resultsDirectory="));
            }
            Process launched = new ProcessBuilder(command).start();
            process = mode.equals("IGNORE_GRACEFUL_TERMINATION") ? new IgnoreGracefulTerminationProcess(launched) : launched;
            return process;
        }
        catch (IOException e) { throw new ExecutionPlanningException("MAVEN_LAUNCH_FAILED", "Controlled fixture launch failed."); }
    }

    private static String argumentValue(java.util.List<String> arguments, String prefix) {
        return arguments.stream().filter(argument -> argument.startsWith(prefix)).map(argument -> argument.substring(prefix.length())).findFirst()
                .orElseThrow(() -> new ExecutionPlanningException("MAVEN_LAUNCH_FAILED", "Controlled fixture launch is missing a required staging directory argument."));
    }
    Process process() { return process; }
    MavenInvocation invocation() { return invocation; }
    boolean forceDestroyCalled() { return forceDestroyCalled; }
    boolean awaitLaunch(long timeout, TimeUnit unit) throws InterruptedException { return launchEntered.await(timeout, unit); }
    void releaseLaunch() { if (launchRelease != null) launchRelease.countDown(); }

    private final class IgnoreGracefulTerminationProcess extends Process {
        private final Process delegate;
        private IgnoreGracefulTerminationProcess(Process delegate) { this.delegate = delegate; }
        @Override public java.io.OutputStream getOutputStream() { return delegate.getOutputStream(); }
        @Override public java.io.InputStream getInputStream() { return delegate.getInputStream(); }
        @Override public java.io.InputStream getErrorStream() { return delegate.getErrorStream(); }
        @Override public int waitFor() throws InterruptedException { return delegate.waitFor(); }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException { return delegate.waitFor(timeout, unit); }
        @Override public int exitValue() { return delegate.exitValue(); }
        @Override public void destroy() { /* Test fixture deliberately ignores graceful termination. */ }
        @Override public Process destroyForcibly() { forceDestroyCalled = true; delegate.destroyForcibly(); return this; }
        @Override public boolean isAlive() { return delegate.isAlive(); }
        @Override public long pid() { return delegate.pid(); }
        @Override public ProcessHandle toHandle() { return delegate.toHandle(); }
    }
}
