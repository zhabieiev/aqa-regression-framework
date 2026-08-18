package com.aqa.mcp.validation;

import java.util.Arrays;
import java.util.List;

public enum RuleProfile {
    CORE, API, UI, API_UI, MCP, TEST_ONLY;

    static List<String> schemaValues() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }
}
