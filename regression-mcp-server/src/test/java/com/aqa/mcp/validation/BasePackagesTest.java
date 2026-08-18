package com.aqa.mcp.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.github.javaparser.StaticJavaParser;
import org.junit.jupiter.api.Test;

class BasePackagesTest {

    @Test
    void returnsAnEmptyStringForNoSourceUnits() {
        assertThat(BasePackages.derive(List.of())).isEmpty();
    }

    @Test
    void derivesTheSinglePackageOfAOneFileModule() {
        SourceUnit unit = unit("com.aqa.jhipster.ui.pages.BasePage");

        assertThat(BasePackages.derive(List.of(unit))).isEqualTo("com.aqa.jhipster.ui.pages");
    }

    @Test
    void derivesTheLongestCommonPrefixAcrossMultiplePackages() {
        SourceUnit pages = unit("com.aqa.jhipster.ui.pages.BasePage");
        SourceUnit steps = unit("com.aqa.jhipster.api.steps.LoginSteps");

        assertThat(BasePackages.derive(List.of(pages, steps))).isEqualTo("com.aqa.jhipster");
    }

    @Test
    void treatsAMissingPackageDeclarationAsTheDefaultPackage() {
        SourceUnit noPackage = new SourceUnit("regression-mcp-server", "Scratch.java",
                StaticJavaParser.parse("class Scratch { }"));
        SourceUnit withPackage = unit("com.aqa.jhipster.ui.pages.BasePage");

        assertThat(BasePackages.derive(List.of(noPackage, withPackage))).isEmpty();
    }

    private static SourceUnit unit(String fullyQualifiedClassName) {
        int lastDot = fullyQualifiedClassName.lastIndexOf('.');
        String packageName = fullyQualifiedClassName.substring(0, lastDot);
        String simpleName = fullyQualifiedClassName.substring(lastDot + 1);
        String source = "package " + packageName + ";\nclass " + simpleName + " { }\n";
        return new SourceUnit("regression-jhipster", simpleName + ".java", StaticJavaParser.parse(source));
    }
}
