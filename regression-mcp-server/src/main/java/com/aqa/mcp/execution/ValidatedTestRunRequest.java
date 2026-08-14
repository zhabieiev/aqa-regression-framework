package com.aqa.mcp.execution;

import java.util.Objects;

public final class ValidatedTestRunRequest {

    private final ExecutionProfile profile;
    private final String environment;
    private final boolean headless;
    private final int timeoutSeconds;
    private final String effectiveTagExpression;

    private ValidatedTestRunRequest(ExecutionProfile profile, String environment, boolean headless, int timeoutSeconds,
            String effectiveTagExpression) {
        this.profile = Objects.requireNonNull(profile, "profile must not be null");
        this.environment = Objects.requireNonNull(environment, "environment must not be null");
        this.headless = headless;
        this.timeoutSeconds = timeoutSeconds;
        this.effectiveTagExpression = Objects.requireNonNull(effectiveTagExpression,
                "effectiveTagExpression must not be null");
    }

    static ValidatedTestRunRequest of(ExecutionProfile profile, String environment, boolean headless, int timeoutSeconds,
            String effectiveTagExpression) {
        return new ValidatedTestRunRequest(profile, environment, headless, timeoutSeconds, effectiveTagExpression);
    }

    public ExecutionProfile profile() {
        return profile;
    }

    public String environment() {
        return environment;
    }

    public boolean headless() {
        return headless;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    public String effectiveTagExpression() {
        return effectiveTagExpression;
    }
}
