package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;

class ModuleBoundaryRulesTest {

    private static final List<ModuleProfile> REACTOR_MODULES = List.of(
            new ModuleProfile("regression-core", RuleProfile.CORE, "com.aqa.core"),
            new ModuleProfile("regression-petstore-api", RuleProfile.API, "com.aqa.petstore"),
            new ModuleProfile("regression-jhipster", RuleProfile.API_UI, "com.aqa.jhipster"),
            new ModuleProfile("regression-nextjs-commerce", RuleProfile.UI, "com.aqa.nextjscommerce"),
            new ModuleProfile("regression-mcp-server", RuleProfile.MCP, "com.aqa.mcp"));

    private static final Map<String, ValidationRule> RULES_BY_ID = ModuleBoundaryRules.all().stream()
            .collect(java.util.stream.Collectors.toMap(ValidationRule::id, rule -> rule));

    @Test
    void mod001FlagsAnImportFromASiblingProductModule() {
        List<Violation> violations = evaluate("MOD-001", "regression-jhipster", """
                package com.aqa.jhipster.api.services;

                import com.aqa.petstore.api.SomePetstoreApi;

                class Consumer { }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("MOD-001");
        assertThat(violations.getFirst().line()).isEqualTo(3);
    }

    @Test
    void mod001AllowsDependingOnCoreWhichIsNotASibling() {
        List<Violation> violations = evaluate("MOD-001", "regression-jhipster", """
                package com.aqa.jhipster.api.services;

                import com.aqa.core.services.GeneralApiService;

                class Consumer { }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void mod002FlagsAnApiClientImportInCommerce() {
        List<Violation> violations = evaluate("MOD-002", "regression-nextjs-commerce", """
                package com.aqa.nextjscommerce.pages;

                import jakarta.ws.rs.core.UriBuilder;

                class Consumer { }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("MOD-002");
        assertThat(violations.getFirst().line()).isEqualTo(3);
    }

    @Test
    void mod002AllowsSeleniumImportsInCommerce() {
        List<Violation> violations = evaluate("MOD-002", "regression-nextjs-commerce", """
                package com.aqa.nextjscommerce.pages;

                import org.openqa.selenium.By;

                class Consumer { }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void mod003FlagsAnMcpServerImportOfCore() {
        List<Violation> violations = evaluate("MOD-003", "regression-mcp-server", """
                package com.aqa.mcp.validation;

                import com.aqa.core.services.GeneralApiService;

                class Consumer { }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("MOD-003");
        assertThat(violations.getFirst().line()).isEqualTo(3);
    }

    @Test
    void mod003AllowsMcpServerImportingItsOwnPackage() {
        List<Violation> violations = evaluate("MOD-003", "regression-mcp-server", """
                package com.aqa.mcp.validation;

                import com.aqa.mcp.execution.TestRunCoordinator;

                class Consumer { }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void mod004FlagsACoreImportOfAProductModule() {
        List<Violation> violations = evaluate("MOD-004", "regression-core", """
                package com.aqa.core.services;

                import com.aqa.jhipster.api.services.SomeService;

                class Consumer { }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("MOD-004");
        assertThat(violations.getFirst().line()).isEqualTo(3);
    }

    @Test
    void mod004AllowsCoreImportingOnlyJdkTypes() {
        List<Violation> violations = evaluate("MOD-004", "regression-core", """
                package com.aqa.core.services;

                import java.util.List;

                class Consumer { }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void everyRuleAppliesToEveryProfile() {
        for (ValidationRule rule : ModuleBoundaryRules.all()) {
            assertThat(rule.profiles()).containsExactlyInAnyOrder(RuleProfile.values());
        }
    }

    @Test
    void exposesExactlyTheFourExpectedRuleIds() {
        assertThat(ModuleBoundaryRules.all()).extracting(ValidationRule::id)
                .containsExactlyInAnyOrder("MOD-001", "MOD-002", "MOD-003", "MOD-004");
    }

    private static List<Violation> evaluate(String ruleId, String module, String source) {
        SourceUnit sourceUnit = new SourceUnit(module, module + "/Consumer.java", StaticJavaParser.parse(source));
        RuleProfile profile = REACTOR_MODULES.stream().filter(candidate -> candidate.module().equals(module))
                .findFirst().orElseThrow().profile();
        EvaluationContext context = new EvaluationContext(module, profile, List.of(sourceUnit), REACTOR_MODULES);
        return RULES_BY_ID.get(ruleId).evaluate(context);
    }
}
