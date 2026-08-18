package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;

/**
 * Tier 1: pure rule-level unit tests for FrameworkConventionRules, no filesystem I/O — mirrors
 * ModuleBoundaryRulesTest's shape from Gate 15.3. Tool/contract-level (TempDir-based, "Tier 2") coverage lives in
 * FrameworkConventionsToolTest and FrameworkConventionsToolContractTest.
 */
class FrameworkConventionRulesTest {

    static {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    private static final Map<String, ValidationRule> RULES_BY_ID = FrameworkConventionRules.all().stream()
            .collect(Collectors.toMap(ValidationRule::id, rule -> rule));

    // FC-001 — Selenium By locator fields

    @Test
    void fc001FlagsAByFieldThatIsNotPrivateStaticFinal() {
        List<Violation> violations = evaluate("FC-001", "regression-nextjs-commerce", """
                package com.aqa.nextjscommerce.pages;

                import org.openqa.selenium.By;

                public final class SomePage {
                    private By locator = By.cssSelector("main h1");
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("FC-001");
    }

    @Test
    void fc001AllowsAPrivateStaticFinalByFieldAndAStaticFactoryMethod() {
        List<Violation> violations = evaluate("FC-001", "regression-nextjs-commerce", """
                package com.aqa.nextjscommerce.pages;

                import org.openqa.selenium.By;

                public final class ProductPage {
                    private static final By PRODUCT_NAME = By.cssSelector("main h1");

                    private static By optionButton(final String option, final String value) {
                        return By.xpath("//button[@title='" + option + " " + value + "']");
                    }
                }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void fc001IgnoresFilesThatDoNotImportSeleniumBy() {
        List<Violation> violations = evaluate("FC-001", "regression-nextjs-commerce", """
                package com.aqa.nextjscommerce.pages;

                public final class NotAPageObject {
                    private Object locator;
                }
                """);

        assertThat(violations).isEmpty();
    }

    // FC-001-PW — Playwright Locator fields

    @Test
    void fc001PwFlagsANonFinalLocatorField() {
        List<Violation> violations = evaluate("FC-001-PW", "regression-jhipster", """
                package com.aqa.jhipster.ui.pages;

                import com.microsoft.playwright.Locator;
                import com.microsoft.playwright.Page;

                public class SomePage {
                    private Locator button;

                    public SomePage(final Page page) {
                        button = page.getByTestId("button");
                    }
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("FC-001-PW");
    }

    @Test
    void fc001PwFlagsANonPrivateLocatorField() {
        List<Violation> violations = evaluate("FC-001-PW", "regression-jhipster", """
                package com.aqa.jhipster.ui.pages;

                import com.microsoft.playwright.Locator;
                import com.microsoft.playwright.Page;

                public class SomePage {
                    protected final Locator button;

                    public SomePage(final Page page) {
                        button = page.getByTestId("button");
                    }
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("FC-001-PW");
    }

    @Test
    void fc001PwAllowsAPrivateFinalLocatorFieldAssignedInTheConstructor() {
        List<Violation> violations = evaluate("FC-001-PW", "regression-jhipster", """
                package com.aqa.jhipster.ui.pages;

                import com.microsoft.playwright.Locator;
                import com.microsoft.playwright.Page;

                public class LoginPage {
                    private final Locator usernameInput;

                    public LoginPage(final Page page) {
                        usernameInput = page.getByTestId("username");
                    }

                    private Locator dynamicRow(final String name) {
                        return usernameInput.locator("tr[data-name='" + name + "']");
                    }
                }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void fc001PwIgnoresFilesThatDoNotImportPlaywright() {
        List<Violation> violations = evaluate("FC-001-PW", "regression-jhipster", """
                package com.aqa.jhipster.ui.pages;

                public class NotAPlaywrightClass {
                    private Object locator;
                }
                """);

        assertThat(violations).isEmpty();
    }

    // FC-002 — no Thread.sleep

    @Test
    void fc002FlagsAThreadSleepCall() {
        List<Violation> violations = evaluate("FC-002", "regression-nextjs-commerce", """
                package com.aqa.nextjscommerce.pages;

                public final class FlakyPage {
                    void awaitSomething() throws InterruptedException {
                        Thread.sleep(1000);
                    }
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("FC-002");
    }

    @Test
    void fc002AllowsThreadSleepInsideTheAllowListedWaitUtilsFile() {
        SourceUnit sourceUnit = new SourceUnit("regression-core", "regression-core/src/main/java/com/aqa/core/utils/WaitUtils.java",
                StaticJavaParser.parse("""
                        package com.aqa.core.utils;

                        public class WaitUtils {
                            static void pollOnce() throws InterruptedException {
                                Thread.sleep(50);
                            }
                        }
                        """));
        EvaluationContext context = new EvaluationContext("regression-core", RuleProfile.CORE, List.of(sourceUnit), List.of());

        List<Violation> violations = RULES_BY_ID.get("FC-002").evaluate(context);

        assertThat(violations).isEmpty();
    }

    // FC-002-PW — no Playwright waitForTimeout

    @Test
    void fc002PwFlagsAWaitForTimeoutCall() {
        List<Violation> violations = evaluate("FC-002-PW", "regression-jhipster", """
                package com.aqa.jhipster.ui.pages;

                import com.microsoft.playwright.Page;

                public final class FlakyPage {
                    private final Page page;

                    public FlakyPage(final Page page) {
                        this.page = page;
                    }

                    void awaitSomething() {
                        page.waitForTimeout(1000);
                    }
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("FC-002-PW");
    }

    @Test
    void fc002PwIgnoresFilesThatDoNotImportPlaywright() {
        List<Violation> violations = evaluate("FC-002-PW", "regression-jhipster", """
                package com.aqa.jhipster.ui.pages;

                public final class NotPlaywright {
                    void awaitSomething() {
                        someOtherApi.waitForTimeout(1000);
                    }
                }
                """);

        assertThat(violations).isEmpty();
    }

    // FC-003 — constructor injection

    @Test
    void fc003FlagsAFieldInjectionAnnotation() {
        List<Violation> violations = evaluate("FC-003", "regression-jhipster", """
                package com.aqa.jhipster.ui.steps;

                import org.springframework.beans.factory.annotation.Autowired;

                public class SomeSteps {
                    @Autowired
                    private SomeService service;
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("FC-003");
    }

    @Test
    void fc003FlagsAFieldAssignedByAPublicSetterOutsideTheConstructor() {
        List<Violation> violations = evaluate("FC-003", "regression-jhipster", """
                package com.aqa.jhipster.ui.steps;

                public class SomeSteps {
                    private SomeService service;

                    public void setService(final SomeService service) {
                        this.service = service;
                    }
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("FC-003");
    }

    @Test
    void fc003AllowsAPrivateSetterMethod() {
        List<Violation> violations = evaluate("FC-003", "regression-jhipster", """
                package com.aqa.jhipster.ui.steps;

                public class SomeSteps {
                    private SomeService service;

                    private void setService(final SomeService service) {
                        this.service = service;
                    }
                }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void fc003AllowsConstructorAssignedFinalFields() {
        List<Violation> violations = evaluate("FC-003", "regression-jhipster", """
                package com.aqa.jhipster.ui.steps;

                public class LoginSteps {
                    private final Object scenarioContext;
                    private Object loginPage;

                    public LoginSteps(final Object scenarioContext) {
                        this.scenarioContext = scenarioContext;
                    }

                    void openLoginPage() {
                        loginPage = new Object();
                    }
                }
                """);

        assertThat(violations).isEmpty();
    }

    // FC-004 — records for value objects (advisory-only)

    @Test
    void fc004FlagsACandidateValueObjectNotDeclaredAsARecord() {
        List<Violation> violations = evaluate("FC-004", "regression-jhipster", """
                package com.aqa.jhipster.ui.models;

                public class Coordinates {
                    private final int x;
                    private final int y;

                    public Coordinates(final int x, final int y) {
                        this.x = x;
                        this.y = y;
                    }

                    public int getX() {
                        return x;
                    }

                    public int getY() {
                        return y;
                    }
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("FC-004");
    }

    @Test
    void fc004AllowsAClassWithBusinessLogicMethods() {
        List<Violation> violations = evaluate("FC-004", "regression-jhipster", """
                package com.aqa.jhipster.ui.models;

                public class Coordinates {
                    private final int x;
                    private final int y;

                    public Coordinates(final int x, final int y) {
                        this.x = x;
                        this.y = y;
                    }

                    public double distanceFromOrigin() {
                        return Math.sqrt(x * x + y * y);
                    }
                }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void fc004AllowsAnAlreadyDeclaredRecord() {
        List<Violation> violations = evaluate("FC-004", "regression-jhipster", """
                package com.aqa.jhipster.ui.models;

                public record Coordinates(int x, int y) {
                }
                """);

        assertThat(violations).isEmpty();
    }

    // FC-005 — no static mutable UI state

    @Test
    void fc005FlagsAStaticNonFinalWebDriverField() {
        List<Violation> violations = evaluate("FC-005", "regression-nextjs-commerce", """
                package com.aqa.nextjscommerce.driver;

                import org.openqa.selenium.WebDriver;

                public final class BadDriverHolder {
                    private static WebDriver driver;
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("FC-005");
    }

    @Test
    void fc005FlagsAStaticNonFinalPageObjectField() {
        List<Violation> violations = evaluate("FC-005", "regression-nextjs-commerce", """
                package com.aqa.nextjscommerce.pages;

                public final class BadPageHolder {
                    private static ProductPage currentPage;
                }
                """);

        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().ruleId()).isEqualTo("FC-005");
    }

    @Test
    void fc005AllowsAnInstanceWebDriverFieldLikeTheRealDriverSession() {
        List<Violation> violations = evaluate("FC-005", "regression-nextjs-commerce", """
                package com.aqa.nextjscommerce.driver;

                import org.openqa.selenium.WebDriver;

                public final class DriverSession {
                    private WebDriver driver;
                }
                """);

        assertThat(violations).isEmpty();
    }

    @Test
    void fc005AllowsAStaticFinalConstant() {
        List<Violation> violations = evaluate("FC-005", "regression-nextjs-commerce", """
                package com.aqa.nextjscommerce.pages;

                public final class ProductPage {
                    private static final String PRODUCT_PATH = "/product/";
                }
                """);

        assertThat(violations).isEmpty();
    }

    // Cross-cutting

    @Test
    void everyRuleAppliesToEveryProfile() {
        for (ValidationRule rule : FrameworkConventionRules.all()) {
            assertThat(rule.profiles()).containsExactlyInAnyOrder(RuleProfile.values());
        }
    }

    @Test
    void exposesExactlyTheSevenExpectedRuleIds() {
        assertThat(FrameworkConventionRules.all()).extracting(ValidationRule::id)
                .containsExactlyInAnyOrder("FC-001", "FC-001-PW", "FC-002", "FC-002-PW", "FC-003", "FC-004", "FC-005");
    }

    private static List<Violation> evaluate(String ruleId, String module, String source) {
        SourceUnit sourceUnit = new SourceUnit(module, module + "/Consumer.java", StaticJavaParser.parse(source));
        EvaluationContext context = new EvaluationContext(module, RuleProfile.UI, List.of(sourceUnit), List.of());
        return RULES_BY_ID.get(ruleId).evaluate(context);
    }
}
