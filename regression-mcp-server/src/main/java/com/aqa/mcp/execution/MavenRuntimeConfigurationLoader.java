package com.aqa.mcp.execution;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Loads execution-only runtime paths from server configuration, never from an MCP request. */
public final class MavenRuntimeConfigurationLoader {
    public static final String MAVEN_HOME_ENVIRONMENT_VARIABLE = "REGRESSION_MAVEN_HOME";

    private MavenRuntimeConfigurationLoader() { }

    public static MavenRuntimeConfiguration load(Map<String, String> environment) {
        try {
            if (Runtime.version().feature() != 21) throw unavailable();
            String configuredHome = environment == null ? null : environment.get(MAVEN_HOME_ENVIRONMENT_VARIABLE);
            if (configuredHome == null || configuredHome.isBlank()) throw unavailable();
            Path javaHome = Path.of(System.getProperty("java.home")).toRealPath();
            String executable = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java";
            Path java = javaHome.resolve("bin").resolve(executable).toRealPath();
            if (!Files.isRegularFile(java) || !java.startsWith(javaHome)) throw unavailable();
            Path mavenHome = Path.of(configuredHome);
            if (!mavenHome.isAbsolute()) throw unavailable();
            return MavenRuntimeConfiguration.fromTrustedPaths(java, mavenHome);
        } catch (RuntimeException | java.io.IOException exception) {
            throw unavailable();
        }
    }

    private static ExecutionPlanningException unavailable() {
        return new ExecutionPlanningException("MAVEN_RUNTIME_UNAVAILABLE", "Trusted Maven runtime configuration is unavailable.");
    }
}
