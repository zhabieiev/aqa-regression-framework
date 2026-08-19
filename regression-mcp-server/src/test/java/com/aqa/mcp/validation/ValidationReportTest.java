package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Compact-constructor invariant coverage, added after being identified as missing from the original test
 * inventory. */
class ValidationReportTest {

    private static final ModuleValidationResult RESULT =
            new ModuleValidationResult("regression-core", RuleProfile.CORE, List.of(), List.of(), false);

    @Test
    void constructsWithValidModules() {
        ValidationReport report = new ValidationReport(List.of(RESULT));

        assertThat(report.modules()).containsExactly(RESULT);
    }

    @Test
    void defaultsANullModuleListToEmpty() {
        ValidationReport report = new ValidationReport(null);

        assertThat(report.modules()).isEmpty();
    }

    @Test
    void defensivelyCopiesItsModuleList() {
        List<ModuleValidationResult> mutable = new ArrayList<>(List.of(RESULT));
        ValidationReport report = new ValidationReport(mutable);
        mutable.clear();

        assertThat(report.modules()).containsExactly(RESULT);
        assertThatThrownBy(() -> report.modules().add(RESULT)).isInstanceOf(UnsupportedOperationException.class);
    }
}
