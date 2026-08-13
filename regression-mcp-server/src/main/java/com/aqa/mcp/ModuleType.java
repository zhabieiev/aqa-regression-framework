package com.aqa.mcp;

import java.util.Arrays;
import java.util.List;

enum ModuleType {
    CORE, UI, API, API_UI, MCP, UNKNOWN;

    static List<String> schemaValues() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
