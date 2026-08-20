package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class TestRunRequestValidatorTest {

    private static final List<String> DECLARED_MODULES = List.of(
            "regression-core",
            "regression-petstore-api",
            "regression-jhipster",
            "regression-nextjs-commerce",
            "regression-mcp-server");
    private final TestRunRequestValidator validator = new TestRunRequestValidator(DECLARED_MODULES);

    @Test
    void exposesTheCommerceAndJhipsterExecutionProfiles() {
        assertThat(ExecutionProfileRegistry.profiles()).containsExactlyInAnyOrder(
                new ExecutionProfile("regression-nextjs-commerce", "regression-nextjs-commerce/pom.xml", List.of("dev"), true),
                new ExecutionProfile("regression-jhipster", "regression-jhipster/pom.xml", List.of("dev"), true));
        assertThatThrownBy(() -> ExecutionProfileRegistry.profiles().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ExecutionProfileRegistry.profiles().getFirst().environments().add("qa"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requireProfileSucceedsForJhipsterWithCorrectFields() {
        ExecutionProfile profile = ExecutionProfileRegistry.requireProfile("regression-jhipster", DECLARED_MODULES);

        assertThat(profile).isEqualTo(new ExecutionProfile(
                "regression-jhipster", "regression-jhipster/pom.xml", List.of("dev"), true));
        assertThat(profile.modulePomPath()).isEqualTo("regression-jhipster/pom.xml");
        assertThat(profile.environments()).containsExactly("dev");
        assertThat(profile.supportsHeadless()).isTrue();
    }

    @Test
    void rejectsUnknownAndNonExecutableDeclaredModules() {
        for (String module : List.of("unknown", "regression-core", "regression-mcp-server",
                "regression-petstore-api")) {
            assertCode("UNSUPPORTED_MODULE", () -> validator.validate(request(module, null, "dev", true, 30)));
        }
    }

    @Test
    void rejectsNullRequestsAndFailsClosedWhenCommerceIsNotDeclared() {
        assertCode("INVALID_ARGUMENTS", () -> validator.validate(null));
        assertCode("UNSUPPORTED_MODULE", () -> validator.validate(request(null, null, "dev", true, 30)));
        assertCode("UNSUPPORTED_MODULE", () -> validator.validate(request(" ", null, "dev", true, 30)));
        assertCode("UNSUPPORTED_MODULE", () -> new TestRunRequestValidator(List.of())
                .validate(request("regression-nextjs-commerce", null, "dev", true, 30)));
        assertCode("UNSUPPORTED_MODULE", () -> new TestRunRequestValidator(null)
                .validate(request("regression-nextjs-commerce", null, "dev", true, 30)));
    }

    @Test
    void defensivelyCopiesDeclaredModuleMembership() {
        List<String> modules = new ArrayList<>(List.of("regression-nextjs-commerce"));
        TestRunRequestValidator isolatedValidator = new TestRunRequestValidator(modules);
        modules.clear();

        assertThat(isolatedValidator.validate(request("regression-nextjs-commerce", null, "dev", true, 30)).profile().module())
                .isEqualTo("regression-nextjs-commerce");
    }

    @Test
    void validatesTheOnlySupportedEnvironmentAndHeadlessCapability() {
        assertThat(validator.validate(request("regression-nextjs-commerce", null, "dev", false, 30)).headless()).isFalse();
        assertCode("UNSUPPORTED_CAPABILITY",
                () -> validator.validate(request("regression-nextjs-commerce", null, "qa", true, 30)));
        assertCode("INVALID_ARGUMENTS",
                () -> validator.validate(request("regression-nextjs-commerce", null, "dev", null, 30)));
        assertCode("UNSUPPORTED_CAPABILITY",
                () -> validator.validate(request("regression-nextjs-commerce", null, null, true, 30)));
        assertCode("INVALID_TIMEOUT",
                () -> validator.validate(request("regression-nextjs-commerce", null, "dev", true, null)));
    }

    @Test
    void acceptsInclusiveTimeoutBoundsAndRejectsValuesOutsideThem() {
        assertThat(validator.validate(request("regression-nextjs-commerce", null, "dev", true, 30)).timeoutSeconds()).isEqualTo(30);
        assertThat(validator.validate(request("regression-nextjs-commerce", null, "dev", true, 1800)).timeoutSeconds()).isEqualTo(1800);
        assertCode("INVALID_TIMEOUT", () -> validator.validate(request("regression-nextjs-commerce", null, "dev", true, 29)));
        assertCode("INVALID_TIMEOUT", () -> validator.validate(request("regression-nextjs-commerce", null, "dev", true, 1801)));
    }

    @Test
    void ownsTheNonWipTagInvariant() {
        assertThat(validator.validate(request("regression-nextjs-commerce", null, "dev", true, 30)).effectiveTagExpression())
                .isEqualTo("not @wip");
        assertThat(validator.validate(request("regression-nextjs-commerce", "@cart", "dev", true, 30)).effectiveTagExpression())
                .isEqualTo("(@cart) and not @wip");
        assertThat(validator.validate(request("regression-nextjs-commerce", "@wip", "dev", true, 30)).effectiveTagExpression())
                .isEqualTo("(@wip) and not @wip");
        assertThat(validator.validate(request("regression-nextjs-commerce", "@wip or @cart", "dev", true, 30)).effectiveTagExpression())
                .isEqualTo("(@wip or @cart) and not @wip");
    }

    @Test
    void rejectsBlankOversizedAndMalformedTagExpressions() {
        assertCode("INVALID_TAG_EXPRESSION", () -> validator.validate(request("regression-nextjs-commerce", " ", "dev", true, 30)));
        assertCode("INVALID_TAG_EXPRESSION", () -> validator.validate(request("regression-nextjs-commerce", "@a".repeat(513), "dev", true, 30)));
        assertCode("INVALID_TAG_EXPRESSION", () -> validator.validate(request("regression-nextjs-commerce", "@cart and", "dev", true, 30)));
    }

    @Test
    void acceptsCompoundCucumberTagExpressions() {
        assertThat(validator.validate(request("regression-nextjs-commerce", "(@cart or @catalog) and not @slow", "dev", true, 30))
                .effectiveTagExpression()).isEqualTo("((@cart or @catalog) and not @slow) and not @wip");
    }

    @Test
    void appliesValidationInDocumentedOrder() {
        assertCode("UNSUPPORTED_MODULE", () -> validator.validate(request("regression-core", "@cart and", "qa", null, 29)));
        assertCode("UNSUPPORTED_CAPABILITY", () -> validator.validate(request("regression-nextjs-commerce", "@cart and", "qa", null, 29)));
        assertCode("INVALID_ARGUMENTS", () -> validator.validate(request("regression-nextjs-commerce", "@cart and", "dev", null, 29)));
        assertCode("INVALID_TIMEOUT", () -> validator.validate(request("regression-nextjs-commerce", "@cart and", "dev", true, 29)));
        assertCode("INVALID_TAG_EXPRESSION", () -> validator.validate(request("regression-nextjs-commerce", "@cart and", "dev", true, 30)));
    }

    @Test
    void exposesNoNormalConstructionPathForValidatedRequests() {
        assertThat(ValidatedTestRunRequest.class.getDeclaredConstructors())
                .allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers()));
        assertThat(ValidatedTestRunRequest.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("of"))
                .allMatch(method -> !Modifier.isPublic(method.getModifiers()));
        assertThat(MavenInvocationFactory.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("create"))
                .allMatch(method -> List.of(method.getParameterTypes()).contains(ValidatedTestRunRequest.class));
        assertThat(StartTestRunRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("module", "tags", "environment", "headless", "timeoutSeconds");
    }

    private static StartTestRunRequest request(String module, String tags, String environment, Boolean headless, Integer timeout) {
        return new StartTestRunRequest(module, tags, environment, headless, timeout);
    }

    private static void assertCode(String expectedCode, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ExecutionPlanningException.class)
                .extracting(exception -> ((ExecutionPlanningException) exception).code())
                .isEqualTo(expectedCode);
    }
}
