package com.aqa.mcp.validation;

import java.util.Objects;

import com.github.javaparser.ast.CompilationUnit;

public record SourceUnit(String module, String relativePath, CompilationUnit unit) {

    public SourceUnit {
        Objects.requireNonNull(module, "module must not be null");
        Objects.requireNonNull(relativePath, "relativePath must not be null");
        Objects.requireNonNull(unit, "unit must not be null");
        if (module.isBlank() || relativePath.isBlank()) {
            throw new IllegalArgumentException("module and relativePath must not be blank.");
        }
    }
}
