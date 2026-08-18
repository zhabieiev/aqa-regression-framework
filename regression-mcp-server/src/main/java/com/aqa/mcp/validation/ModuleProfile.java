package com.aqa.mcp.validation;

import java.util.Objects;

public record ModuleProfile(String module, RuleProfile profile, String basePackage) {

    public ModuleProfile {
        Objects.requireNonNull(module, "module must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        Objects.requireNonNull(basePackage, "basePackage must not be null");
        if (module.isBlank()) {
            throw new IllegalArgumentException("module must not be blank.");
        }
    }
}
