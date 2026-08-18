package com.aqa.mcp.validation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;

/**
 * The fixed rule list backing regression_validate_framework_conventions, direct translations of CLAUDE.md's
 * "Selenium and UI safety" / "Architecture" sections plus the Gate 15.4 Phase A Playwright-parallel design (accepted
 * in full). Every rule declares EnumSet.allOf(RuleProfile.class) and instead gates itself on the actual
 * imports/types found in the scanned source, never assuming applicability from RuleProfile alone, matching
 * ModuleBoundaryRules's existing pattern and the standing per-module-tech-stack principle recorded in
 * STAGE_15_PROGRESS.md.
 */
final class FrameworkConventionRules {

    private static final String SELENIUM_BY_FQCN = "org.openqa.selenium.By";
    private static final String PLAYWRIGHT_PACKAGE_PREFIX = "com.microsoft.playwright.";

    /** FC-002's explicit, reviewable allow-list of legitimate wait-abstraction implementation files. Confirmed by
     * direct grep in Gate 15.4 Phase A to be exactly this one file: WaitManager.java (nextjs-commerce) contains no
     * Thread.sleep calls itself, so it needs no entry here. */
    private static final Set<String> THREAD_SLEEP_ALLOW_LIST = Set.of(
            "regression-core/src/main/java/com/aqa/core/utils/WaitUtils.java");

    private static final Set<String> FIELD_INJECTION_ANNOTATIONS = Set.of("Autowired", "Inject", "Resource");
    private static final Set<String> STATIC_MUTABLE_UI_TYPE_NAMES = Set.of("WebDriver", "Browser", "BrowserContext", "Page");

    /** FC-004's advisory (non-blocking) rule id — FrameworkConventionsTool presents violations carrying this id in
     * a separate "advisoryViolations" bucket rather than the blocking "violations" list, per the explicit
     * advisory-only status accepted in Gate 15.1 and reconfirmed in Gate 15.4 Phase A. */
    static final String RECORDS_FOR_VALUE_OBJECTS_RULE_ID = "FC-004";

    private FrameworkConventionRules() {
    }

    static List<ValidationRule> all() {
        return List.of(new SeleniumLocatorFields(), new PlaywrightLocatorFields(), new NoThreadSleep(),
                new NoPlaywrightWaitForTimeout(), new ConstructorInjection(), new RecordsForValueObjects(),
                new NoStaticMutableUiState());
    }

