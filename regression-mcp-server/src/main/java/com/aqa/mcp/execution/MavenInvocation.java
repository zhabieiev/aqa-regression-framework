package com.aqa.mcp.execution;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record MavenInvocation(Path javaExecutable, Path workingDirectory, List<String> arguments) {

    public MavenInvocation {
        Objects.requireNonNull(javaExecutable, "javaExecutable must not be null");
        Objects.requireNonNull(workingDirectory, "workingDirectory must not be null");
        arguments = List.copyOf(arguments);
        if (arguments.isEmpty()) {
            throw new IllegalArgumentException("Maven invocation arguments must not be empty.");
        }
    }
}
