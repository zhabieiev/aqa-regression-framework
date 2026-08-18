package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;

/**
 * Tier 1: pure rule-level unit tests for ArchitectureRules, no filesystem I/O -- mirrors ModuleBoundaryRulesTest's
 * and FrameworkConventionRulesTest's shape. Tool/contract-level (TempDir-based, "Tier 2") coverage lives in
 * ArchitectureToolTest and ArchitectureToolContractTest, including the two new real-reactor-facing tests for
 * ARCH-002 and ARCH-003.
 */
class ArchitectureRulesTest {

    static {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    private static final Map<String, ValidationRule> RULES_BY_ID = ArchitectureRules.all().stream()
            .collect(Collectors.toMap(ValidationRule::id, rule -> rule));

    private static final List<ModuleProfile> REACTOR_MODULES = List.of(
            new ModuleProfile("regression-jhipster", RuleProfile.API_UI, "com.aqa.jhipster"),
            new ModuleProfile("regression-core", RuleProfile.CORE, "com.aqa.core"));

    // ARCH-001 -- definitions layer discipline

    @Test
    void arch001FlagsARecordComponentTypedAsAPageClass() {
        List<Violation> violations = evaluate("ARCH-001", "regression-jhipster", """
                package com.aqa.jhipster.ui.definitions;

                import com.aqa.jhipster.ui.pages.LoginPage;

                public record LoginDefinitions(LoginPage loginPage) {
                    public void open() {
                        loginPage.navigateTo("/login");
                    }
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("ARCH-001");
    }

    @Test
    void arch001FlagsAFieldTypedAsASeleniumByDirectly() {
        List<Violation> violations = evaluate("ARCH-001", "regression-jhipster", """
                package com.aqa.jhipster.ui.definitions;

                import org.openqa.selenium.By;

                public class BadDefinitions {
                    private By locator;

                    public void click() {
                        locator.toString();
                    }
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("ARCH-001");
    }

    @Test
    void arch001FlagsAFieldTypedAsAPlaywrightLocatorDirectly() {
        List<Violation> violations = evaluate("ARCH-001", "regression-jhipster", """
                package com.aqa.jhipster.ui.definitions;

                import com.microsoft.playwright.Locator;

                public class BadDefinitions {
                    private Locator button;

                    public void click() {
                        button.click();
                    }
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("ARCH-001");
    }

    @Test
    void arch001AllowsASingleHopFieldTypedAsASteps() {
        List<Violation> violations = evaluate("ARCH-001", "regression-jhipster", """
                package com.aqa.jhipster.ui.definitions;

                import com.aqa.jhipster.ui.steps.LoginSteps;

                public record LoginDefinitions(LoginSteps loginSteps) {
                    public void open() {
                        loginSteps.openLoginPage();
                    }
                }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void arch001AllowsTheKnownAcceptedTwoHopGap() {
        List<Violation> violations = evaluate("ARCH-001", "regression-core", """
                package com.aqa.core.definitions;

                import com.aqa.core.steps.S3Steps;

                public record S3Definitions(S3Steps s3Steps) {
                    public void get() {
                        s3Steps.s3ServiceActions().getObject(null);
                    }
                }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void arch001IgnoresClassesOutsideTheDefinitionsLayer() {
        List<Violation> violations = evaluate("ARCH-001", "regression-jhipster", """
                package com.aqa.jhipster.ui.steps;

                import com.aqa.jhipster.ui.pages.LoginPage;

                public class LoginSteps {
                    private LoginPage loginPage;

                    public void open() {
                        loginPage.navigateTo("/login");
                    }
                }
                """);

        assertThat(violations).isEmpty();
    }

    // ARCH-002 -- package dependency cycles

    @Test
    void arch002FlagsATwoPackageCycleAndListsTheFullSequenceForEachParticipant() {
        SourceUnit a = new SourceUnit("regression-jhipster", "regression-jhipster/A.java", StaticJavaParser.parse("""
                package com.aqa.jhipster.ui.a;

                import com.aqa.jhipster.ui.b.B;

                public class A {
                }
                """));
        SourceUnit b = new SourceUnit("regression-jhipster", "regression-jhipster/B.java", StaticJavaParser.parse("""
                package com.aqa.jhipster.ui.b;

                import com.aqa.jhipster.ui.a.A;

                public class B {
                }
                """));
        EvaluationContext context = new EvaluationContext("regression-jhipster", RuleProfile.API_UI, List.of(a, b), REACTOR_MODULES);

        List<Violation> violations = RULES_BY_ID.get("ARCH-002").evaluate(context);

        assertThat(violations).hasSize(2);
        assertThat(violations).allSatisfy(violation -> {
            assertThat(violation.ruleId()).isEqualTo("ARCH-002");
            assertThat(violation.message()).contains("com.aqa.jhipster.ui.a").contains("com.aqa.jhipster.ui.b").contains("->");
        });
    }

    @Test
    void arch002FlagsAThreePackageCycle() {
        SourceUnit a = new SourceUnit("regression-jhipster", "regression-jhipster/A.java", StaticJavaParser.parse("""
                package com.aqa.jhipster.ui.a;

                import com.aqa.jhipster.ui.b.B;

                public class A {
                }
                """));
        SourceUnit b = new SourceUnit("regression-jhipster", "regression-jhipster/B.java", StaticJavaParser.parse("""
                package com.aqa.jhipster.ui.b;

                import com.aqa.jhipster.ui.c.C;

                public class B {
                }
                """));
        SourceUnit c = new SourceUnit("regression-jhipster", "regression-jhipster/C.java", StaticJavaParser.parse("""
                package com.aqa.jhipster.ui.c;

                import com.aqa.jhipster.ui.a.A;

                public class C {
                }
                """));
        EvaluationContext context = new EvaluationContext("regression-jhipster", RuleProfile.API_UI, List.of(a, b, c), REACTOR_MODULES);

        List<Violation> violations = RULES_BY_ID.get("ARCH-002").evaluate(context);

        assertThat(violations).hasSize(3);
        assertThat(violations.getFirst().message()).contains("com.aqa.jhipster.ui.a").contains("com.aqa.jhipster.ui.b").contains("com.aqa.jhipster.ui.c");
    }

    @Test
    void arch002AllowsAOneWayDependencyWithNoCycle() {
        SourceUnit a = new SourceUnit("regression-jhipster", "regression-jhipster/A.java", StaticJavaParser.parse("""
                package com.aqa.jhipster.ui.a;

                import com.aqa.jhipster.ui.b.B;

                public class A {
                }
                """));
        SourceUnit b = new SourceUnit("regression-jhipster", "regression-jhipster/B.java", StaticJavaParser.parse("""
                package com.aqa.jhipster.ui.b;

                public class B {
                }
                """));
        EvaluationContext context = new EvaluationContext("regression-jhipster", RuleProfile.API_UI, List.of(a, b), REACTOR_MODULES);

        List<Violation> violations = RULES_BY_ID.get("ARCH-002").evaluate(context);

        assertThat(violations).isEmpty();
    }

    @Test
    void arch002IgnoresImportsOutsideTheModulesOwnBasePackage() {
        SourceUnit a = new SourceUnit("regression-jhipster", "regression-jhipster/A.java", StaticJavaParser.parse("""
                package com.aqa.jhipster.ui.a;

                import com.aqa.core.services.GeneralApiService;

                public class A {
                }
                """));
        EvaluationContext context = new EvaluationContext("regression-jhipster", RuleProfile.API_UI, List.of(a), REACTOR_MODULES);

        List<Violation> violations = RULES_BY_ID.get("ARCH-002").evaluate(context);

        assertThat(violations).isEmpty();
    }

    // ARCH-003 -- no assertions in pages/components

    @Test
    void arch003FlagsAnUnqualifiedAssertThatStaticallyImportedFromAssertJ() {
        List<Violation> violations = evaluate("ARCH-003", "regression-jhipster", """
                package com.aqa.jhipster.ui.pages;

                import static org.assertj.core.api.Assertions.assertThat;

                public class SomePage {
                    void check() {
                        assertThat(true).isTrue();
                    }
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("ARCH-003");
    }

    @Test
    void arch003FlagsAnUnqualifiedAssertThatStaticallyImportedFromPlaywrightAssertions() {
        List<Violation> violations = evaluate("ARCH-003", "regression-jhipster", """
                package com.aqa.jhipster.ui.pages;

                import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

                public class SomePage {
                    void check(Object locator) {
                        assertThat(locator).isTrue();
                    }
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("ARCH-003");
    }

    @Test
    void arch003FlagsAQualifiedJUnitAssertionsCallInAComponent() {
        List<Violation> violations = evaluate("ARCH-003", "regression-jhipster", """
                package com.aqa.jhipster.ui.components;

                import org.junit.jupiter.api.Assertions;

                public class SomeComponent {
                    void check() {
                        Assertions.assertEquals(1, 1);
                    }
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("ARCH-003");
    }

    @Test
    void arch003IgnoresClassesOutsidePagesAndComponents() {
        List<Violation> violations = evaluate("ARCH-003", "regression-jhipster", """
                package com.aqa.jhipster.ui.steps;

                import static org.assertj.core.api.Assertions.assertThat;

                public class SomeSteps {
                    void check() {
                        assertThat(true).isTrue();
                    }
                }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void arch003IgnoresAnUnrelatedLocalMethodAlsoNamedAssertThat() {
        List<Violation> violations = evaluate("ARCH-003", "regression-jhipster", """
                package com.aqa.jhipster.ui.pages;

                public class SomePage {
                    void check() {
                        assertThat(true);
                    }

                    private static void assertThat(boolean value) {
                    }
                }
                """);

        assertThat(violations).isEmpty();
    }

    // ARCH-004 -- thin BasePage (advisory)

    @Test
    void arch004FlagsABasePageWithMoreThanEightPublicOrProtectedMethods() {
        List<Violation> violations = evaluate("ARCH-004", "regression-jhipster", """
                package com.aqa.jhipster.ui.pages;

                public abstract class BasePage {
                    public void m1() { }
                    public void m2() { }
                    public void m3() { }
                    public void m4() { }
                    protected void m5() { }
                    protected void m6() { }
                    protected void m7() { }
                    protected void m8() { }
                    public void m9() { }
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("ARCH-004");
    }

    @Test
    void arch004AllowsExactlyEightPublicOrProtectedMethods() {
        List<Violation> violations = evaluate("ARCH-004", "regression-jhipster", """
                package com.aqa.jhipster.ui.pages;

                public abstract class BasePage {
                    public void m1() { }
                    public void m2() { }
                    public void m3() { }
                    public void m4() { }
                    protected void m5() { }
                    protected void m6() { }
                    protected void m7() { }
                    protected void m8() { }
                }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void arch004IgnoresPrivateHelpersWhenCounting() {
        List<Violation> violations = evaluate("ARCH-004", "regression-jhipster", """
                package com.aqa.jhipster.ui.pages;

                public abstract class BasePage {
                    public void m1() { }
                    public void m2() { }
                    public void m3() { }
                    public void m4() { }
                    protected void m5() { }
                    protected void m6() { }
                    protected void m7() { }
                    protected void m8() { }
                    private void helper1() { }
                    private void helper2() { }
                }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void arch004IgnoresAClassNotNamedOrSuffixedBasePage() {
        List<Violation> violations = evaluate("ARCH-004", "regression-jhipster", """
                package com.aqa.jhipster.ui.pages;

                public class LoginPage {
                    public void m1() { }
                    public void m2() { }
                    public void m3() { }
                    public void m4() { }
                    public void m5() { }
                    public void m6() { }
                    public void m7() { }
                    public void m8() { }
                    public void m9() { }
                }
                """);

        assertThat(violations).isEmpty();
    }

    // Cross-cutting

    @Test
    void everyRuleAppliesToEveryProfile() {
        for (ValidationRule rule : ArchitectureRules.all()) {
            assertThat(rule.profiles()).containsExactlyInAnyOrder(RuleProfile.values());
        }
    }

    @Test
    void exposesExactlyTheFourExpectedRuleIds() {
        assertThat(ArchitectureRules.all()).extracting(ValidationRule::id)
                .containsExactlyInAnyOrder("ARCH-001", "ARCH-002", "ARCH-003", "ARCH-004");
    }

    private static List<Violation> evaluate(String ruleId, String module, String source) {
        SourceUnit sourceUnit = new SourceUnit(module, module + "/Consumer.java", StaticJavaParser.parse(source));
        EvaluationContext context = new EvaluationContext(module, RuleProfile.API_UI, List.of(sourceUnit), REACTOR_MODULES);
        return RULES_BY_ID.get(ruleId).evaluate(context);
    }
}
