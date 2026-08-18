package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaSourceScannerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void scansBothSrcMainJavaAndSrcTestJava() throws Exception {
        mainFile("com/aqa/example/pages/BasePage.java", "package com.aqa.example.pages;\nclass BasePage { }\n");
        testFile("com/aqa/example/steps/LoginSteps.java", "package com.aqa.example.steps;\nclass LoginSteps { }\n");

        List<SourceUnit> units = scan();

        assertThat(units).extracting(SourceUnit::relativePath).containsExactlyInAnyOrder(
                "example/src/main/java/com/aqa/example/pages/BasePage.java",
                "example/src/test/java/com/aqa/example/steps/LoginSteps.java");
        assertThat(units).allMatch(unit -> unit.module().equals("example"));
    }

    @Test
    void succeedsWithOnlySrcTestJavaWhenAModuleHasNoSrcMainJava() throws Exception {
        testFile("com/aqa/example/pages/BasePage.java", "package com.aqa.example.pages;\nclass BasePage { }\n");

        assertThat(scan()).extracting(SourceUnit::relativePath)
                .containsExactly("example/src/test/java/com/aqa/example/pages/BasePage.java");
    }

    @Test
    void ordersFilesByNormalizedRelativePath() throws Exception {
        mainFile("z/Z.java", "package z;\nclass Z { }\n");
        mainFile("a/A.java", "package a;\nclass A { }\n");

        assertThat(scan()).extracting(SourceUnit::relativePath).containsExactly(
                "example/src/main/java/a/A.java", "example/src/main/java/z/Z.java");
    }

    @Test
    void rejectsUnparsableSourceWithoutSkippingIt() throws Exception {
        mainFile("Broken.java", "class Broken { this is not valid java");

        assertThatIllegalArgumentException().isThrownBy(this::scan)
                .isInstanceOf(ValidationException.class)
                .extracting(exception -> ((ValidationException) exception).code())
                .isEqualTo("SOURCE_PARSE_ERROR");
    }

    @Test
    void rejectsOversizedSourceFiles() throws Exception {
        Path file = mainPath("Large.java");
        Files.writeString(file, "// " + "x".repeat((int) JavaSourceScanner.MAX_JAVA_FILE_BYTES + 1));

        assertThatIllegalArgumentException().isThrownBy(this::scan)
                .isInstanceOf(ValidationException.class)
                .extracting(exception -> ((ValidationException) exception).code())
                .isEqualTo("SOURCE_FILE_TOO_LARGE");
    }

    @Test
    void rejectsMoreThanTheJavaFileLimit() throws Exception {
        Path folder = mainPath("bulk/Marker.java").getParent();
        for (int index = 0; index <= JavaSourceScanner.MAX_JAVA_FILES; index++) {
            Files.writeString(folder.resolve("C" + index + ".java"), "package bulk;\nclass C" + index + " { }\n");
        }

        assertThatIllegalArgumentException().isThrownBy(this::scan)
                .isInstanceOf(ValidationException.class)
                .extracting(exception -> ((ValidationException) exception).code())
                .isEqualTo("SOURCE_FILE_LIMIT_EXCEEDED");
    }

    @Test
    void rejectsSymlinkedSourceFilesThatEscapeTheSourceRoot() throws Exception {
        Path outside = temporaryDirectory.resolve("outside/Outside.java");
        Files.createDirectories(outside.getParent());
        Files.writeString(outside, "package outside;\nclass Outside { }\n");
        Path escape = mainPath("Escape.java");
        try {
            Files.createSymbolicLink(escape, outside);
        }
        catch (java.nio.file.FileSystemException exception) {
            Assumptions.abort("Symbolic-link creation is not permitted by this Windows account.");
        }

        assertThatIllegalArgumentException().isThrownBy(this::scan)
                .isInstanceOf(ValidationException.class)
                .extracting(exception -> ((ValidationException) exception).code())
                .isEqualTo("SOURCE_PATH_VIOLATION");
    }

    @Test
    void rejectsAnUnknownModuleDirectory() {
        assertThatIllegalArgumentException().isThrownBy(() -> JavaSourceScanner.scan(temporaryDirectory, "missing"))
                .isInstanceOf(ValidationException.class)
                .extracting(exception -> ((ValidationException) exception).code())
                .isEqualTo("SOURCE_PATH_VIOLATION");
    }

    private List<SourceUnit> scan() {
        return JavaSourceScanner.scan(temporaryDirectory, "example");
    }

    private void mainFile(String relativePath, String content) throws Exception {
        Files.writeString(mainPath(relativePath), content);
    }

    private void testFile(String relativePath, String content) throws Exception {
        Path path = temporaryDirectory.resolve("example/src/test/java").resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    private Path mainPath(String relativePath) throws Exception {
        Path path = temporaryDirectory.resolve("example/src/main/java").resolve(relativePath);
        Files.createDirectories(path.getParent());
        return path;
    }
}
