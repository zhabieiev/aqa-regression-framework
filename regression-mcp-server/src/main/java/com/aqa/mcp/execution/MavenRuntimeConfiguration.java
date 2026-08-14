package com.aqa.mcp.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

public final class MavenRuntimeConfiguration {

    private final Path javaExecutable;
    private final Path mavenHome;
    private final Path classworldsJar;
    private final Path classworldsConfiguration;
    private final Path jansiNativePath;

    private MavenRuntimeConfiguration(Path javaExecutable, Path mavenHome, Path classworldsJar,
            Path classworldsConfiguration, Path jansiNativePath) {
        this.javaExecutable = javaExecutable;
        this.mavenHome = mavenHome;
        this.classworldsJar = classworldsJar;
        this.classworldsConfiguration = classworldsConfiguration;
        this.jansiNativePath = jansiNativePath;
    }

    /**
     * Creates a configuration only from server-owned paths supplied by a future runtime-configuration loader.
     * This method validates filesystem shape and resolves symlinks; it does not establish that a caller obtained
     * the paths from a trusted source. StartTestRunRequest never carries runtime paths.
     */
    static MavenRuntimeConfiguration fromTrustedPaths(Path configuredJava, Path configuredMavenHome) {
        try {
            Path java = realRegularFile(configuredJava);
            Path home = realDirectory(configuredMavenHome);
            Path configuration = realRegularFile(home.resolve("bin").resolve("m2.conf"));
            Path boot = realDirectory(home.resolve("boot"));
            Path jansi = realDirectory(home.resolve("lib").resolve("jansi-native"));
            Path classworlds = classworldsJar(boot);
            if (!configuration.startsWith(home) || !jansi.startsWith(home) || !classworlds.startsWith(home)) {
                throw unavailable();
            }
            return new MavenRuntimeConfiguration(java, home, classworlds, configuration, jansi);
        }
        catch (IOException exception) {
            throw unavailable();
        }
    }

    public Path javaExecutable() {
        return javaExecutable;
    }

    public Path mavenHome() {
        return mavenHome;
    }

    public Path classworldsJar() {
        return classworldsJar;
    }

    public Path classworldsConfiguration() {
        return classworldsConfiguration;
    }

    public Path jansiNativePath() {
        return jansiNativePath;
    }

    private static Path classworldsJar(Path boot) throws IOException {
        try (Stream<Path> files = Files.list(boot)) {
            java.util.List<Path> candidates = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("plexus-classworlds-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (candidates.size() != 1) throw unavailable();
            return toRealPath(candidates.getFirst());
        }
    }

    private static Path realRegularFile(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw unavailable();
        }
        return path.toRealPath();
    }

    private static Path realDirectory(Path path) throws IOException {
        if (path == null || !Files.isDirectory(path)) {
            throw unavailable();
        }
        return path.toRealPath();
    }

    private static Path toRealPath(Path path) {
        try {
            return path.toRealPath();
        }
        catch (IOException exception) {
            throw unavailable();
        }
    }

    private static ExecutionPlanningException unavailable() {
        return new ExecutionPlanningException("MAVEN_RUNTIME_UNAVAILABLE", "Trusted Maven runtime configuration is unavailable.");
    }
}
