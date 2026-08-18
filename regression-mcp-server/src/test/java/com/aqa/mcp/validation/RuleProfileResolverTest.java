package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class RuleProfileResolverTest {

    @Test
    void resolvesEveryKnownModuleTypeNameToItsRuleProfile() {
        Map<String, RuleProfile> expected = Map.of(
                "CORE", RuleProfile.CORE,
                "API", RuleProfile.API,
                "UI", RuleProfile.UI,
                "API_UI", RuleProfile.API_UI,
                "MCP", RuleProfile.MCP,
                "UNKNOWN", RuleProfile.TEST_ONLY);

        expected.forEach((typeName, profile) -> assertThat(RuleProfileResolver.resolve(typeName)).isEqualTo(profile));
    }

    @Test
    void rejectsAnUnrecognizedModuleTypeName() {
        assertThatThrownBy(() -> RuleProfileResolver.resolve("NOT_A_REAL_TYPE"))
                .isInstanceOf(ValidationException.class)
                .extracting(exception -> ((ValidationException) exception).code())
                .isEqualTo("UNKNOWN_MODULE_TYPE");
    }

    @Test
    void rejectsANullModuleTypeName() {
        assertThatThrownBy(() -> RuleProfileResolver.resolve(null))
                .isInstanceOf(ValidationException.class)
                .extracting(exception -> ((ValidationException) exception).code())
                .isEqualTo("UNKNOWN_MODULE_TYPE");
    }
}
