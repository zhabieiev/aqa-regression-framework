package com.aqa.mcp.validation;

public final class ValidationException extends IllegalArgumentException {

    private final String code;

    public ValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    ValidationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
