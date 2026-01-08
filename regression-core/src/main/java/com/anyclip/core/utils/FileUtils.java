package com.anyclip.core.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.GZIPOutputStream;

import static java.lang.String.format;

public final class FileUtils {

    public static String readFile(String input) {
        Path path = Path.of(input);
        try {
            return Files.readString(path);
        } catch (final IOException e) {
            throw new IllegalStateException(format("Failed to read file: %s", input), e);
        }
    }

    public static void compressToGzip(String input, Path target) {
        try (GZIPOutputStream gos = new GZIPOutputStream(new FileOutputStream(target.toFile()))) {
            gos.write(input.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(format("Unable to get File: %s", target), e);
        }
    }

    public static File createTempFile(String path) {
        try {
            return File.createTempFile(Paths.get(path).getFileName().toString(), null);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create or write temp file", e);
        }
    }
}