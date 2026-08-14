package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ControlledProcessFixtureTest {
    @TempDir Path directory;
    private Process process;
    @AfterEach void cleanup() { if (process != null && process.isAlive()) process.destroyForcibly(); }
    @Test void controlledPassAndFailHaveExpectedExitCodes() throws Exception { process = new ControlledProcessLauncher("PASS").launch(invocation()); assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero(); process = new ControlledProcessLauncher("FAIL").launch(invocation()); assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isEqualTo(7); }
    @Test void drainsLargeConcurrentOutputWithoutDeadlockAndRetainsBoundedTails() throws Exception { process = new ControlledProcessLauncher("LARGE_OUTPUT").launch(invocation()); BoundedLogDrainer out = new BoundedLogDrainer(process.getInputStream(), directory.resolve("out.log")); BoundedLogDrainer err = new BoundedLogDrainer(process.getErrorStream(), directory.resolve("err.log")); Thread one = Thread.ofVirtual().start(out); Thread two = Thread.ofVirtual().start(err); assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue(); one.join(); two.join(); assertThat(out.tail()).hasSize(BoundedLogDrainer.TAIL_LIMIT); assertThat(err.tail()).hasSize(BoundedLogDrainer.TAIL_LIMIT); assertThat(out.bytes()).isGreaterThan(BoundedLogDrainer.TAIL_LIMIT); assertThat(err.bytes()).isGreaterThan(BoundedLogDrainer.TAIL_LIMIT); }
    private MavenInvocation invocation() { return new MavenInvocation(Path.of("java"), directory, java.util.List.of("ignored")); }
}
