package com.aqa.mcp.validation;

import java.util.List;
import java.util.Objects;

public record EvaluationContext(String module, RuleProfile profile, List<SourceUnit> moduleSources,
        List<ModuleProfile> reactorModules) {

    public EvaluationContext {
        Objects.requireNonNull(module, "module must not be null");
        Objects.requireNonNull(profile, "profile must not be null");
        moduleSources = List.copyOf(moduleSources == null ? List.of() : moduleSources);
        reactorModules = List.copyOf(reactorModules == null ? List.of() : reactorModules);
        if (module.isBlank()) {
            throw new IllegalArgumentException("module must not be blank.");
        }
    }
}
