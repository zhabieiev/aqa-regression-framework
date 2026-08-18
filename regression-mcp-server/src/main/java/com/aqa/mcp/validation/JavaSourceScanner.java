package com.aqa.mcp.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

/**
 * Walks a module's src/main/java and src/test/java trees and parses every .java file found. Some modules (e.g.
 * regression-nextjs-commerce) have no src/main/java at all, so both roots are scanned independently and either may
 * be absent without error.
 */
public final class JavaSourceScanner {

    static final long MAX_JAVA_FILE_BYTES = 1_048_576;
    static final int MAX_JAVA_FILES = 10_000;
    private static final List<String> SOURCE_ROOTS = List.of("src/main/java", "src/test/java");

    static {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    private JavaSourceScanner() {
    }

    public static List<SourceUnit> scan(Path repositoryRoot, String module) {
        if (module == null || module.isBlank()) {
            throw new ValidationException("INVALID_ARGUMENTS", "module must be a non-blank string.");
        }
        Path modulePath = repositoryRoot.resolve(module).normalize();
        if (!modulePath.startsWith(repositoryRoot) || !Files.isDirectory(modulePath)) {
            throw new ValidationException("SOURCE_PATH_VIOLATION", "Unable to inspect selected module: " + module + ".");
        }
        List<SourceUnit> units = new ArrayList<>();
        for (String sourceRootName : SOURCE_ROOTS) {
            units.addAll(scanSourceRoot(repositoryRoot, modulePath, module, sourceRootName));
        }
        return List.copyOf(units);
    }

    private static List<SourceUnit> scanSourceRoot(Path repositoryRoot, Path modulePath, String module, String sourceRootName) {
        Path sourceRoot = modulePath.resolve(sourceRootName).normalize();
        if (!sourceRoot.startsWith(modulePath) || !Files.exists(sourceRoot)) {
            return List.of();
        }
        try {
            Path realModule = modulePath.toRealPath();
            Path realSourceRoot = sourceRoot.toRealPath();
            if (!realModule.startsWith(repositoryRoot.toRealPath()) || !realSourceRoot.startsWith(realModule)
                    || !Files.isDirectory(realSourceRoot)) {
                throw error("SOURCE_PATH_VIOLATION", "Source root resolves outside the selected module: " + sourceRootName);
            }
            List<Path> files = files(sourceRoot, realSourceRoot);
            List<SourceUnit> units = new ArrayList<>();
            for (Path file : files) {
                units.add(parse(file, repositoryRoot, module));
            }
            return units;
        }
        catch (IOException exception) {
            throw new ValidationException("SOURCE_PATH_VIOLATION", "Unable to resolve source root: " + sourceRootName, exception);
        }
    }

    private static List<Path> files(Path root, Path realRoot) {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> walked = Files.walk(root)) {
            for (Path path : walked.toList()) {
                if (Files.isSymbolicLink(path)) {
                    validate(path, root, realRoot);
                }
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".java")) {
                    validate(path, root, realRoot);
                    result.add(path);
                    if (result.size() > MAX_JAVA_FILES) {
                        throw error("SOURCE_FILE_LIMIT_EXCEEDED", "Java source file limit exceeded: " + MAX_JAVA_FILES + ".");
                    }
                }
            }
        }
        catch (IOException exception) {
            throw new ValidationException("SOURCE_IO_ERROR", "Unable to inspect Java source files.", exception);
        }
        result.sort(Comparator.comparing(path -> display(root.relativize(path))));
        return result;
    }

    private static void validate(Path path, Path displayRoot, Path realRoot) {
        try {
            if (!path.toRealPath().startsWith(realRoot)) {
                throw error("SOURCE_PATH_VIOLATION", "Source path resolves outside the source root: "
                        + display(displayRoot.relativize(path)));
            }
        }
        catch (IOException exception) {
            throw new ValidationException("SOURCE_PATH_VIOLATION", "Unable to resolve source path: "
                    + display(displayRoot.relativize(path)), exception);
        }
    }

    private static SourceUnit parse(Path file, Path repositoryRoot, String module) {
        String relativePath = display(repositoryRoot.relativize(file));
        try {
            if (Files.size(file) > MAX_JAVA_FILE_BYTES) {
                throw error("SOURCE_FILE_TOO_LARGE", "Java source file exceeds " + MAX_JAVA_FILE_BYTES + " bytes: " + relativePath);
            }
            CompilationUnit unit = StaticJavaParser.parse(file);
            return new SourceUnit(module, relativePath, unit);
        }
        catch (IOException exception) {
            throw new ValidationException("SOURCE_IO_ERROR", "Unable to read Java source file: " + relativePath, exception);
        }
        catch (ParseProblemException exception) {
            throw new ValidationException("SOURCE_PARSE_ERROR", "Malformed Java source in " + relativePath + ".", exception);
        }
    }

    private static String display(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static ValidationException error(String code, String message) {
        return new ValidationException(code, message);
    }
}
