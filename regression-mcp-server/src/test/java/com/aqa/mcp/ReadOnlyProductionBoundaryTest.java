package com.aqa.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ReadOnlyProductionBoundaryTest {

    @Test
    void productionSourcesDoNotContainWriteProcessOrNetworkBoundaries() throws IOException {
        Path productionSources = Path.of("src", "main", "java", "com", "aqa", "mcp");
        try (Stream<Path> paths = Files.walk(productionSources)) {
            for (Path source : paths.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !path.startsWith(productionSources.resolve("execution"))).toList()) {
                String content = Files.readString(source);
                assertThat(content).as("read-only production source %s", source)
                        .doesNotContain("ProcessBuilder", "Runtime.getRuntime().exec", "java.net", "HttpClient", "Socket",
                                "URLConnection", "Files.write", "Files.create", "Files.delete", "Files.move", "Files.copy",
                                "FileOutputStream", "FileWriter", "PrintWriter", "cmd.exe", "/bin/sh");
            }
        }
    }

    @Test
    void executionBoundariesAreNarrowAndNeverUseAShell() throws IOException {
        Path execution = Path.of("src", "main", "java", "com", "aqa", "mcp", "execution");
        try (Stream<Path> paths = Files.walk(execution)) {
            var sources = paths.filter(path -> path.getFileName().toString().endsWith(".java")).toList();
            assertThat(sources.stream().filter(path -> read(path).contains("ProcessBuilder")).map(path -> path.getFileName().toString()))
                    .containsExactly("DirectMavenProcessLauncher.java");
            for (Path source : sources) assertThat(read(source)).as("execution source %s", source)
                    .doesNotContain("cmd.exe", "powershell", "Runtime.getRuntime().exec", "taskkill", "/bin/sh", "bash");
        }
    }

    private static String read(Path source) { try { return Files.readString(source); } catch (IOException e) { throw new IllegalStateException(e); } }
}
