package com.aqa.mcp.validation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;

/**
 * The fixed rule list backing regression_validate_architecture, direct translations of the Gate 15.5 Phase A design
 * (ACCEPTED, recorded in STAGE_15_PROGRESS.md's "Gate 15.5 -- Phase A design" section). Every rule declares
 * EnumSet.allOf(RuleProfile.class) and gates itself on the class's own package-last-segment layer and on the actual
 * imports/types found in the scanned source, matching ModuleBoundaryRules's and FrameworkConventionRules's existing
 * pattern rather than assuming applicability from RuleProfile alone.
 */
final class ArchitectureRules {

    private static final String SELENIUM_BY_FQCN = "org.openqa.selenium.By";
    private static final String PLAYWRIGHT_PACKAGE = "com.microsoft.playwright";
    private static final Set<String> PLAYWRIGHT_DIRECT_TYPE_NAMES = Set.of("Locator", "Page");
    private static final Set<String> DOWNSTREAM_LAYER_PACKAGE_LAST_SEGMENTS = Set.of("pages", "services", "components");

    private static final String ASSERTJ_ASSERT_THAT_FQCN = "org.assertj.core.api.Assertions.assertThat";
    private static final String PLAYWRIGHT_ASSERT_THAT_FQCN = "com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat";
    private static final Set<String> ASSERTION_STATIC_WILDCARD_SOURCES = Set.of(
            "org.assertj.core.api.Assertions", "com.microsoft.playwright.assertions.PlaywrightAssertions", "org.junit.jupiter.api.Assertions");
    private static final String JUNIT_ASSERTIONS_FQCN = "org.junit.jupiter.api.Assertions";
    private static final String JUNIT_ASSERTIONS_STATIC_PREFIX = JUNIT_ASSERTIONS_FQCN + ".";

    /** ARCH-004's advisory (non-blocking) rule id -- ArchitectureTool presents violations carrying this id in a
     * separate "advisoryViolations" bucket rather than the blocking "violations" list, reusing the exact mechanism
     * FrameworkConventionsTool established for FC-004 in Gate 15.4. */
    static final String THIN_BASE_PAGE_RULE_ID = "ARCH-004";

    private ArchitectureRules() {
    }

    static List<ValidationRule> all() {
        return List.of(new DefinitionsLayerDiscipline(), new NoPackageCycles(), new NoAssertionsInPagesOrComponents(), new ThinBasePage());
    }

    /** ARCH-001: a definitions-layer class must not reach past the steps layer into pages/services/components.
     * Single-hop, field-declared-type-only detection via ImportDeclaration (no Symbol Solver): flags a
     * MethodCallExpr whose immediate scope is a field (or, since every real definitions class in this reactor is a
     * Java record, a record component -- JavaParser models record components as Parameter nodes rather than
     * FieldDeclaration, so both shapes are examined) of that class, where the field's declared type's import
     * resolves to a package ending in .pages, .services, or .components, or is a Selenium By / Playwright
     * Locator/Page type directly. Known accepted gap, documented rather than fixed: a two-hop call like
     * regression-core's S3Definitions.s3Steps.s3ServiceActions().getObject(...) is not caught, because s3Steps is
     * declared as S3Steps (package com.aqa.core.steps, an allowed intermediate layer) and the outer call's own
     * scope is a MethodCallExpr, not a field/component reference. */
    private static final class DefinitionsLayerDiscipline implements ValidationRule {
        @Override
        public String id() {
            return "ARCH-001";
        }

        @Override
        public Set<RuleProfile> profiles() {
            return EnumSet.allOf(RuleProfile.class);
        }

        @Override
        public List<Violation> evaluate(EvaluationContext context) {
            List<Violation> violations = new ArrayList<>();
            for (SourceUnit sourceUnit : context.moduleSources()) {
                CompilationUnit unit = sourceUnit.unit();
                if (!"definitions".equals(lastPackageSegment(unit))) {
                    continue;
                }
                for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                    violations.addAll(layerViolations(sourceUnit, unit, type, fieldTypesOf(type)));
                }
                for (RecordDeclaration type : unit.findAll(RecordDeclaration.class)) {
                    violations.addAll(layerViolations(sourceUnit, unit, type, componentTypesOf(type)));
                }
            }
            return List.copyOf(violations);
        }

