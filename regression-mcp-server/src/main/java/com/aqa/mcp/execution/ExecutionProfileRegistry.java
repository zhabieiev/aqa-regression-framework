package com.aqa.mcp.execution;

import java.util.List;
import java.util.Map;

public final class ExecutionProfileRegistry {

    public static final String COMMERCE_MODULE = "regression-nextjs-commerce";
    public static final String JHIPSTER_MODULE = "regression-jhipster";
    private static final ExecutionProfile COMMERCE = new ExecutionProfile(COMMERCE_MODULE,
            "regression-nextjs-commerce/pom.xml", List.of("dev"), true);
    private static final ExecutionProfile JHIPSTER = new ExecutionProfile(JHIPSTER_MODULE,
            "regression-jhipster/pom.xml", List.of("dev"), true);
    private static final Map<String, ExecutionProfile> PROFILES = Map.of(
            COMMERCE_MODULE, COMMERCE,
            JHIPSTER_MODULE, JHIPSTER);

    private ExecutionProfileRegistry() {
    }

    public static ExecutionProfile requireProfile(String module, List<String> declaredModules) {
        if (module == null || module.isBlank() || declaredModules == null || !declaredModules.contains(module)) {
            throw unsupportedModule();
        }
        ExecutionProfile profile = PROFILES.get(module);
        if (profile == null) {
            throw unsupportedModule();
        }
        return profile;
    }

    public static List<ExecutionProfile> profiles() {
        return List.copyOf(PROFILES.values());
    }

    private static ExecutionPlanningException unsupportedModule() {
        return new ExecutionPlanningException("UNSUPPORTED_MODULE", "The selected module is not supported for test execution.");
    }
}
