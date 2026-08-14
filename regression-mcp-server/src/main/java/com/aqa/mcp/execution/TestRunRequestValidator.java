package com.aqa.mcp.execution;

import java.util.List;

import io.cucumber.tagexpressions.TagExpressionParser;

public final class TestRunRequestValidator {

    public static final int MIN_TIMEOUT_SECONDS = 30;
    public static final int MAX_TIMEOUT_SECONDS = 1800;
    public static final int MAX_TAG_EXPRESSION_LENGTH = 1024;
    private static final String NON_WIP_FILTER = "not @wip";

    private final List<String> declaredModules;

    public TestRunRequestValidator(List<String> declaredModules) {
        this.declaredModules = declaredModules == null ? List.of() : List.copyOf(declaredModules);
    }

    /**
     * Applies validation in this fixed order: request, module, environment, headless, timeout, then tags.
     * A caller can therefore receive only the first applicable structured error.
     */
    public ValidatedTestRunRequest validate(StartTestRunRequest request) {
        if (request == null) {
            throw invalidArguments("A test run request is required.");
        }

        ExecutionProfile profile = ExecutionProfileRegistry.requireProfile(request.module(), declaredModules);
        validateEnvironment(profile, request.environment());
        boolean headless = validateHeadless(profile, request.headless());
        int timeout = validateTimeout(request.timeoutSeconds());
        String effectiveTags = effectiveTagExpression(request.tags());
        return ValidatedTestRunRequest.of(profile, request.environment(), headless, timeout, effectiveTags);
    }

    private static void validateEnvironment(ExecutionProfile profile, String environment) {
        if (environment == null || !profile.environments().contains(environment)) {
            throw unsupportedCapability("The selected environment is not supported by this module.");
        }
    }

    private static boolean validateHeadless(ExecutionProfile profile, Boolean headless) {
        if (headless == null) {
            throw invalidArguments("headless must be a boolean.");
        }
        if (!profile.supportsHeadless()) {
            throw unsupportedCapability("Headless execution is not supported by this module.");
        }
        return headless;
    }

    private static int validateTimeout(Integer timeoutSeconds) {
        if (timeoutSeconds == null || timeoutSeconds < MIN_TIMEOUT_SECONDS || timeoutSeconds > MAX_TIMEOUT_SECONDS) {
            throw new ExecutionPlanningException("INVALID_TIMEOUT", "timeoutSeconds must be between 30 and 1800.");
        }
        return timeoutSeconds;
    }

    private static String effectiveTagExpression(String tags) {
        if (tags == null) {
            return NON_WIP_FILTER;
        }
        if (tags.isBlank() || tags.length() > MAX_TAG_EXPRESSION_LENGTH) {
            throw new ExecutionPlanningException("INVALID_TAG_EXPRESSION", "tags must be a non-blank expression of at most 1024 characters.");
        }
        parse(tags);
        String effective = "(" + tags + ") and " + NON_WIP_FILTER;
        parse(effective);
        return effective;
    }

    private static void parse(String expression) {
        try {
            TagExpressionParser.parse(expression);
        }
        catch (RuntimeException exception) {
            throw new ExecutionPlanningException("INVALID_TAG_EXPRESSION", "Invalid tag expression.");
        }
    }

    private static ExecutionPlanningException unsupportedCapability(String message) {
        return new ExecutionPlanningException("UNSUPPORTED_CAPABILITY", message);
    }

    private static ExecutionPlanningException invalidArguments(String message) {
        return new ExecutionPlanningException("INVALID_ARGUMENTS", message);
    }
}
