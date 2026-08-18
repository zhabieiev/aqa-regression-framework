package com.aqa.mcp.validation;

import java.util.List;
import java.util.Set;

public interface ValidationRule {

    String id();

    Set<RuleProfile> profiles();

    List<Violation> evaluate(EvaluationContext context);
}
