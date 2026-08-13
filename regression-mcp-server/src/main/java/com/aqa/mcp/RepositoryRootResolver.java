package com.aqa.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class RepositoryRootResolver {

    static final String ENVIRONMENT_VARIABLE = "REGRESSION_ROOT";

    private RepositoryRootResolver() {
    }

    static RepositoryRoot resolve(Map<String, String> environment) {
        String configuredRoot = environment.get(ENVIRONMENT_VARIABLE);
        if (configuredRoot == null || configuredRoot.isBlank()) {
            throw new IllegalArgumentException("REGRESSION_ROOT must be set.");
        }
        return resolve(Path.of(configuredRoot));
    }

    static RepositoryRoot resolve(Path configuredRoot) {
        if (!Files.exists(configuredRoot)) {
            throw new IllegalArgumentException("REGRESSION_ROOT does not exist.");
        }

        final Path normalizedRoot;
        try {
            normalizedRoot = configuredRoot.toRealPath();
        }
        catch (IOException exception) {
            throw new IllegalArgumentException("REGRESSION_ROOT cannot be resolved.", exception);
        }

        if (!Files.isDirectory(normalizedRoot)) {
            throw new IllegalArgumentException("REGRESSION_ROOT must identify a directory.");
        }
        if (!Files.isRegularFile(normalizedRoot.resolve("pom.xml"))) {
            throw new IllegalArgumentException("REGRESSION_ROOT must contain the root pom.xml.");
        }

        return new RepositoryRoot(normalizedRoot);
    }
}
