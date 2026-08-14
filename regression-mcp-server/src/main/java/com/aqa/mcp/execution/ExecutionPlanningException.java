package com.aqa.mcp.execution;

public final class ExecutionPlanningException extends IllegalArgumentException {

    private final String code;

    public ExecutionPlanningException(String code, String message) {
        super(message);
        this.code = code;
    }

    ExecutionPlanningException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
