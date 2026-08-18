package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

/** Compact-constructor invariant coverage deferred from Gate 15.2 to Gate 15.3, added here in Gate 15.4 since it
 * was not yet present as of Gate 15.3's inventory. */
class SourceUnitTest {

    private static final CompilationUnit UNIT = StaticJavaParser.parse("class Foo { }");

    @Test
    void constructsWithValidFields() {
        SourceUnit sourceUnit = new SourceUnit("regression-core", "Foo.java", UNIT);

        assertThat(sourceUnit.module()).isEqualTo("regression-core");
        assertThat(sourceUnit.relativePath()).isEqualTo("Foo.java");
        assertThat(sourceUnit.unit()).isSameAs(UNIT);
    }

    @Test
    void rejectsNullFields() {
        assertThatNullPointerException().isThrownBy(() -> new SourceUnit(null, "Foo.java", UNIT));
        assertThatNullPointerException().isThrownBy(() -> new SourceUnit("regression-core", null, UNIT));
        assertThatNullPointerException().isThrownBy(() -> new SourceUnit("regression-core", "Foo.java", null));
    }

    @Test
    void rejectsABlankModule() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SourceUnit(" ", "Foo.java", UNIT));
    }

    @Test
    void rejectsABlankRelativePath() {
        assertThatIllegalArgumentException().isThrownBy(() -> new SourceUnit("regression-core", " ", UNIT));
    }
}
