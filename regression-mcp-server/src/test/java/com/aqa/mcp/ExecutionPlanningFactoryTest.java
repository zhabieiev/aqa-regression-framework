package com.aqa.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import com.aqa.mcp.execution.ExecutionPlanningException;
import com.aqa.mcp.execution.StartTestRunRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionPlanningFactoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void derivesExecutableMembershipFromTheRootPomModules() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), """
                <project><modules>
                  <module>regression-nextjs-commerce</module>
                  <module>regression-core</module>
                </modules></project>
                """);
        Files.createDirectory(temporaryDirectory.resolve("regression-nextjs-commerce"));
        Files.writeString(temporaryDirectory.resolve("regression-nextjs-commerce/pom.xml"), "<project/>");
        Files.createDirectory(temporaryDirectory.resolve("regression-core"));
        Files.writeString(temporaryDirectory.resolve("regression-core/pom.xml"), "<project/>");

        assertThat(ExecutionPlanningFactory.validatorFor(RepositoryRootResolver.resolve(temporaryDirectory))
                .validate(new StartTestRunRequest("regression-nextjs-commerce", null, "dev", true, 30)).profile().module())
                .isEqualTo("regression-nextjs-commerce");
        assertThatThrownBy(() -> ExecutionPlanningFactory.validatorFor(RepositoryRootResolver.resolve(temporaryDirectory))
                .validate(new StartTestRunRequest("regression-core", null, "dev", true, 30)))
                .isInstanceOf(ExecutionPlanningException.class)
                .extracting(exception -> ((ExecutionPlanningException) exception).code())
                .isEqualTo("UNSUPPORTED_MODULE");
    }

    @Test
    void failsClosedWhenTheStaticCommerceProfileIsAbsentFromTheParentPom() throws Exception {
        Files.writeString(temporaryDirectory.resolve("pom.xml"), "<project><modules/></project>");

        assertThatThrownBy(() -> ExecutionPlanningFactory.validatorFor(RepositoryRootResolver.resolve(temporaryDirectory))
                .validate(new StartTestRunRequest("regression-nextjs-commerce", null, "dev", true, 30)))
                .isInstanceOf(ExecutionPlanningException.class)
                .extracting(exception -> ((ExecutionPlanningException) exception).code())
                .isEqualTo("UNSUPPORTED_MODULE");
    }
}
