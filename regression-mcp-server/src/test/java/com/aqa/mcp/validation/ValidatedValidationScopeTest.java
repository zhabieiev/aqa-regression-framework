package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Compact-constructor invariant coverage, added after being identified as missing from the original test
 * inventory. */
class ValidatedValidationScopeTest {

    @Test
    void constructsWithValidModules() {
        ValidatedValidationScope scope = new ValidatedValidationScope(List.of("regression-core", "regression-jhipster"));

        assertThat(scope.modules()).containsExactly("regression-core", "regression-jhipster");
    }

    @Test
    void defaultsANullModuleListToEmpty() {
        ValidatedValidationScope scope = new ValidatedValidationScope(null);

        assertThat(scope.modules()).isEmpty();
    }

    @Test
    void defensivelyCopiesItsModuleList() {
        List<String> mutable = new ArrayList<>(List.of("regression-core"));
        ValidatedValidationScope scope = new ValidatedValidationScope(mutable);
        mutable.clear();

        assertThat(scope.modules()).containsExactly("regression-core");
        assertThatThrownBy(() -> scope.modules().add("regression-jhipster")).isInstanceOf(UnsupportedOperationException.class);
    }
}
