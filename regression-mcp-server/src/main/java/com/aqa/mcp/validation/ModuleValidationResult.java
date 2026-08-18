package com.aqa.mcp.validation;

import java.util.List;
import java.util.Objects;

public record ModuleValidationResult(String module, RuleProfile profile, List<String> rulesApplied,
        List<Violation> violations, boolean truncated) {

    public ModuleValidationResult {
        Objects.requireNonNull(module, "module must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        rulesApplied = List.copyOf(rulesApplied == null ? List.of() : rulesApplied);
        violations = List.copyOf(violations == null ? List.of() : violations);
        if (module.isBlank()) {
            throw new IllegalArgumentException("module must not be blank.");
        }
    }
}
