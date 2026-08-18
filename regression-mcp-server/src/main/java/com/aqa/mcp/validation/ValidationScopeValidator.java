package com.aqa.mcp.validation;

import java.util.List;
import java.util.Map;

public final class ValidationScopeValidator {

    private ValidationScopeValidator() {
    }

    /**
     * Applies validation in this fixed order: request, profile format, module, then module/profile agreement.
     * A caller can therefore receive only the first applicable structured error.
     */
    public static ValidatedValidationScope validate(ValidationScopeRequest request, List<String> declaredModules,
            Map<String, String> moduleTypeByName) {
        if (request == null) {
            throw invalidArguments("A validation scope request is required.");
        }
        List<String> modules = declaredModules == null ? List.of() : List.copyOf(declaredModules);
        Map<String, String> types = moduleTypeByName == null ? Map.of() : Map.copyOf(moduleTypeByName);
        RuleProfile requestedProfile = parseProfile(request.profile());

        if (request.module() != null) {
            return validateSingleModule(request.module(), requestedProfile, modules, types);
        }
        if (requestedProfile == null) {
            return new ValidatedValidationScope(modules);
        }
        return new ValidatedValidationScope(modules.stream()
                .filter(module -> RuleProfileResolver.resolve(types.get(module)) == requestedProfile)
                .toList());
    }

    private static ValidatedValidationScope validateSingleModule(String module, RuleProfile requestedProfile,
            List<String> declaredModules, Map<String, String> moduleTypeByName) {
        if (module.isBlank() || !declaredModules.contains(module)) {
            throw new ValidationException("UNKNOWN_MODULE", "Module is not declared in the root pom.xml.");
        }
        RuleProfile resolved = RuleProfileResolver.resolve(moduleTypeByName.get(module));
        if (requestedProfile != null && requestedProfile != resolved) {
            throw invalidArguments("The requested module's resolved profile does not match the requested profile filter.");
        }
        return new ValidatedValidationScope(List.of(module));
    }

    private static RuleProfile parseProfile(String profile) {
        if (profile == null) {
            return null;
        }
        try {
            return RuleProfile.valueOf(profile);
        }
        catch (IllegalArgumentException exception) {
            throw invalidArguments("profile must be one of the known rule profiles.");
        }
    }

    private static ValidationException invalidArguments(String message) {
        return new ValidationException("INVALID_ARGUMENTS", message);
    }
}
