package com.aqa.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryRootResolverTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsMissingEnvironmentVariable() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RepositoryRootResolver.resolve(Map.of()))
                .withMessage("REGRESSION_ROOT must be set.");
    }

    @Test
    void rejectsRootThatDoesNotExist() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RepositoryRootResolver.resolve(Map.of(
                        RepositoryRootResolver.ENVIRONMENT_VARIABLE,
                        temporaryDirectory.resolve("missing").toString())))
                .withMessage("REGRESSION_ROOT does not exist.");
    }

    @Test
    void rejectsRootThatIsNotADirectory() throws Exception {
        Path file = Files.createFile(temporaryDirectory.resolve("not-a-directory"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> RepositoryRootResolver.resolve(Map.of(
                        RepositoryRootResolver.ENVIRONMENT_VARIABLE, file.toString())))
                .withMessage("REGRESSION_ROOT must identify a directory.");
    }

    @Test
    void rejectsRootWithoutPomXml() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RepositoryRootResolver.resolve(Map.of(
                        RepositoryRootResolver.ENVIRONMENT_VARIABLE, temporaryDirectory.toString())))
                .withMessage("REGRESSION_ROOT must contain the root pom.xml.");
    }

    @Test
    void normalizesAValidRoot() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project/>");

        RepositoryRoot root = RepositoryRootResolver.resolve(Map.of(
                RepositoryRootResolver.ENVIRONMENT_VARIABLE, temporaryDirectory.toString()));

        assertThat(root.path()).isEqualTo(temporaryDirectory.toRealPath());
    }
}
