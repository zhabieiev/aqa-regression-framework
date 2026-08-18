package com.aqa.mcp.validation;

public final class RuleProfileResolver {

    private RuleProfileResolver() {
    }

    /**
     * moduleTypeName is the existing, untouched ModuleTypeClassifier's ModuleType.name() output, passed in as a
     * plain String because ModuleType is package-private in com.aqa.mcp and cannot be referenced from this package.
     * UNKNOWN maps to TEST_ONLY: both mean "no CLAUDE.md-defined production/API/UI role", so neither the
     * CORE/API/UI/API_UI/MCP-specific rules should fire for such a module.
     */
    public static RuleProfile resolve(String moduleTypeName) {
        if (moduleTypeName == null) {
            throw unknownModuleType(null);
        }
        return switch (moduleTypeName) {
            case "CORE" -> RuleProfile.CORE;
            case "API" -> RuleProfile.API;
            case "UI" -> RuleProfile.UI;
            case "API_UI" -> RuleProfile.API_UI;
            case "MCP" -> RuleProfile.MCP;
            case "UNKNOWN" -> RuleProfile.TEST_ONLY;
            default -> throw unknownModuleType(moduleTypeName);
        };
    }

    private static ValidationException unknownModuleType(String moduleTypeName) {
        return new ValidationException("UNKNOWN_MODULE_TYPE", "Unrecognized module classification: " + moduleTypeName);
    }
}
