package com.aqa.mcp;

final class ModuleTypeClassifier {

    private ModuleTypeClassifier() {
    }

    static ModuleType classify(String moduleName) {
        return switch (moduleName) {
            case "regression-core" -> ModuleType.CORE;
            case "regression-petstore-api" -> ModuleType.API;
            case "regression-jhipster" -> ModuleType.API_UI;
            case "regression-nextjs-commerce" -> ModuleType.UI;
            case "regression-mcp-server" -> ModuleType.MCP;
            default -> ModuleType.UNKNOWN;
        };
    }
}
