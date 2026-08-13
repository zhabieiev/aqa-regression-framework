package com.aqa.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

import io.cucumber.gherkin.GherkinParser;
import io.cucumber.messages.types.Envelope;
import io.cucumber.messages.types.Examples;
import io.cucumber.messages.types.Feature;
import io.cucumber.messages.types.FeatureChild;
import io.cucumber.messages.types.GherkinDocument;
import io.cucumber.messages.types.Pickle;
import io.cucumber.messages.types.Rule;
import io.cucumber.messages.types.RuleChild;
import io.cucumber.messages.types.Scenario;
import io.cucumber.messages.types.TableRow;

final class FeatureDiscovery {
    static final long MAX_FEATURE_FILE_BYTES = 1_048_576;
    static final int MAX_FEATURE_FILES = 10_000;
    static final String FEATURE_ROOT = "src/test/resources/features";

    private FeatureDiscovery() { }

    static DiscoveryResult discover(RepositoryRoot root, String moduleName) {
        ModuleDescriptor module = module(root, moduleName);
        Path modulePath = root.path().resolve(module.relativePath()).normalize();
        if (!modulePath.startsWith(root.path()) || !Files.isDirectory(modulePath)) {
            throw error("FEATURE_PATH_VIOLATION", "Unable to inspect selected module: " + moduleName + ".");
        }
        Path featureRoot = modulePath.resolve(FEATURE_ROOT).normalize();
        if (!featureRoot.startsWith(modulePath) || !Files.exists(featureRoot)) {
            return new DiscoveryResult(moduleName, false, List.of());
        }
        try {
            Path realModule = modulePath.toRealPath();
            Path realFeatureRoot = featureRoot.toRealPath();
            if (!realModule.startsWith(root.path()) || !realFeatureRoot.startsWith(realModule) || !Files.isDirectory(realFeatureRoot)) {
                throw error("FEATURE_PATH_VIOLATION", "Feature root resolves outside the selected module.");
            }
            List<Path> files = files(featureRoot, realFeatureRoot);
            List<ParsedFeature> features = new ArrayList<>();
            for (Path file : files) features.add(parse(file, featureRoot, realFeatureRoot));
            return new DiscoveryResult(moduleName, true, List.copyOf(features));
        } catch (IOException exception) {
            throw new RepositoryInspectionException("FEATURE_PATH_VIOLATION", "Unable to resolve feature root.", exception);
        }
    }

    private static ModuleDescriptor module(RepositoryRoot root, String name) {
        if (name == null || name.isBlank()) throw error("INVALID_ARGUMENTS", "module must be a non-blank string.");
        return ModuleList.forRoot(root).modules().stream().filter(module -> module.name().equals(name)).findFirst()
                .orElseThrow(() -> error("UNKNOWN_MODULE", "Module is not declared in the root pom.xml."));
    }

