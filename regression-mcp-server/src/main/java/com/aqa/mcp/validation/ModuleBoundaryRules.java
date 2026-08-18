package com.aqa.mcp.validation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.javaparser.ast.ImportDeclaration;

/**
 * The fixed rule list backing regression_validate_module_boundaries, direct translations of CLAUDE.md's module
 * boundary rules. These are not profile-specific: every rule declares EnumSet.allOf(RuleProfile.class).
 */
final class ModuleBoundaryRules {

    private static final Set<String> SIBLING_PRODUCT_MODULES = Set.of(
            "regression-petstore-api", "regression-jhipster", "regression-nextjs-commerce");
    private static final String COMMERCE_MODULE = "regression-nextjs-commerce";
    private static final String MCP_MODULE = "regression-mcp-server";
    private static final String CORE_MODULE = "regression-core";
    private static final List<String> FORBIDDEN_API_CLIENT_IMPORT_PREFIXES = List.of(
            "jakarta.ws.rs.", "org.glassfish.jersey.");

    private ModuleBoundaryRules() {
    }

    static List<ValidationRule> all() {
        return List.of(new SiblingModuleIndependence(), new CommerceHasNoApiClients(),
                new McpServerIndependence(), new CoreIndependence());
    }

    /** MOD-001: sibling product modules must not depend on one another. */
    private static final class SiblingModuleIndependence implements ValidationRule {
        @Override
        public String id() {
            return "MOD-001";
        }

        @Override
        public Set<RuleProfile> profiles() {
            return EnumSet.allOf(RuleProfile.class);
        }

        @Override
        public List<Violation> evaluate(EvaluationContext context) {
            if (!SIBLING_PRODUCT_MODULES.contains(context.module())) {
                return List.of();
            }
            Set<String> forbidden = SIBLING_PRODUCT_MODULES.stream()
                    .filter(module -> !module.equals(context.module())).collect(Collectors.toSet());
            return forbiddenModuleImports(context, forbidden, id(),
                    " must not depend on sibling product module ");
        }
    }

    /** MOD-002: do not add API clients/services/scenarios to regression-nextjs-commerce. */
    private static final class CommerceHasNoApiClients implements ValidationRule {
        @Override
        public String id() {
            return "MOD-002";
        }

        @Override
        public Set<RuleProfile> profiles() {
            return EnumSet.allOf(RuleProfile.class);
        }

        @Override
        public List<Violation> evaluate(EvaluationContext context) {
            if (!COMMERCE_MODULE.equals(context.module())) {
                return List.of();
            }
            List<Violation> violations = new ArrayList<>();
            for (SourceUnit sourceUnit : context.moduleSources()) {
                for (ImportDeclaration importDeclaration : sourceUnit.unit().getImports()) {
                    String name = importDeclaration.getNameAsString();
                    if (FORBIDDEN_API_CLIENT_IMPORT_PREFIXES.stream().anyMatch(name::startsWith)) {
                        violations.add(violation(id(), sourceUnit, importDeclaration,
                                COMMERCE_MODULE + " must not import API client libraries: " + name));
                    }
                }
            }
            return List.copyOf(violations);
        }
    }

    /** MOD-003: regression-mcp-server must not depend on regression-core or any product module. */
    private static final class McpServerIndependence implements ValidationRule {
        @Override
        public String id() {
            return "MOD-003";
        }

        @Override
        public Set<RuleProfile> profiles() {
            return EnumSet.allOf(RuleProfile.class);
        }

        @Override
        public List<Violation> evaluate(EvaluationContext context) {
            if (!MCP_MODULE.equals(context.module())) {
                return List.of();
            }
            return forbiddenModuleImports(context, otherDeclaredModules(context, MCP_MODULE), id(), " must not depend on ");
        }
    }

    /** MOD-004: regression-core must not depend on any product module or on regression-mcp-server. */
    private static final class CoreIndependence implements ValidationRule {
        @Override
        public String id() {
            return "MOD-004";
        }

        @Override
        public Set<RuleProfile> profiles() {
            return EnumSet.allOf(RuleProfile.class);
        }

        @Override
        public List<Violation> evaluate(EvaluationContext context) {
            if (!CORE_MODULE.equals(context.module())) {
                return List.of();
            }
            return forbiddenModuleImports(context, otherDeclaredModules(context, CORE_MODULE), id(), " must not depend on ");
        }
    }

    private static Set<String> otherDeclaredModules(EvaluationContext context, String self) {
        return context.reactorModules().stream().map(ModuleProfile::module)
                .filter(module -> !module.equals(self)).collect(Collectors.toSet());
    }

    private static List<Violation> forbiddenModuleImports(EvaluationContext context, Set<String> forbiddenModules,
            String ruleId, String messagePrefix) {
        List<Violation> violations = new ArrayList<>();
        for (SourceUnit sourceUnit : context.moduleSources()) {
            for (ImportDeclaration importDeclaration : sourceUnit.unit().getImports()) {
                String importName = importDeclaration.getNameAsString();
                for (ModuleProfile candidate : context.reactorModules()) {
                    if (!forbiddenModules.contains(candidate.module())) {
                        continue;
                    }
                    if (matchesBasePackage(importName, candidate.basePackage())) {
                        violations.add(violation(ruleId, sourceUnit, importDeclaration,
                                context.module() + messagePrefix + candidate.module() + ": " + importName));
                        break;
                    }
                }
            }
        }
        return List.copyOf(violations);
    }

    private static boolean matchesBasePackage(String importName, String basePackage) {
        return !basePackage.isBlank() && (importName.equals(basePackage) || importName.startsWith(basePackage + "."));
    }

    private static Violation violation(String ruleId, SourceUnit sourceUnit, ImportDeclaration importDeclaration, String message) {
        int line = importDeclaration.getBegin().map(position -> position.line).orElse(1);
        return new Violation(ruleId, sourceUnit.module(), sourceUnit.relativePath(), line, message);
    }
}
