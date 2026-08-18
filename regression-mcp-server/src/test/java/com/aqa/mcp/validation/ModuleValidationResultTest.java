package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class ModuleValidationResultTest {

    private static final Violation VIOLATION = new Violation("MOD-001", "regression-jhipster", "Foo.java", 1, "message");

    @Test
    void constructsWithValidFields() {
        ModuleValidationResult result = new ModuleValidationResult("regression-jhipster", RuleProfile.API_UI,
                List.of("MOD-001"), List.of(VIOLATION), false);

        assertThat(result.module()).isEqualTo("regression-jhipster");
        assertThat(result.profile()).isEqualTo(RuleProfile.API_UI);
        assertThat(result.rulesApplied()).containsExactly("MOD-001");
        assertThat(result.violations()).containsExactly(VIOLATION);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void defaultsNullListsToEmpty() {
        ModuleValidationResult result = new ModuleValidationResult("regression-jhipster", RuleProfile.API_UI, null, null, true);

        assertThat(result.rulesApplied()).isEmpty();
        assertThat(result.violations()).isEmpty();
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void defensivelyCopiesItsLists() {
        java.util.List<String> mutable = new java.util.ArrayList<>(List.of("MOD-001"));
        ModuleValidationResult result = new ModuleValidationResult("regression-jhipster", RuleProfile.API_UI, mutable, List.of(VIOLATION), false);
        mutable.clear();

        assertThat(result.rulesApplied()).containsExactly("MOD-001");
        assertThatThrownBy(() -> result.rulesApplied().add("MOD-002")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullModuleOrProfile() {
        assertThatNullPointerException().isThrownBy(() -> new ModuleValidationResult(null, RuleProfile.CORE, List.of(), List.of(), false));
        assertThatNullPointerException().isThrownBy(() -> new ModuleValidationResult("module", null, List.of(), List.of(), false));
    }

    @Test
    void rejectsABlankModule() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ModuleValidationResult(" ", RuleProfile.CORE, List.of(), List.of(), false));
    }
}