    private static List<Path> files(Path root, Path realRoot) {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> walked = Files.walk(root)) {
            for (Path path : walked.toList()) {
                if (Files.isSymbolicLink(path)) validate(path, root, realRoot);
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".feature")) {
                    validate(path, root, realRoot);
                    result.add(path);
                    if (result.size() > MAX_FEATURE_FILES) {
                        throw error("FEATURE_FILE_LIMIT_EXCEEDED", "Feature file limit exceeded: " + MAX_FEATURE_FILES + ".");
                    }
                }
            }
        } catch (IOException exception) {
            throw new RepositoryInspectionException("FEATURE_IO_ERROR", "Unable to inspect feature files.", exception);
        }
        result.sort(Comparator.comparing(path -> display(root.relativize(path))));
        return result;
    }

    private static void validate(Path path, Path displayRoot, Path realRoot) {
        try {
            if (!path.toRealPath().startsWith(realRoot)) {
                throw error("FEATURE_PATH_VIOLATION", "Feature path resolves outside the feature root: "
                        + display(displayRoot.relativize(path)));
            }
        } catch (IOException exception) {
            throw new RepositoryInspectionException("FEATURE_PATH_VIOLATION", "Unable to resolve feature path: "
                    + display(displayRoot.relativize(path)), exception);
        }
    }

    private static ParsedFeature parse(Path file, Path root, Path realRoot) {
        String path = FEATURE_ROOT + "/" + display(root.relativize(file));
        try {
            if (Files.size(file) > MAX_FEATURE_FILE_BYTES) {
                throw error("FEATURE_FILE_TOO_LARGE", "Feature file exceeds " + MAX_FEATURE_FILE_BYTES + " bytes: " + path);
            }
            validate(file, root, realRoot);
            byte[] source = Files.readString(file, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
            List<Envelope> envelopes = GherkinParser.builder().includeGherkinDocument(true).includePickles(true).build()
                    .parse(path, source).toList();
            Optional<String> diagnostic = envelopes.stream().flatMap(envelope -> envelope.getParseError().stream())
                    .map(error -> error.getMessage()).findFirst();
            if (diagnostic.isPresent()) throw error("GHERKIN_PARSE_ERROR", "Malformed Gherkin in " + path + ": " + diagnostic.get());
            GherkinDocument document = envelopes.stream().flatMap(envelope -> envelope.getGherkinDocument().stream()).findFirst()
                    .orElseThrow(() -> error("GHERKIN_PARSE_ERROR", "Malformed Gherkin in " + path + "."));
            Feature feature = document.getFeature().orElseThrow(() -> error("GHERKIN_PARSE_ERROR", "Malformed Gherkin in " + path + "."));
            Map<String, Source> sourceById = sources(feature);
            List<ExecutableScenario> scenarios = envelopes.stream().flatMap(envelope -> envelope.getPickle().stream())
                    .map(pickle -> scenario(pickle, feature, path, sourceById)).toList();
            return new ParsedFeature(feature.getName(), feature.getLanguage(), tags(feature.getTags().stream().map(tag -> tag.getName()).toList()),
                    path, feature.getLocation().getLine().intValue(), scenarios);
        } catch (IOException exception) {
            throw new RepositoryInspectionException("FEATURE_IO_ERROR", "Unable to read feature file: " + path, exception);
        }
    }

    private static Map<String, Source> sources(Feature feature) {
        Map<String, Source> result = new LinkedHashMap<>();
        List<String> featureTags = tags(feature.getTags().stream().map(tag -> tag.getName()).toList());
        for (FeatureChild child : feature.getChildren()) {
            child.getScenario().ifPresent(scenario -> source(result, scenario, featureTags));
            child.getRule().ifPresent(rule -> ruleSources(result, rule, featureTags));
        }
        return result;
    }

    private static void ruleSources(Map<String, Source> result, Rule rule, List<String> featureTags) {
        List<String> inherited = merge(featureTags, rule.getTags().stream().map(tag -> tag.getName()).toList());
        for (RuleChild child : rule.getChildren()) child.getScenario().ifPresent(scenario -> source(result, scenario, inherited));
    }

    private static void source(Map<String, Source> result, Scenario scenario, List<String> inherited) {
        String type = scenario.getExamples().isEmpty() ? "SCENARIO" : "SCENARIO_OUTLINE_EXAMPLE";
        List<String> scenarioTags = merge(inherited, scenario.getTags().stream().map(tag -> tag.getName()).toList());
        result.put(scenario.getId(), new Source(scenario.getLocation().getLine().intValue(), type, scenarioTags));
        for (Examples examples : scenario.getExamples()) for (TableRow row : examples.getTableBody()) {
            result.put(row.getId(), new Source(row.getLocation().getLine().intValue(), "SCENARIO_OUTLINE_EXAMPLE",
                    merge(scenarioTags, examples.getTags().stream().map(tag -> tag.getName()).toList())));
        }
    }
    private static ExecutableScenario scenario(Pickle pickle, Feature feature, String path, Map<String, Source> sourceById) {
        Source source = pickle.getAstNodeIds().stream().map(sourceById::get).filter(java.util.Objects::nonNull)
                .reduce((first, second) -> second).orElseThrow(() -> error("GHERKIN_PARSE_ERROR", "Unable to locate scenario source in " + path + "."));
        return new ExecutableScenario(feature.getName(), pickle.getName(), source.type(),
                merge(source.tags(), pickle.getTags().stream().map(tag -> tag.getName()).toList()), path, source.line());
    }

    private static List<String> tags(List<String> values) { return new TreeSet<>(values).stream().toList(); }
    private static List<String> merge(List<String> first, List<String> second) {
        TreeSet<String> merged = new TreeSet<>(first); merged.addAll(second); return merged.stream().toList();
    }
    private static String display(Path path) { return path.toString().replace('\\', '/'); }
    private static RepositoryInspectionException error(String code, String message) { return new RepositoryInspectionException(code, message); }

    record DiscoveryResult(String module, boolean featureRootExists, List<ParsedFeature> features) {
        List<ExecutableScenario> scenarios() {
            return features.stream().flatMap(feature -> feature.scenarios().stream())
                    .sorted(Comparator.comparing(ExecutableScenario::path).thenComparingInt(ExecutableScenario::line)).toList();
        }
    }
    record ParsedFeature(String name, String language, List<String> tags, String path, int line, List<ExecutableScenario> scenarios) { }
    record ExecutableScenario(String feature, String name, String type, List<String> tags, String path, int line) { }
    private record Source(int line, String type, List<String> tags) { }
}