        private List<Violation> layerViolations(SourceUnit sourceUnit, CompilationUnit unit, Node typeRoot, Map<String, String> fieldNameToDeclaredType) {
            Set<String> flaggedFieldNames = new LinkedHashSet<>();
            for (Map.Entry<String, String> entry : fieldNameToDeclaredType.entrySet()) {
                if (isDownstreamLayerType(unit, entry.getValue())) {
                    flaggedFieldNames.add(entry.getKey());
                }
            }
            if (flaggedFieldNames.isEmpty()) {
                return List.of();
            }
            List<Violation> violations = new ArrayList<>();
            for (MethodCallExpr call : typeRoot.findAll(MethodCallExpr.class)) {
                call.getScope().ifPresent(scope -> {
                    if (scope instanceof NameExpr name && flaggedFieldNames.contains(name.getNameAsString())) {
                        violations.add(violation(id(), sourceUnit, call, "Definitions layer must not call past the steps layer into "
                                + "pages/services/components; field '" + name.getNameAsString() + "' is called directly: " + call.getNameAsString() + "(...)"));
                    }
                });
            }
            return violations;
        }

        private static Map<String, String> fieldTypesOf(ClassOrInterfaceDeclaration type) {
            Map<String, String> fieldTypes = new LinkedHashMap<>();
            for (FieldDeclaration field : type.getFields()) {
                String declaredType = simpleTypeName(field.getElementType().asString());
                field.getVariables().forEach(variable -> fieldTypes.put(variable.getNameAsString(), declaredType));
            }
            return fieldTypes;
        }

        private static Map<String, String> componentTypesOf(RecordDeclaration type) {
            Map<String, String> componentTypes = new LinkedHashMap<>();
            for (Parameter component : type.getParameters()) {
                componentTypes.put(component.getNameAsString(), simpleTypeName(component.getType().asString()));
            }
            return componentTypes;
        }

        private static String simpleTypeName(String rawType) {
            int genericStart = rawType.indexOf('<');
            return genericStart < 0 ? rawType : rawType.substring(0, genericStart);
        }

