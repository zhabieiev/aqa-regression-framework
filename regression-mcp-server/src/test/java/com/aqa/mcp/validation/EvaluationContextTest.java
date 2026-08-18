package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;

class EvaluationContextTest {

    private static final SourceUnit SOURCE_UNIT = new SourceUnit("regression-jhipster", "Foo.java",
            StaticJavaParser.parse("class Foo { }"));
    private static final ModuleProfile MODULE_PROFILE = new ModuleProfile("regression-core", RuleProfile.CORE, "com.aqa.core");

    @Test
    void constructsWithValidFields() {
        EvaluationContext context = new EvaluationContext("regression-jhipster", RuleProfile.API_UI,
                List.of(SOURCE_UNIT), List.of(MODULE_PROFILE));

        assertThat(context.module()).isEqualTo("regression-jhipster");
        assertThat(context.profile()).isEqualTo(RuleProfile.API_UI);
        assertThat(context.moduleSources()).containsExactly(SOURCE_UNIT);
        assertThat(context.reactorModules()).containsExactly(MODULE_PROFILE);
    }

    @Test
    void defaultsNullListsToEmpty() {
        EvaluationContext context = new EvaluationContext("regression-jhipster", RuleProfile.API_UI, null, null);

        assertThat(context.moduleSources()).isEmpty();
        assertThat(context.reactorModules()).isEmpty();
    }

    @Test
    void defensivelyCopiesItsLists() {
        java.util.List<SourceUnit> mutable = new java.util.ArrayList<>(List.of(SOURCE_UNIT));
        EvaluationContext context = new EvaluationContext("regression-jhipster", RuleProfile.API_UI, mutable, List.of(MODULE_PROFILE));
        mutable.clear();

        assertThat(context.moduleSources()).containsExactly(SOURCE_UNIT);
        assertThatThrownBy(() -> context.moduleSources().add(SOURCE_UNIT)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullModuleOrProfile() {
        assertThatNullPointerException().isThrownBy(() -> new EvaluationContext(null, RuleProfile.CORE, List.of(), List.of()));
        assertThatNullPointerException().isThrownBy(() -> new EvaluationContext("module", null, List.of(), List.of()));
    }

    @Test
    void rejectsABlankModule() {
        assertThatIllegalArgumentException().isThrownBy(() -> new EvaluationContext(" ", RuleProfile.CORE, List.of(), List.of()));
    }
}
