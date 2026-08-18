package com.aqa.mcp.validation;

import java.util.List;

public record ValidationReport(List<ModuleValidationResult> modules) {

    public ValidationReport {
        modules = List.copyOf(modules == null ? List.of() : modules);
    }
}
