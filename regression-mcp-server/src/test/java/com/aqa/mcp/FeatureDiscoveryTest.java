package com.aqa.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.cucumber.tagexpressions.TagExpressionParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FeatureDiscoveryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversFeatureMetadataAndExecutableScenarios() throws Exception {
        module();
        feature("cart.feature", """
                @cart @smoke
                Feature: Shopping cart management
                  Scenario: Add an item
                    Given a cart
                """);

        FeatureDiscovery.ParsedFeature result = discover().features().getFirst();

        assertThat(result.name()).isEqualTo("Shopping cart management");
        assertThat(result.language()).isEqualTo("en");
        assertThat(result.tags()).containsExactly("@cart", "@smoke");
        assertThat(result.path()).isEqualTo("src/test/resources/features/cart.feature");
        assertThat(result.line()).isEqualTo(2);
        assertThat(result.scenarios()).hasSize(1);
    }

    @Test
    void discoversFeatureFilesRecursively() throws Exception {
        module();
        feature("nested/deep/cart.feature", feature("deep cart"));

        assertThat(discover().features()).extracting(FeatureDiscovery.ParsedFeature::path)
                .containsExactly("src/test/resources/features/nested/deep/cart.feature");
    }

    @Test
    void ordersFeaturesByNormalizedRelativePathAndScenariosByLine() throws Exception {
        module();
        feature("z.feature", feature("z"));
        feature("nested/a.feature", """
                Feature: a
                  Scenario: second
                    Given x

                  Scenario: third
                    Given x
                """);

        FeatureDiscovery.DiscoveryResult result = discover();

        assertThat(result.features()).extracting(FeatureDiscovery.ParsedFeature::path).containsExactly(
                "src/test/resources/features/nested/a.feature", "src/test/resources/features/z.feature");
        assertThat(result.scenarios()).extracting(FeatureDiscovery.ExecutableScenario::name)
                .containsExactly("second", "third", "run");
    }

    @Test
    void returnsEmptySuccessfulDiscoveryForMissingFeatureRoot() throws Exception {
        module();

        FeatureDiscovery.DiscoveryResult result = discover();

        assertThat(result.featureRootExists()).isFalse();
        assertThat(result.features()).isEmpty();
    }

    @Test
    void rejectsUnknownModules() throws Exception {
        module();

        assertThatIllegalArgumentException().isThrownBy(() -> FeatureDiscovery.discover(root(), "unknown"))
                .isInstanceOf(RepositoryInspectionException.class)
                .withMessageContaining("Module is not declared");
    }

    @Test
    void malformedGherkinFailsTheWholeDiscoveryWithoutPartialResults() throws Exception {
        module();
        feature("good.feature", feature("good"));
        feature("broken.feature", "Feature: broken\n  Scenario: bad\n    Given x\n    \"\"\"\n    unclosed");

        assertThatIllegalArgumentException().isThrownBy(this::discover)
                .isInstanceOf(RepositoryInspectionException.class)
                .withMessageContaining("Malformed Gherkin");
    }

    @Test
    void supportsNonEnglishGherkinDialects() throws Exception {
        module();
        feature("fr.feature", """
                # language: fr
                Fonctionnalit\u00E9: panier
                  Sc\u00E9nario: ajouter
                    Soit un panier
                """);

        FeatureDiscovery.ParsedFeature result = discover().features().getFirst();

        assertThat(result.language()).isEqualTo("fr");
        assertThat(result.name()).isEqualTo("panier");
        assertThat(result.scenarios()).extracting(FeatureDiscovery.ExecutableScenario::name).containsExactly("ajouter");
    }

    @Test
    void excludesBackgroundsAndIncludesRuleScenarios() throws Exception {
        module();
        feature("rule.feature", """
                Feature: rules
                  Background:
                    Given shared setup
                  Rule: pricing
                    Background:
                      Given rule setup
                    Scenario: applies price
                      Given an item
                """);

        List<FeatureDiscovery.ExecutableScenario> scenarios = discover().scenarios();

        assertThat(scenarios).extracting(FeatureDiscovery.ExecutableScenario::name).containsExactly("applies price");
        assertThat(scenarios).extracting(FeatureDiscovery.ExecutableScenario::type).containsExactly("SCENARIO");
    }

    @Test
    void inheritsFeatureRuleScenarioAndExamplesTagsAndRemovesDuplicates() throws Exception {
        module();
        feature("tags.feature", """
                @feature @duplicate
                Feature: tags
                  @rule @duplicate
                  Rule: grouped
                    @scenario @duplicate
                    Scenario Outline: values <value>
                      Given <value>
                      @examples @duplicate
                      Examples:
                        | value |
                        | one |
                """);

        assertThat(discover().scenarios().getFirst().tags())
                .containsExactly("@duplicate", "@examples", "@feature", "@rule", "@scenario");
    }

    @Test
    void expandsEveryScenarioOutlineExampleRowWithItsRowLine() throws Exception {
        module();
        feature("outline.feature", """
                Feature: outline
                  Scenario Outline: buy <product>
                    Given <product>
                    Examples:
                      | product |
                      | one |
                      | two |
                """);

        List<FeatureDiscovery.ExecutableScenario> scenarios = discover().scenarios();

        assertThat(scenarios).hasSize(2);
        assertThat(scenarios).extracting(FeatureDiscovery.ExecutableScenario::type)
                .containsOnly("SCENARIO_OUTLINE_EXAMPLE");
        assertThat(scenarios).extracting(FeatureDiscovery.ExecutableScenario::line).containsExactly(6, 7);
    }

    @Test
    void returnsAllExecutableScenariosWhenNoTagFilterIsApplied() throws Exception {
        module();
        feature("all.feature", """
                Feature: all
                  @ui
                  Scenario: browser
                    Given x
                  @api
                  Scenario: service
                    Given x
                """);

        assertThat(discover().scenarios()).hasSize(2);
    }

    @Test
    void evaluatesSmokeAndCartTagExpression() throws Exception {
        module();
        feature("filter.feature", """
                Feature: filter
                  @smoke @cart
                  Scenario: selected
                    Given x
                  @smoke
                  Scenario: rejected
                    Given x
                """);

        assertThat(discover().scenarios().stream()
                .filter(scenario -> TagExpressionParser.parse("@smoke and @cart").evaluate(scenario.tags())).toList())
                .extracting(FeatureDiscovery.ExecutableScenario::name).containsExactly("selected");
    }

    @Test
    void evaluatesOrNotAndParenthesizedTagExpressions() throws Exception {
        module();
        feature("boolean.feature", """
                Feature: boolean
                  @ui
                  Scenario: browser
                    Given x
                  @api @skip
                  Scenario: skipped service
                    Given x
                  @api
                  Scenario: service
                    Given x
                """);

        assertThat(discover().scenarios().stream().filter(scenario ->
                TagExpressionParser.parse("(@ui or @api) and not @skip").evaluate(scenario.tags())).toList())
                .extracting(FeatureDiscovery.ExecutableScenario::name).containsExactly("browser", "service");
    }

    @Test
    void rejectsInvalidTagExpressions() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> TagExpressionParser.parse("@smoke and")))
                .isNotNull();
    }

    @Test
    void rejectsOversizedFeatureFiles() throws Exception {
        module();
        Path file = featurePath("large.feature");
        Files.writeString(file, "#".repeat((int) FeatureDiscovery.MAX_FEATURE_FILE_BYTES + 1));

        assertThatIllegalArgumentException().isThrownBy(this::discover).withMessageContaining("Feature file exceeds");
    }

    @Test
    void rejectsMoreThanTheFeatureFileLimit() throws Exception {
        module();
        Path folder = featurePath("first.feature").getParent();
        for (int index = 0; index <= FeatureDiscovery.MAX_FEATURE_FILES; index++) {
            Files.writeString(folder.resolve(index + ".feature"), feature("x"));
        }

        assertThatIllegalArgumentException().isThrownBy(this::discover).withMessageContaining("Feature file limit exceeded");
    }

    @Test
    void rejectsSymlinkedFeatureFilesThatEscapeTheFeatureRoot() throws Exception {
        module();
        Path outside = temporaryDirectory.resolve("commerce/outside.feature");
        Files.writeString(outside, feature("outside"));
        Path escape = featurePath("escape.feature");
        try {
            Files.createSymbolicLink(escape, outside);
        }
        catch (java.nio.file.FileSystemException exception) {
            Assumptions.abort("Symbolic-link creation is not permitted by this Windows account.");
        }

        assertThatIllegalArgumentException().isThrownBy(this::discover)
                .isInstanceOf(RepositoryInspectionException.class)
                .withMessageContaining("resolves outside the feature root");
    }

    @Test
    void doesNotInspectFeatureLikeFilesInTarget() throws Exception {
        module();
        feature("live.feature", feature("live"));
        Path target = temporaryDirectory.resolve("commerce/target/generated.feature");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "not valid gherkin");

        assertThat(discover().features()).hasSize(1);
    }

    private FeatureDiscovery.DiscoveryResult discover() {
        return FeatureDiscovery.discover(root(), "commerce");
    }

    private RepositoryRoot root() {
        return RepositoryRootResolver.resolve(temporaryDirectory);
    }

    private void module() throws Exception {
        Path module = temporaryDirectory.resolve("commerce");
        Files.createDirectories(module);
        Files.writeString(module.resolve("pom.xml"), "<project/>");
        Files.writeString(temporaryDirectory.resolve("pom.xml"),
                "<project><modules><module>commerce</module></modules></project>");
    }

    private void feature(String relativePath, String content) throws Exception {
        Files.writeString(featurePath(relativePath), content);
    }

    private Path featurePath(String relativePath) throws Exception {
        Path path = temporaryDirectory.resolve("commerce").resolve(FeatureDiscovery.FEATURE_ROOT).resolve(relativePath);
        Files.createDirectories(path.getParent());
        return path;
    }

    private String feature(String name) {
        return "Feature: " + name + "\n  Scenario: run\n    Given x\n";
    }
}
