package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoundedLogDrainerLimitTest {
    @TempDir Path directory;

    @Test
    void concurrentlyDrainsBeyondSixteenMiBWithoutBlockingAndRetainsFinalTail() throws Exception {
        Process process = new ControlledProcessLauncher("LARGE_OUTPUT").launch(invocation());
        BoundedLogDrainer stdout = new BoundedLogDrainer(process.getInputStream(), directory.resolve("stdout.log"));
        BoundedLogDrainer stderr = new BoundedLogDrainer(process.getErrorStream(), directory.resolve("stderr.log"));
        Thread out = Thread.ofVirtual().start(stdout);
        Thread err = Thread.ofVirtual().start(stderr);

        assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
        out.join(); err.join();

        assertThat(Files.size(directory.resolve("stdout.log"))).isEqualTo(BoundedLogDrainer.FILE_LIMIT);
        assertThat(Files.size(directory.resolve("stderr.log"))).isEqualTo(BoundedLogDrainer.FILE_LIMIT);
        assertThat(stdout.bytes()).isEqualTo(BoundedLogDrainer.FILE_LIMIT);
        assertThat(stderr.bytes()).isEqualTo(BoundedLogDrainer.FILE_LIMIT);
        assertThat(stdout.observedBytes()).isGreaterThan(BoundedLogDrainer.FILE_LIMIT);
        assertThat(stderr.observedBytes()).isGreaterThan(BoundedLogDrainer.FILE_LIMIT);
        assertThat(stdout.droppedBytes()).isEqualTo(stdout.observedBytes() - BoundedLogDrainer.FILE_LIMIT);
        assertThat(stderr.droppedBytes()).isEqualTo(stderr.observedBytes() - BoundedLogDrainer.FILE_LIMIT);
        assertThat(stdout.truncated()).isTrue();
        assertThat(stderr.truncated()).isTrue();
        assertThat(new String(stdout.tail(), StandardCharsets.UTF_8)).endsWith("TAIL-OUT");
        assertThat(new String(stderr.tail(), StandardCharsets.UTF_8)).endsWith("TAIL-ERR");
    }

    private MavenInvocation invocation() { return new MavenInvocation(Path.of("java"), directory, java.util.List.of("ignored")); }
}
