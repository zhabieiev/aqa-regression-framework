package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ValidationScopeValidatorTest {

    private static final List<String> DECLARED_MODULES = List.of(
            "regression-core",
            "regression-petstore-api",
            "regression-jhipster",
            "regression-nextjs-commerce",
            "regression-mcp-server");
    private static final Map<String, String> MODULE_TYPES = Map.of(
            "regression-core", "CORE",
            "regression-petstore-api", "API",
            "regression-jhipster", "API_UI",
            "regression-nextjs-commerce", "UI",
            "regression-mcp-server", "MCP");

    @Test
    void rejectsANullRequest() {
        assertCode("INVALID_ARGUMENTS", () -> ValidationScopeValidator.validate(null, DECLARED_MODULES, MODULE_TYPES));
    }

    @Test
    void resolvesAValidSingleModule() {
        ValidatedValidationScope scope = ValidationScopeValidator.validate(
                new ValidationScopeRequest("regression-jhipster", null), DECLARED_MODULES, MODULE_TYPES);

        assertThat(scope.modules()).containsExactly("regression-jhipster");
    }

    @Test
    void rejectsAnUndeclaredModule() {
        assertCode("UNKNOWN_MODULE", () -> ValidationScopeValidator.validate(
                new ValidationScopeRequest("not-declared", null), DECLARED_MODULES, MODULE_TYPES));
    }

    @Test
    void rejectsABlankModule() {
        assertCode("UNKNOWN_MODULE", () -> ValidationScopeValidator.validate(
                new ValidationScopeRequest(" ", null), DECLARED_MODULES, MODULE_TYPES));
    }

    @Test
    void rejectsAnUnrecognizedProfileValue() {
        assertCode("INVALID_ARGUMENTS", () -> ValidationScopeValidator.validate(
                new ValidationScopeRequest(null, "NOT_A_PROFILE"), DECLARED_MODULES, MODULE_TYPES));
    }

    @Test
    void rejectsAModuleWhoseResolvedProfileDoesNotMatchTheRequestedProfile() {
        assertCode("INVALID_ARGUMENTS", () -> ValidationScopeValidator.validate(
                new ValidationScopeRequest("regression-jhipster", "UI"), DECLARED_MODULES, MODULE_TYPES));
    }

    @Test
    void acceptsAModuleWhoseResolvedProfileMatchesTheRequestedProfile() {
        ValidatedValidationScope scope = ValidationScopeValidator.validate(
                new ValidationScopeRequest("regression-jhipster", "API_UI"), DECLARED_MODULES, MODULE_TYPES);

        assertThat(scope.modules()).containsExactly("regression-jhipster");
    }

    @Test
    void filtersAllDeclaredModulesByProfileWhenNoModuleIsGiven() {
        ValidatedValidationScope scope = ValidationScopeValidator.validate(
                new ValidationScopeRequest(null, "API"), DECLARED_MODULES, MODULE_TYPES);

        assertThat(scope.modules()).containsExactly("regression-petstore-api");
    }

    @Test
    void resolvesEveryDeclaredModuleWhenNoFiltersAreGiven() {
        ValidatedValidationScope scope = ValidationScopeValidator.validate(
                new ValidationScopeRequest(null, null), DECLARED_MODULES, MODULE_TYPES);

        assertThat(scope.modules()).containsExactlyElementsOf(DECLARED_MODULES);
    }

    private static void assertCode(String expectedCode, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ValidationException.class)
                .extracting(exception -> ((ValidationException) exception).code())
                .isEqualTo(expectedCode);
    }
}
