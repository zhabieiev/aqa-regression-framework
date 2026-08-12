package com.aqa.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleListTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsOnlyModulesDeclaredByAValidParentPom() throws Exception {
        createModule("regression-core", true);
        createModule("regression-petstore-api", true);
        createModule("regression-jhipster", true);
        createModule("regression-nextjs-commerce", true);
        createModule("regression-mcp-server", true);
        Files.createDirectory(temporaryDirectory.resolve("not-declared"));
        writePom("regression-core", "regression-petstore-api", "regression-jhipster",
                "regression-nextjs-commerce", "regression-mcp-server");

        List<ModuleDescriptor> modules = moduleList().modules();

        assertThat(modules).containsExactly(
                new ModuleDescriptor("regression-core", "regression-core", ModuleType.CORE, true, true),
                new ModuleDescriptor("regression-petstore-api", "regression-petstore-api", ModuleType.API, true, true),
                new ModuleDescriptor("regression-jhipster", "regression-jhipster", ModuleType.API_UI, true, true),
                new ModuleDescriptor("regression-nextjs-commerce", "regression-nextjs-commerce", ModuleType.UI, true, true),
                new ModuleDescriptor("regression-mcp-server", "regression-mcp-server", ModuleType.MCP, true, true));
    }

    @Test
    void preservesDeclarationOrder() throws Exception {
        createModule("third", true);
        createModule("first", true);
        createModule("second", true);
        writePom("third", "first", "second");

        assertThat(moduleList().modules()).extracting(ModuleDescriptor::name)
                .containsExactly("third", "first", "second");
    }

    @Test
    void reportsAMissingModuleDirectory() throws Exception {
        writePom("missing");

        assertThat(moduleList().modules()).containsExactly(
                new ModuleDescriptor("missing", "missing", ModuleType.UNKNOWN, false, false));
    }

    @Test
    void reportsAMissingModulePom() throws Exception {
        createModule("without-pom", false);
        writePom("without-pom");

        assertThat(moduleList().modules()).containsExactly(
                new ModuleDescriptor("without-pom", "without-pom", ModuleType.UNKNOWN, true, false));
    }

    @Test
    void rejectsDuplicateModuleDeclarations() throws Exception {
        writePom("duplicate", "duplicate");

        assertThatIllegalArgumentException().isThrownBy(this::moduleList)
                .isInstanceOf(RepositoryInspectionException.class)
                .withMessage("Duplicate module declaration: duplicate");
    }

    @Test
    void classifiesUnrecognizedModuleNamesAsUnknown() throws Exception {
        createModule("custom-module", true);
        writePom("custom-module");

        assertThat(moduleList().modules().getFirst().type()).isEqualTo(ModuleType.UNKNOWN);
    }

    @Test
    void rejectsMalformedXml() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project><modules>");

        assertThatIllegalArgumentException().isThrownBy(this::moduleList)
                .isInstanceOf(RepositoryInspectionException.class)
                .withMessage("Unable to parse root pom.xml.");
    }

    @Test
    void rejectsDoctypeAndExternalEntities() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), """
                <!DOCTYPE project [<!ENTITY external SYSTEM \"file:///not-allowed\">]>
                <project><modules><module>&external;</module></modules></project>
                """);

        assertThatIllegalArgumentException().isThrownBy(this::moduleList)
                .isInstanceOf(RepositoryInspectionException.class)
                .withMessage("Unable to parse root pom.xml.");
    }

    @Test
    void rejectsAbsoluteModulePaths() throws Exception {
        writePom(temporaryDirectory.resolve("outside").toAbsolutePath().toString());

        assertThatIllegalArgumentException().isThrownBy(this::moduleList)
                .withMessageStartingWith("Module path must be relative:");
    }

    @Test
    void rejectsParentDirectoryTraversal() throws Exception {
        writePom("../outside");

        assertThatIllegalArgumentException().isThrownBy(this::moduleList)
                .withMessage("Module path escapes REGRESSION_ROOT: ../outside");
    }

    @Test
    void rejectsNormalizedPathsEscapingTheRepositoryRoot() throws Exception {
        writePom("nested/../../outside");

        assertThatIllegalArgumentException().isThrownBy(this::moduleList)
                .withMessage("Module path escapes REGRESSION_ROOT: nested/../../outside");
    }

    private ModuleList moduleList() {
        return ModuleList.forRoot(RepositoryRootResolver.resolve(temporaryDirectory));
    }

    private void createModule(String name, boolean withPom) throws Exception {
        Path module = Files.createDirectory(temporaryDirectory.resolve(name));
        if (withPom) {
            Files.writeString(module.resolve("pom.xml"), "<project/>");
        }
    }

    private void writePom(String... moduleNames) throws Exception {
        String modules = String.join("", java.util.Arrays.stream(moduleNames)
                .map(name -> "<module>" + name + "</module>").toList());
        Files.writeString(temporaryDirectory.resolve("pom.xml"),
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><modules>" + modules
                        + "</modules></project>");
    }
}
