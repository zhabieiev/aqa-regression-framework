package com.aqa.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

record FrameworkOverview(String root) {

    private static final String NAME = "regression";
    private static final String JAVA_VERSION = "21";
    private static final String BUILD_TOOL = "Maven";
    private static final String AVAILABILITY = "AVAILABLE";

    static FrameworkOverview forRoot(RepositoryRoot repositoryRoot) {
        return new FrameworkOverview(repositoryRoot.displayPath());
    }

    Map<String, Object> asToolOutput() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", NAME);
        data.put("root", root);
        data.put("javaVersion", JAVA_VERSION);
        data.put("buildTool", BUILD_TOOL);
        data.put("availability", AVAILABILITY);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", "ok");
        output.put("data", Map.copyOf(data));
        return Map.copyOf(output);
    }
}
