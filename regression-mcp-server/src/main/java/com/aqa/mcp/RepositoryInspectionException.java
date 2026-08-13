package com.aqa.mcp;

final class RepositoryInspectionException extends IllegalArgumentException {

    private final String code;

    RepositoryInspectionException(String code, String message) {
        super(message);
        this.code = code;
    }

    RepositoryInspectionException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    String code() {
        return code;
    }
}
