package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tier 2: TempDir-based, end-to-end tool coverage -- mirrors ModuleBoundariesToolTest's and
 * FrameworkConventionsToolTest's shape. Also carries the two Gate 15.5 real-reactor-facing tests, since no prior
 * test in this suite pointed a validation tool at the real repository filesystem before this gate. The pattern
 * chosen (see {@link #realRepositoryRoot()}) is to resolve the real reactor root by walking up from the test JVM's
 * working directory until a directory is found that both contains a pom.xml and has a regression-mcp-server
 * subdirectory -- this works whether Surefire's working directory is the module's own basedir (the default when
 * running "mvn -pl regression-mcp-server -am clean verify" from the reactor root, which is how this gate's
 * verification command is run) or the reactor root itself (e.g. some IDE run configurations), without hardcoding
 * either shape or depending on any environment variable.
 */
class ArchitectureToolTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsARealArch001ViolationWhenNoFilterIsGiven() throws Exception {
        reactor();

        Map<String, Object> data = call(Map.of());

        Map<String, Object> jhipster = moduleResult(data, "regression-jhipster");
        assertThat(rulesApplied(jhipster)).contains("ARCH-001", "ARCH-002", "ARCH-003", "ARCH-004");
        List<Map<String, Object>> violations = violations(jhipster);
        assertThat(violations).hasSize(1);
        assertThat(violations.getFirst().get("ruleId")).isEqualTo("ARCH-001");

        assertThat(violations(moduleResult(data, "regression-core"))).isEmpty();
    }

    @Test
    void reportsAnAdvisoryArch004ViolationSeparatelyFromBlockingViolations() throws Exception {
        reactorWithAThickBasePage();

        Map<String, Object> data = call(temporaryDirectory, this::jhipsterOnlyModuleType, Map.of("module", "regression-jhipster"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) data.get("modules");
        Map<String, Object> jhipster = modules.getFirst();
        assertThat(violations(jhipster)).isEmpty();
        assertThat(advisoryViolations(jhipster)).hasSize(1);
        assertThat(advisoryViolations(jhipster).getFirst().get("ruleId")).isEqualTo("ARCH-004");
    }

    @Test
    void filtersByModuleAndProfileTogether() throws Exception {
        reactor();

        Map<String, Object> data = call(Map.of("module", "regression-jhipster", "profile", "API_UI"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) data.get("modules");
        assertThat(modules).hasSize(1);
        assertThat(modules.getFirst().get("module")).isEqualTo("regression-jhipster");
        assertThat(modules.getFirst().get("profile")).isEqualTo("API_UI");
    }

    @Test
    void rejectsAnUnknownModuleArgument() throws Exception {
        reactor();

        CallToolResult result = invoke(temporaryDirectory, this::moduleTypeByName, Map.of("module", "not-a-real-module"));

        assertThat(result.isError()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
        assertThat(structured.get("status")).isEqualTo("error");
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) structured.get("error");
        assertThat(error.get("code")).isEqualTo("UNKNOWN_MODULE");
    }

    /** Gate 15.5's standing instruction was to establish real-reactor cleanliness via the tool itself, not assume
     * it -- ARCH-002 was never manually pre-verified against the real reactor in Phase A. Running it for the first
     * time in this gate's own Phase B surfaced a genuine, previously-unknown two-package cycle in
     * regression-nextjs-commerce: com.aqa.nextjscommerce.config.UiSettings imports
     * com.aqa.nextjscommerce.driver.BrowserType, while com.aqa.nextjscommerce.driver's ChromeOptionsFactory,
     * DriverSession, and DriverFactory all import com.aqa.nextjscommerce.config.UiSettings. Per explicit user
     * decision (accepted as known debt rather than fixed in this gate, which is scoped to the MCP server only, not
     * product-module source), this test asserts that exactly this one known cycle is present, everywhere else in
     * the reactor stays cycle-free, and ARCH-002 keeps failing the build the moment a second, different cycle
     * ever appears. Fixing the underlying regression-nextjs-commerce cycle is deferred to a separate, explicitly
     * authorized task -- it is not addressed by this gate. */
    @Test
    void realReactorHasOnlyTheOneKnownAcceptedArch002PackageCycle() {
        Path repositoryRoot = realRepositoryRoot();

        Map<String, Object> data = call(repositoryRoot, this::realModuleTypeByName, Map.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) data.get("modules");
        List<Map<String, Object>> arch002Violations = modules.stream()
                .flatMap(module -> violations(module).stream())
                .filter(violation -> "ARCH-002".equals(violation.get("ruleId")))
                .toList();

        assertThat(arch002Violations).hasSize(2);
        assertThat(arch002Violations).allSatisfy(violation -> {
            assertThat(violation.get("module")).isEqualTo("regression-nextjs-commerce");
            assertThat((String) violation.get("message")).contains("com.aqa.nextjscommerce.config").contains("com.aqa.nextjscommerce.driver");
        });
        assertThat(arch002Violations).extracting(violation -> violation.get("file")).containsExactlyInAnyOrder(
                "regression-nextjs-commerce/src/test/java/com/aqa/nextjscommerce/config/CommerceProperty.java",
                "regression-nextjs-commerce/src/test/java/com/aqa/nextjscommerce/driver/BrowserType.java");
    }

    /** Gate 15.5's standing instruction: confirms the real reactor surfaces exactly the 18 real, pre-existing
     * ARCH-003 violation call sites across exactly the 7 named regression-jhipster files, confirmed by direct
     * source inspection in this gate's own Phase B work (BasePage.java, LoginPage.java, BankAccountPage.java,
     * BankAccountFormPage.java, BaseComponent.java, DataTableComponent.java, NavigationBar.java). */
    @Test
    void realReactorSurfacesTheExpectedArch003ViolationsInJhipster() {
        Path repositoryRoot = realRepositoryRoot();

        Map<String, Object> data = call(repositoryRoot, this::realModuleTypeByName, Map.of("module", "regression-jhipster"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> modules = (List<Map<String, Object>>) data.get("modules");
        List<Map<String, Object>> arch003Violations = violations(modules.getFirst()).stream()
                .filter(violation -> "ARCH-003".equals(violation.get("ruleId"))).toList();

        assertThat(arch003Violations).hasSize(18);
        Set<String> distinctFiles = arch003Violations.stream().map(violation -> (String) violation.get("file")).collect(Collectors.toSet());
        assertThat(distinctFiles).hasSize(7);
        assertThat(distinctFiles).allSatisfy(file -> assertThat(file).endsWith(".java")
                .containsAnyOf("BasePage.java", "LoginPage.java", "BankAccountPage.java", "BankAccountFormPage.java",
                        "BaseComponent.java", "DataTableComponent.java", "NavigationBar.java"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> moduleResult(Map<String, Object> data, String module) {
        List<Map<String, Object>> modules = (List<Map<String, Object>>) data.get("modules");
        return modules.stream().filter(candidate -> candidate.get("module").equals(module)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> violations(Map<String, Object> moduleResult) {
        return (List<Map<String, Object>>) moduleResult.get("violations");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> advisoryViolations(Map<String, Object> moduleResult) {
        return (List<Map<String, Object>>) moduleResult.get("advisoryViolations");
    }

    @SuppressWarnings("unchecked")
    private static List<String> rulesApplied(Map<String, Object> moduleResult) {
        return (List<String>) moduleResult.get("rulesApplied");
    }

    private Map<String, Object> call(Map<String, Object> arguments) {
        return call(temporaryDirectory, this::moduleTypeByName, arguments);
    }

    private static Map<String, Object> call(Path repositoryRoot, java.util.function.Supplier<Map<String, String>> moduleTypeByName,
            Map<String, Object> arguments) {
        CallToolResult result = invoke(repositoryRoot, moduleTypeByName, arguments);
        assertThat(result.isError()).as("expected a successful result: %s", result.structuredContent()).isFalse();
        @SuppressWarnings("unchecked")
        Map<String, Object> structured = (Map<String, Object>) result.structuredContent();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) structured.get("data");
        return data;
    }

    private static CallToolResult invoke(Path repositoryRoot, java.util.function.Supplier<Map<String, String>> moduleTypeByName,
            Map<String, Object> arguments) {
        SyncToolSpecification tool = ArchitectureTool.tool(repositoryRoot, moduleTypeByName);
        return tool.callHandler().apply(null, new CallToolRequest(ArchitectureTool.TOOL_NAME, arguments));
    }

    private Map<String, String> moduleTypeByName() {
        return Map.of("regression-core", "CORE", "regression-jhipster", "API_UI");
    }

    private Map<String, String> jhipsterOnlyModuleType() {
        return Map.of("regression-jhipster", "API_UI");
    }

    /** Matches the real reactor's module -> profile grounding recorded in docs/TECHNICAL_DEBT.md. */
    private Map<String, String> realModuleTypeByName() {
        return Map.of("regression-core", "CORE", "regression-petstore-api", "API", "regression-jhipster", "API_UI",
                "regression-nextjs-commerce", "UI", "regression-mcp-server", "MCP");
    }

    /** Walks up from the test JVM's working directory to find the real reactor root -- see the class-level javadoc
     * for the pattern's rationale. */
    private static Path realRepositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve("pom.xml")) && Files.isDirectory(candidate.resolve("regression-mcp-server"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Unable to resolve the real reactor root from the working directory: " + Path.of("").toAbsolutePath());
    }

    /** A minimal, real 2-module reactor: regression-jhipster carries a genuine ARCH-001 violation (a definitions
     * record component typed directly as a page class), regression-core stays clean. */
    private void reactor() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project><modules>"
                + "<module>regression-core</module><module>regression-jhipster</module>"
                + "</modules></project>");

        javaFile("regression-core", "com/aqa/core/Marker.java", "package com.aqa.core;\n\nclass Marker { }\n");

        javaFile("regression-jhipster", "com/aqa/jhipster/ui/pages/LoginPage.java", """
                package com.aqa.jhipster.ui.pages;

                public class LoginPage {
                    public void navigateTo(String path) { }
                }
                """);

        javaFile("regression-jhipster", "com/aqa/jhipster/ui/definitions/LoginDefinitions.java", """
                package com.aqa.jhipster.ui.definitions;

                import com.aqa.jhipster.ui.pages.LoginPage;

                public record LoginDefinitions(LoginPage loginPage) {
                    public void open() {
                        loginPage.navigateTo("/login");
                    }
                }
                """);
    }

    /** A minimal, real reactor whose sole regression-jhipster source is a BasePage-suffixed class with 9
     * public/protected methods -- a genuine ARCH-004 advisory finding, and nothing else. */
    private void reactorWithAThickBasePage() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project><modules>"
                + "<module>regression-jhipster</module></modules></project>");

        javaFile("regression-jhipster", "com/aqa/jhipster/ui/pages/BasePage.java", """
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
    }

    private void javaFile(String module, String relativePath, String content) throws Exception {
        Path modulePom = temporaryDirectory.resolve(module).resolve("pom.xml");
        Files.createDirectories(modulePom.getParent());
        if (Files.notExists(modulePom)) {
            Files.writeString(modulePom, "<project/>");
        }
        Path file = temporaryDirectory.resolve(module).resolve("src/main/java").resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
