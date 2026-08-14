package com.aqa.mcp.execution;

public record StartTestRunRequest(String module, String tags, String environment, Boolean headless,
        Integer timeoutSeconds) {
}
