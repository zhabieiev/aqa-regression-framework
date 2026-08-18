package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ViolationTest {

    @Test
    void constructsWithValidFields() {
        Violation violation = new Violation("MOD-001", "regression-jhipster", "Foo.java", 12, "message");

        assertThat(violation.ruleId()).isEqualTo("MOD-001");
        assertThat(violation.module()).isEqualTo("regression-jhipster");
        assertThat(violation.file()).isEqualTo("Foo.java");
        assertThat(violation.line()).isEqualTo(12);
        assertThat(violation.message()).isEqualTo("message");
    }

    @Test
    void rejectsNullFields() {
        assertThatNullPointerException().isThrownBy(() -> new Violation(null, "module", "file", 1, "message"));
        assertThatNullPointerException().isThrownBy(() -> new Violation("id", null, "file", 1, "message"));
        assertThatNullPointerException().isThrownBy(() -> new Violation("id", "module", null, 1, "message"));
        assertThatNullPointerException().isThrownBy(() -> new Violation("id", "module", "file", 1, null));
    }

    @Test
    void rejectsBlankFields() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Violation(" ", "module", "file", 1, "message"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Violation("id", " ", "file", 1, "message"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Violation("id", "module", " ", 1, "message"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Violation("id", "module", "file", 1, " "));
    }

    @Test
    void rejectsANonPositiveLine() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Violation("id", "module", "file", 0, "message"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Violation("id", "module", "file", -1, "message"));
    }
}
