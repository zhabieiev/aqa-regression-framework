package com.aqa.mcp.validation;

import java.util.List;

public record ValidatedValidationScope(List<String> modules) {

    public ValidatedValidationScope {
        modules = List.copyOf(modules == null ? List.of() : modules);
    }
}
