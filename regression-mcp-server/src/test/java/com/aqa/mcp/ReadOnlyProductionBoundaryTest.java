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
            for (Path source : paths.filter(path -> path.getFileName().toString().endsWith(".java")).toList()) {
                String content = Files.readString(source);
                assertThat(content).as("read-only production source %s", source)
                        .doesNotContain("ProcessBuilder", "Runtime.getRuntime().exec", "java.net", "HttpClient", "Socket",
                                "URLConnection", "Files.write", "Files.create", "Files.delete", "Files.move", "Files.copy",
                                "FileOutputStream", "FileWriter", "PrintWriter", "cmd.exe", "/bin/sh");
            }
        }
    }
}
