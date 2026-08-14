package com.aqa.mcp.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MavenInvocationFactory {

    private static final String CLASSWORLDS_LAUNCHER = "org.codehaus.plexus.classworlds.launcher.Launcher";

    private MavenInvocationFactory() {
    }

    public static MavenInvocation create(MavenRuntimeConfiguration runtime, Path repositoryRoot,
            ValidatedTestRunRequest request) {
        if (runtime == null) {
            throw unavailable();
        }
        if (request == null) {
            throw new ExecutionPlanningException("INVALID_ARGUMENTS", "A validated test run request is required.");
        }
        Path root = trustedRepositoryRoot(repositoryRoot);
        ExecutionProfile profile = request.profile();
        return new MavenInvocation(runtime.javaExecutable(), root, List.of(
                "--enable-native-access=ALL-UNNAMED",
                "-classpath", runtime.classworldsJar().toString(),
                "-Dclassworlds.conf=" + runtime.classworldsConfiguration(),
                "-Dmaven.home=" + runtime.mavenHome(),
                "-Dlibrary.jansi.path=" + runtime.jansiNativePath(),
                "-Dmaven.multiModuleProjectDirectory=" + root,
                CLASSWORLDS_LAUNCHER,
                "-f", profile.modulePomPath(),
                "test",
                "-Denv=" + request.environment(),
                "-Dui.headless=" + request.headless(),
                "-Dcucumber.filter.tags=" + request.effectiveTagExpression()));
    }

    private static Path trustedRepositoryRoot(Path repositoryRoot) {
        try {
            if (repositoryRoot == null || !Files.isDirectory(repositoryRoot)
                    || !Files.isRegularFile(repositoryRoot.resolve("pom.xml"))) {
                throw unavailable();
            }
            return repositoryRoot.toRealPath();
        }
        catch (IOException exception) {
            throw unavailable();
        }
    }

    private static ExecutionPlanningException unavailable() {
        return new ExecutionPlanningException("MAVEN_RUNTIME_UNAVAILABLE", "Trusted Maven runtime configuration is unavailable.");
    }
}
