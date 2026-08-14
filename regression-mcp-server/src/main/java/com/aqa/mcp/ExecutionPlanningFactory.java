package com.aqa.mcp;

import com.aqa.mcp.execution.TestRunRequestValidator;

final class ExecutionPlanningFactory {

    private ExecutionPlanningFactory() {
    }

    static TestRunRequestValidator validatorFor(RepositoryRoot repositoryRoot) {
        return new TestRunRequestValidator(ModuleList.forRoot(repositoryRoot).modules().stream()
                .map(ModuleDescriptor::name).toList());
    }
}