    /** FC-001: a Selenium `By` field must be `private static final`; a `private static` factory method returning
     * `By` for dynamic/parameterized locators is never itself examined, so it is naturally never flagged. Gated on
     * `import org.openqa.selenium.By`. */
    private static final class SeleniumLocatorFields implements ValidationRule {
        @Override
        public String id() {
            return "FC-001";
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
                if (!importsExactType(unit, SELENIUM_BY_FQCN)) {
                    continue;
                }
                for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
                    if (!"By".equals(field.getElementType().asString())) {
                        continue;
                    }
                    if (!isPrivateStaticFinal(field)) {
                        violations.add(violation(id(), sourceUnit, field,
                                "Selenium By locator field must be private static final: " + fieldNames(field)));
                    }
                }
            }
            return List.copyOf(violations);
        }
    }

    /** FC-001-PW: a Playwright `Locator` field must be `private final`, assigned only in the constructor (the
     * language enforces single-assignment for a `final` field, so a mutable field is the actual violation shape);
     * dynamic lookups expressed as private instance methods returning `Locator` are never themselves examined.
     * Gated on any `com.microsoft.playwright.*` import. This is a Gate 15.4 Phase A design finding, not a literal
     * mirror of FC-001: Playwright Locator fields are bound to a Page/Locator instance at creation time and can
     * never legitimately be static. */
    private static final class PlaywrightLocatorFields implements ValidationRule {
        @Override
        public String id() {
            return "FC-001-PW";
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
                if (!importsPackagePrefix(unit, PLAYWRIGHT_PACKAGE_PREFIX)) {
                    continue;
                }
                for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
                    if (!"Locator".equals(field.getElementType().asString())) {
                        continue;
                    }
                    if (!(field.hasModifier(Modifier.Keyword.PRIVATE) && field.hasModifier(Modifier.Keyword.FINAL))) {
                        violations.add(violation(id(), sourceUnit, field,
                                "Playwright Locator field must be private final, assigned only in the constructor: " + fieldNames(field)));
                    }
                }
            }
            return List.copyOf(violations);
        }
    }

    /** FC-002: no Thread.sleep calls anywhere in the reactor, technology-agnostic, except the explicit
     * THREAD_SLEEP_ALLOW_LIST of legitimate wait-abstraction implementation files. */
    private static final class NoThreadSleep implements ValidationRule {
        @Override
        public String id() {
            return "FC-002";
        }

        @Override
        public Set<RuleProfile> profiles() {
            return EnumSet.allOf(RuleProfile.class);
        }

        @Override
        public List<Violation> evaluate(EvaluationContext context) {
            List<Violation> violations = new ArrayList<>();
            for (SourceUnit sourceUnit : context.moduleSources()) {
                if (THREAD_SLEEP_ALLOW_LIST.contains(sourceUnit.relativePath())) {
                    continue;
                }
                for (MethodCallExpr call : sourceUnit.unit().findAll(MethodCallExpr.class)) {
                    if (!"sleep".equals(call.getNameAsString())) {
                        continue;
                    }
                    if (call.getScope().filter(scope -> scope instanceof NameExpr name && "Thread".equals(name.getNameAsString())).isPresent()) {
                        violations.add(violation(id(), sourceUnit, call, "Thread.sleep is not allowed; use the module's wait abstraction instead."));
                    }
                }
            }
            return List.copyOf(violations);
        }
    }

    /** FC-002-PW: no Page/Locator/Frame waitForTimeout(...) calls, Playwright's own hardcoded-wait anti-pattern.
     * Gated on any `com.microsoft.playwright.*` import. Forward-looking: the real reactor has zero occurrences
     * today, confirmed in Gate 15.4 Phase A, so this rule ships with no allow-list. */
    private static final class NoPlaywrightWaitForTimeout implements ValidationRule {
        @Override
        public String id() {
            return "FC-002-PW";
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
                if (!importsPackagePrefix(unit, PLAYWRIGHT_PACKAGE_PREFIX)) {
                    continue;
                }
                for (MethodCallExpr call : unit.findAll(MethodCallExpr.class)) {
                    if ("waitForTimeout".equals(call.getNameAsString())) {
                        violations.add(violation(id(), sourceUnit, call,
                                "Playwright waitForTimeout is not allowed; rely on Playwright's own actionability auto-waiting instead."));
                    }
                }
            }
            return List.copyOf(violations);
        }
    }

    /** FC-003: constructor injection for collaborators, profile-agnostic. Flags (a) a field annotated with a
     * field-injection annotation (@Autowired, @Inject, @Resource — this reactor uses no DI framework anywhere), or
     * (b) a field assigned by a public/package setter method outside the constructor. Deliberately does not flag
     * plain non-final mutable fields in general: scenario-local mutable state (e.g. a Cucumber steps class's
     * currently-open page reference) is legitimate and common in this codebase, and a broader rule would
     * false-positive heavily on it. */
    private static final class ConstructorInjection implements ValidationRule {
        @Override
        public String id() {
            return "FC-003";
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
                for (FieldDeclaration field : unit.findAll(FieldDeclaration.class)) {
                    for (AnnotationExpr annotation : field.getAnnotations()) {
                        if (FIELD_INJECTION_ANNOTATIONS.contains(annotation.getNameAsString())) {
                            violations.add(violation(id(), sourceUnit, field,
                                    "Field-injection annotation is not allowed; use constructor injection instead: @" + annotation.getNameAsString()));
                        }
                    }
                }
                for (ClassOrInterfaceDeclaration type : unit.findAll(ClassOrInterfaceDeclaration.class)) {
                    violations.addAll(setterAssignedFieldViolations(sourceUnit, type));
                }
            }
            return List.copyOf(violations);
        }

        private static List<Violation> setterAssignedFieldViolations(SourceUnit sourceUnit, ClassOrInterfaceDeclaration type) {
            Set<String> fieldNames = new java.util.HashSet<>();
            for (FieldDeclaration field : type.getFields()) {
                field.getVariables().forEach(variable -> fieldNames.add(variable.getNameAsString()));
            }
            List<Violation> violations = new ArrayList<>();
            for (MethodDeclaration method : type.getMethods()) {
                if (!isSetterName(method.getNameAsString()) || isPrivateOrProtected(method)) {
                    continue;
                }
                for (AssignExpr assignment : method.findAll(AssignExpr.class)) {
                    String targetField = simpleAssignmentTargetName(assignment.getTarget());
                    if (targetField != null && fieldNames.contains(targetField)) {
                        violations.add(violation("FC-003", sourceUnit, assignment, "Field '" + targetField
                                + "' is assigned outside the constructor by setter '" + method.getNameAsString() + "'; use constructor injection instead."));
                    }
                }
            }
            return violations;
        }

        private static boolean isSetterName(String name) {
            return name.length() > 3 && name.startsWith("set") && Character.isUpperCase(name.charAt(3));
        }

        private static boolean isPrivateOrProtected(MethodDeclaration method) {
            return method.hasModifier(Modifier.Keyword.PRIVATE) || method.hasModifier(Modifier.Keyword.PROTECTED);
        }

        private static String simpleAssignmentTargetName(Expression target) {
            if (target instanceof NameExpr name) {
                return name.getNameAsString();
            }
            if (target instanceof FieldAccessExpr fieldAccess && fieldAccess.getScope() instanceof ThisExpr) {
                return fieldAccess.getNameAsString();
            }
            return null;
        }
    }

    /** FC-004 (advisory-only): flags a non-record class whose every field is private final, with exactly one
     * all-args constructor and only plain getters, as a "candidate value object not declared as a record" finding.
     * Heuristic and lower-precision by design (Gate 15.1 decision); JavaParser models `record` declarations as a
     * distinct node type from ClassOrInterfaceDeclaration, so an actual record is never examined and never
     * flagged. */
    private static final class RecordsForValueObjects implements ValidationRule {
        @Override
        public String id() {
            return RECORDS_FOR_VALUE_OBJECTS_RULE_ID;
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
                    if (isCandidateValueObject(type)) {
                        violations.add(violation(id(), sourceUnit, type,
                                "Candidate value object not declared as a record: " + type.getNameAsString()));
                    }
                }
            }
            return List.copyOf(violations);
        }

        private static boolean isCandidateValueObject(ClassOrInterfaceDeclaration type) {
            if (type.isInterface()) {
                return false;
            }
            List<FieldDeclaration> fields = type.getFields();
            if (fields.isEmpty() || !fields.stream().allMatch(RecordsForValueObjects::isPrivateFinalInstanceField)) {
                return false;
            }
            List<ConstructorDeclaration> constructors = type.getConstructors();
            if (constructors.size() != 1) {
                return false;
            }
            int totalFieldCount = fields.stream().mapToInt(field -> field.getVariables().size()).sum();
            if (constructors.getFirst().getParameters().size() != totalFieldCount) {
                return false;
            }
            Set<String> fieldNames = new java.util.HashSet<>();
            fields.forEach(field -> field.getVariables().forEach(variable -> fieldNames.add(variable.getNameAsString())));
            return type.getMethods().stream().allMatch(method -> isPlainGetter(method, fieldNames));
        }

        private static boolean isPrivateFinalInstanceField(FieldDeclaration field) {
            return field.hasModifier(Modifier.Keyword.PRIVATE) && field.hasModifier(Modifier.Keyword.FINAL)
                    && !field.hasModifier(Modifier.Keyword.STATIC);
        }

        /** A "plain getter" returns exactly one of the class's own fields verbatim — no computation, formatting,
         * or delegation — so a method like distanceFromOrigin() (computed from fields) is correctly treated as
         * business logic, not a getter, and does not count toward the "only plain getters" condition. */
        private static boolean isPlainGetter(MethodDeclaration method, Set<String> fieldNames) {
            if (!method.getParameters().isEmpty()) {
                return false;
            }
            return method.getBody().map(body -> isBareFieldReturn(body, fieldNames)).orElse(false);
        }

        private static boolean isBareFieldReturn(BlockStmt body, Set<String> fieldNames) {
            List<Statement> statements = body.getStatements();
            if (statements.size() != 1 || !(statements.getFirst() instanceof ReturnStmt returnStmt)) {
                return false;
            }
            return returnStmt.getExpression().map(expression -> isBareFieldReference(expression, fieldNames)).orElse(false);
        }

        private static boolean isBareFieldReference(Expression expression, Set<String> fieldNames) {
            if (expression instanceof NameExpr name) {
                return fieldNames.contains(name.getNameAsString());
            }
            if (expression instanceof FieldAccessExpr fieldAccess && fieldAccess.getScope() instanceof ThisExpr) {
                return fieldNames.contains(fieldAccess.getNameAsString());
            }
            return false;
        }
    }

    /** FC-005: a static non-final field typed WebDriver, Browser, BrowserContext, Page, or a page-object type
     * (heuristic: simple type name ends with "Page" or "Component") is a violation. One rule covering both
     * technologies, technology-agnostic by construction. */
    private static final class NoStaticMutableUiState implements ValidationRule {
        @Override
        public String id() {
            return "FC-005";
        }

        @Override
        public Set<RuleProfile> profiles() {
            return EnumSet.allOf(RuleProfile.class);
        }

        @Override
        public List<Violation> evaluate(EvaluationContext context) {
            List<Violation> violations = new ArrayList<>();
            for (SourceUnit sourceUnit : context.moduleSources()) {
                for (FieldDeclaration field : sourceUnit.unit().findAll(FieldDeclaration.class)) {
                    if (!field.hasModifier(Modifier.Keyword.STATIC) || field.hasModifier(Modifier.Keyword.FINAL)) {
                        continue;
                    }
                    String typeName = field.getElementType().asString();
                    if (STATIC_MUTABLE_UI_TYPE_NAMES.contains(typeName) || typeName.endsWith("Page") || typeName.endsWith("Component")) {
                        violations.add(violation(id(), sourceUnit, field,
                                "Static mutable UI/browser state is not allowed; keep it scenario-local: " + fieldNames(field)));
                    }
                }
            }
            return List.copyOf(violations);
        }
    }

    private static boolean isPrivateStaticFinal(FieldDeclaration field) {
        return field.hasModifier(Modifier.Keyword.PRIVATE) && field.hasModifier(Modifier.Keyword.STATIC)
                && field.hasModifier(Modifier.Keyword.FINAL);
    }

    private static String fieldNames(FieldDeclaration field) {
        return field.getVariables().stream().map(variable -> variable.getNameAsString())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static boolean importsExactType(CompilationUnit unit, String fqcn) {
        String packageName = fqcn.substring(0, fqcn.lastIndexOf('.'));
        for (ImportDeclaration importDeclaration : unit.getImports()) {
            String name = importDeclaration.getNameAsString();
            if (importDeclaration.isAsterisk() ? name.equals(packageName) : name.equals(fqcn)) {
                return true;
            }
        }
        return false;
    }

    private static boolean importsPackagePrefix(CompilationUnit unit, String prefix) {
        String prefixNoTrailingDot = prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix;
        for (ImportDeclaration importDeclaration : unit.getImports()) {
            String name = importDeclaration.getNameAsString();
            if (name.equals(prefixNoTrailingDot) || name.startsWith(prefix)) {
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
