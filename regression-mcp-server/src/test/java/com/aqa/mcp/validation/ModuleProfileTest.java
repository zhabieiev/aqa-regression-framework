package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ModuleProfileTest {

    @Test
    void constructsWithValidFields() {
        ModuleProfile profile = new ModuleProfile("regression-core", RuleProfile.CORE, "com.aqa.core");

        assertThat(profile.module()).isEqualTo("regression-core");
        assertThat(profile.profile()).isEqualTo(RuleProfile.CORE);
        assertThat(profile.basePackage()).isEqualTo("com.aqa.core");
    }

    @Test
    void allowsAnEmptyBasePackage() {
        ModuleProfile profile = new ModuleProfile("regression-core", RuleProfile.CORE, "");

        assertThat(profile.basePackage()).isEmpty();
    }

    @Test
    void rejectsNullFields() {
        assertThatNullPointerException().isThrownBy(() -> new ModuleProfile(null, RuleProfile.CORE, "com.aqa.core"));
        assertThatNullPointerException().isThrownBy(() -> new ModuleProfile("regression-core", null, "com.aqa.core"));
        assertThatNullPointerException().isThrownBy(() -> new ModuleProfile("regression-core", RuleProfile.CORE, null));
    }

    @Test
    void rejectsABlankModule() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ModuleProfile(" ", RuleProfile.CORE, "com.aqa.core"));
    }
}