        private static boolean isDownstreamLayerType(CompilationUnit unit, String simpleTypeName) {
            String importedPackage = importedPackageForSimpleType(unit, simpleTypeName);
            if (importedPackage == null) {
                return false;
            }
            if (PLAYWRIGHT_DIRECT_TYPE_NAMES.contains(simpleTypeName)
                    && (importedPackage.equals(PLAYWRIGHT_PACKAGE) || importedPackage.startsWith(PLAYWRIGHT_PACKAGE + "."))) {
                return true;
            }
            if ("By".equals(simpleTypeName) && (importedPackage + "." + simpleTypeName).equals(SELENIUM_BY_FQCN)) {
                return true;
            }
            return DOWNSTREAM_LAYER_PACKAGE_LAST_SEGMENTS.contains(lastSegment(importedPackage));
        }
    }

    /** ARCH-002: per-module directed package-dependency graph -- nodes are packages taken from each SourceUnit's
     * own CompilationUnit.getPackageDeclaration(), edges are module-internal imports (resolved via the module's own
     * BasePackages-derived basePackage, already carried on EvaluationContext.reactorModules()) -- checked with
     * DFS-with-recursion-stack cycle detection. Module-structure-agnostic by construction: applies uniformly to
     * every module regardless of layering vocabulary, including regression-mcp-server and regression-core. One
     * Violation is emitted per package participating in a detected cycle, each listing the full cycle sequence. */
    private static final class NoPackageCycles implements ValidationRule {
        @Override
        public String id() {
            return "ARCH-002";
        }

        @Override
        public Set<RuleProfile> profiles() {
            return EnumSet.allOf(RuleProfile.class);
        }

        @Override
        public List<Violation> evaluate(EvaluationContext context) {
            String basePackage = context.reactorModules().stream()
                    .filter(module -> module.module().equals(context.module()))
                    .map(ModuleProfile::basePackage)
                    .findFirst().orElse("");

            Map<String, Set<String>> edges = new LinkedHashMap<>();
            Map<String, SourceUnit> representativeSource = new LinkedHashMap<>();
            for (SourceUnit sourceUnit : context.moduleSources()) {
                String packageName = packageOf(sourceUnit.unit());
                if (packageName.isBlank()) {
                    continue;
                }
                edges.computeIfAbsent(packageName, key -> new LinkedHashSet<>());
                representativeSource.putIfAbsent(packageName, sourceUnit);
                for (ImportDeclaration importDeclaration : sourceUnit.unit().getImports()) {
                    if (importDeclaration.isAsterisk()) {
                        continue;
                    }
                    String importedPackage = packageOfImport(importDeclaration);
                    if (importedPackage.isBlank() || importedPackage.equals(packageName) || !isModuleInternal(importedPackage, basePackage)) {
                        continue;
                    }
                    edges.get(packageName).add(importedPackage);
                }
            }

            List<List<String>> cycles = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            for (String node : edges.keySet()) {
                if (!visited.contains(node)) {
                    detectCycles(node, edges, visited, new ArrayList<>(), new LinkedHashSet<>(), cycles);
                }
            }

            List<Violation> violations = new ArrayList<>();
            for (List<String> cycle : cycles) {
                String sequence = String.join(" -> ", cycle);
                for (String packageName : new LinkedHashSet<>(cycle.subList(0, cycle.size() - 1))) {
                    SourceUnit sourceUnit = representativeSource.get(packageName);
                    if (sourceUnit == null) {
                        continue;
                    }
                    int line = sourceUnit.unit().getPackageDeclaration()
                            .flatMap(Node::getBegin).map(position -> position.line).orElse(1);
                    violations.add(new Violation(id(), sourceUnit.module(), sourceUnit.relativePath(), line, "Package dependency cycle: " + sequence));
                }
            }
            return List.copyOf(violations);
        }

        private static void detectCycles(String node, Map<String, Set<String>> edges, Set<String> visited,
                List<String> stack, Set<String> onStack, List<List<String>> cycles) {
            visited.add(node);
            stack.add(node);
            onStack.add(node);
            for (String neighbor : edges.getOrDefault(node, Set.of())) {
                if (onStack.contains(neighbor)) {
                    int index = stack.indexOf(neighbor);
                    List<String> cycle = new ArrayList<>(stack.subList(index, stack.size()));
                    cycle.add(neighbor);
                    cycles.add(cycle);
                }
                else if (!visited.contains(neighbor)) {
                    detectCycles(neighbor, edges, visited, stack, onStack, cycles);
                }
            }
            stack.remove(stack.size() - 1);
            onStack.remove(node);
        }

        private static boolean isModuleInternal(String importedPackage, String basePackage) {
            return !basePackage.isBlank() && (importedPackage.equals(basePackage) || importedPackage.startsWith(basePackage + "."));
        }

        private static String packageOf(CompilationUnit unit) {
            return unit.getPackageDeclaration().map(declaration -> declaration.getNameAsString()).orElse("");
        }

        private static String packageOfImport(ImportDeclaration importDeclaration) {
            String name = importDeclaration.getNameAsString();
            int lastDot = name.lastIndexOf('.');
            return lastDot < 0 ? "" : name.substring(0, lastDot);
        }
    }

    /** ARCH-003: no assertions in pages/components. Flags assertThat/Assertions.assert* calls in a class whose
     * package's last segment is pages or components, matched against the file's own static ImportDeclarations
     * (AssertJ's Assertions.assertThat, Playwright's PlaywrightAssertions.assertThat, JUnit's Assertions.assert*)
     * rather than a Symbol Solver. Confirmed by direct source inspection to have a non-clean real baseline: 18 real
     * violation call sites across 7 files in regression-jhipster, zero in regression-nextjs-commerce -- expected,
     * correct validator behavior surfacing pre-existing debt, not a bug. */
    private static final class NoAssertionsInPagesOrComponents implements ValidationRule {
        @Override
        public String id() {
            return "ARCH-003";
        }

        @Override
        public Set<RuleProfile> profiles() {
            return EnumSet.allOf(RuleProfile.class);
        }

        @Override
        public List<Violation> evaluate(EvaluationContext context) {
            List<Violation> violations = new ArrayList<>();
            for (SourceUnit sourceUnit : context.moduleSources()) {
                CompilationUnit unit = sourceUnit.unit();
                String lastSegment = lastPackageSegment(unit);
                if (!"pages".equals(lastSegment) && !"components".equals(lastSegment)) {
                    continue;
                }
                UnqualifiedAssertionImports unqualified = unqualifiedAssertionImports(unit);
                boolean importsJUnitAssertionsType = importsExactType(unit, JUNIT_ASSERTIONS_FQCN);
                for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
                    if (isFlaggedAssertionCall(call, unqualified, importsJUnitAssertionsType)) {
                        violations.add(violation(id(), sourceUnit, call,
                                "Assertion call is not allowed in a page/component; keep assertions in steps or the test layer: "
                                        + call.getNameAsString() + "(...)"));
                    }
                }
            }
            return List.copyOf(violations);
        }

        private static boolean isFlaggedAssertionCall(MethodCallExpr call, UnqualifiedAssertionImports unqualified, boolean importsJUnitAssertionsType) {
            String name = call.getNameAsString();
            if (call.getScope().isEmpty()) {
                return unqualified.wildcard() || unqualified.names().contains(name);
            }
            Expression scope = call.getScope().get();
            return importsJUnitAssertionsType && scope instanceof NameExpr scopeName
                    && "Assertions".equals(scopeName.getNameAsString()) && name.startsWith("assert");
        }

        private record UnqualifiedAssertionImports(boolean wildcard, Set<String> names) {
        }

        private static UnqualifiedAssertionImports unqualifiedAssertionImports(CompilationUnit unit) {
            boolean wildcard = false;
            Set<String> names = new LinkedHashSet<>();
            for (ImportDeclaration importDeclaration : unit.getImports()) {
                if (!importDeclaration.isStatic()) {
                    continue;
                }
                String name = importDeclaration.getNameAsString();
                if (importDeclaration.isAsterisk()) {
                    if (ASSERTION_STATIC_WILDCARD_SOURCES.contains(name)) {
                        wildcard = true;
                    }
                    continue;
                }
                if (name.equals(ASSERTJ_ASSERT_THAT_FQCN) || name.equals(PLAYWRIGHT_ASSERT_THAT_FQCN)) {
                    names.add("assertThat");
                }
                else if (name.startsWith(JUNIT_ASSERTIONS_STATIC_PREFIX)) {
                    names.add(name.substring(JUNIT_ASSERTIONS_STATIC_PREFIX.length()));
                }
            }
            return new UnqualifiedAssertionImports(wildcard, names);
        }
    }

    /** ARCH-004 (advisory-only): a BasePage-named/suffixed class should stay thin. Flags a class whose simple name
     * ends with "BasePage" and declares more than 8 public/protected methods, excluding constructors (JavaParser
     * never returns a ConstructorDeclaration from getMethods()) and private helpers. Method-count-only threshold,
     * calibrated against both real BasePage classes (regression-jhipster's at 4, regression-nextjs-commerce's at 7
     * -- both pass at the >8 threshold). Gate 15.1's original two-pronged design (count OR a name-vocabulary
     * heuristic) was dropped after the vocabulary half was shown to false-positive on regression-nextjs-commerce's
     * legitimate header()/cart()/currentUrl()/title() accessor methods. */
    private static final class ThinBasePage implements ValidationRule {
        private static final int MAX_PUBLIC_OR_PROTECTED_METHODS = 8;

        @Override
        public String id() {
            return THIN_BASE_PAGE_RULE_ID;
        }

        @Override
        public Set<RuleProfile> profiles() {
            return EnumSet.allOf(RuleProfile.class);
        }

        @Override
        public List<Violation> evaluate(EvaluationContext context) {
            List<Violation> violations = new ArrayList<>();
            for (SourceUnit sourceUnit : context.moduleSources()) {
                for (ClassOrInterfaceDeclaration type : sourceUnit.unit().findAll(ClassOrInterfaceDeclaration.class)) {
                    String name = type.getNameAsString();
                    if (!name.endsWith("BasePage")) {
                        continue;
                    }
                    long count = type.getMethods().stream().filter(ThinBasePage::isPublicOrProtected).count();
                    if (count > MAX_PUBLIC_OR_PROTECTED_METHODS) {
                        violations.add(violation(id(), sourceUnit, type, "BasePage-shaped class '" + name + "' declares " + count
                                + " public/protected methods, more than the advisory threshold of " + MAX_PUBLIC_OR_PROTECTED_METHODS + "."));
                    }
                }
            }
            return List.copyOf(violations);
        }

        private static boolean isPublicOrProtected(MethodDeclaration method) {
            return method.hasModifier(Modifier.Keyword.PUBLIC) || method.hasModifier(Modifier.Keyword.PROTECTED);
        }
    }

    private static String lastPackageSegment(CompilationUnit unit) {
        return unit.getPackageDeclaration().map(declaration -> lastSegment(declaration.getNameAsString())).orElse("");
    }

    private static String lastSegment(String dotted) {
        int lastDot = dotted.lastIndexOf('.');
        return lastDot < 0 ? dotted : dotted.substring(lastDot + 1);
    }

    private static String importedPackageForSimpleType(CompilationUnit unit, String simpleTypeName) {
        for (ImportDeclaration importDeclaration : unit.getImports()) {
            if (importDeclaration.isAsterisk()) {
                continue;
            }
            String name = importDeclaration.getNameAsString();
            if (lastSegment(name).equals(simpleTypeName)) {
                int lastDot = name.lastIndexOf('.');
                return lastDot < 0 ? "" : name.substring(0, lastDot);
            }
        }
        return null;
    }

    private static boolean importsExactType(CompilationUnit unit, String fqcn) {
        for (ImportDeclaration importDeclaration : unit.getImports()) {
            if (!importDeclaration.isStatic() && importDeclaration.getNameAsString().equals(fqcn)) {
                return true;
            }
        }
        return false;
    }

    private static Violation violation(String ruleId, SourceUnit sourceUnit, Node node, String message) {
        int line = node.getBegin().map(position -> position.line).orElse(1);
        return new Violation(ruleId, sourceUnit.module(), sourceUnit.relativePath(), line, message);
    }
}
