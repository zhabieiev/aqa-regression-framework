package com.aqa.mcp.validation;

import java.util.Objects;

public record Violation(String ruleId, String module, String file, int line, String message) {

    public Violation {
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(module, "module must not be null");
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (ruleId.isBlank() || module.isBlank() || file.isBlank() || message.isBlank() || line < 1) {
            throw new IllegalArgumentException("Violation fields are invalid.");
        }
    }
}
