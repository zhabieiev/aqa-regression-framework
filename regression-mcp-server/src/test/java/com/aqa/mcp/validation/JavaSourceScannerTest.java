package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaSourceScannerTest {

    @TempDir
    Path temporaryDirectory;

    /**
     * Guards against a regression where the parser's Java-21 language level was configured only on whichever
     * thread first triggered {@link JavaSourceScanner}'s class initialization, leaving every other thread to parse
     * at the parser library's default language level and fail on JAVA_21-only syntax (records, pattern-matching
     * instanceof, switch expressions) genuinely present in this repository's own sources. Class initialization is
     * forced on one freshly created thread, joined to completion, and the scan is then performed on a second,
     * separately created thread that has never touched the class before. Two dedicated threads are used, rather
     * than initializing on the test thread itself, so the guarantee holds by construction: it must not depend on
     * JUnit's test method execution order within this class, nor on Surefire fork or parallelism settings, neither
     * of which the POM pins — either could otherwise let some earlier test method already have initialized the
     * class on the Surefire thread, making this test pass even against the broken code by circumstance rather than
     * by what it actually verifies. This test's power also depends on {@code regression-core} actually containing
     * syntax the parser library's default language level cannot parse (records, pattern-matching {@code instanceof},
     * switch expressions); if that ceases to be true, this test would pass vacuously — including against the
     * pre-fix code — because a scan that never needs a JAVA_21-only feature cannot distinguish a correctly
     * configured parser from a misconfigured one.
     */
    @Test
    void scanSucceedsWhenPerformedOnAThreadThatNeverInitializedTheScannerClass() throws Exception {
        Thread initializingThread = new Thread(() -> {
            try {
                Class.forName(JavaSourceScanner.class.getName());
            }
            catch (ClassNotFoundException exception) {
                throw new AssertionError(exception);
            }
        });
        initializingThread.start();
        initializingThread.join();

        Path repositoryRoot = realRepositoryRoot();
        AtomicReference<List<SourceUnit>> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread scanningThread = new Thread(() -> {
            try {
                result.set(JavaSourceScanner.scan(repositoryRoot, "regression-core"));
            }
            catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        scanningThread.start();
        scanningThread.join();

        if (failure.get() != null) {
            throw new AssertionError("Scanning regression-core on a separate thread failed", failure.get());
        }
        assertThat(result.get()).isNotEmpty();
    }

    /** Walks up from the test JVM's working directory to find the real reactor root, mirroring
     * {@code ArchitectureToolTest.realRepositoryRoot()} (not reusable directly: that method is private to its own
     * class). */
    private static Path realRepositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml")) && Files.isDirectory(candidate.resolve("regression-mcp-server"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Unable to resolve the real reactor root from the working directory: " + Path.of("").toAbsolutePath());
    }

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
