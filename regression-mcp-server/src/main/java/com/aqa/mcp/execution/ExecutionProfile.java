package com.aqa.mcp.execution;

import java.util.List;
import java.util.Objects;

public record ExecutionProfile(String module, String modulePomPath, List<String> environments, boolean supportsHeadless) {

    public ExecutionProfile {
        Objects.requireNonNull(module, "module must not be null");
        Objects.requireNonNull(modulePomPath, "modulePomPath must not be null");
        environments = List.copyOf(environments);
        if (module.isBlank() || modulePomPath.isBlank() || environments.isEmpty()) {
            throw new IllegalArgumentException("Execution profile fields must not be blank.");
        }
    }
}
