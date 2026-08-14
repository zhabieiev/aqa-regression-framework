package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenInvocationFactoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAnImmutableDirectClassworldsInvocationWithOneCompleteTagArgument() throws Exception {
        MavenRuntimeConfiguration runtime = runtime();
        Path root = repositoryRoot();
        ValidatedTestRunRequest request = request("(@cart or @catalog) and not @slow", true);

        MavenInvocation invocation = MavenInvocationFactory.create(runtime, root, request);

        assertThat(invocation.javaExecutable()).isEqualTo(runtime.javaExecutable());
        assertThat(invocation.workingDirectory()).isEqualTo(root.toRealPath());
        assertThat(invocation.arguments()).containsExactly(
                "--enable-native-access=ALL-UNNAMED",
                "-classpath", runtime.classworldsJar().toString(),
                "-Dclassworlds.conf=" + runtime.classworldsConfiguration(),
                "-Dmaven.home=" + runtime.mavenHome(),
                "-Dlibrary.jansi.path=" + runtime.jansiNativePath(),
                "-Dmaven.multiModuleProjectDirectory=" + root.toRealPath(),
                "org.codehaus.plexus.classworlds.launcher.Launcher",
                "-f", "regression-nextjs-commerce/pom.xml",
                "test",
                "-Denv=dev",
                "-Dui.headless=true",
                "-Dcucumber.filter.tags=" + request.effectiveTagExpression());
        assertThat(invocation.arguments().stream().filter(argument -> argument.startsWith("-Dcucumber.filter.tags=")))
                .containsExactly("-Dcucumber.filter.tags=((@cart or @catalog) and not @slow) and not @wip");
        assertThat(invocation.arguments()).doesNotContain("mvn", "mvn.cmd", "cmd.exe", "powershell.exe", "/C");
        assertThatThrownBy(() -> invocation.arguments().add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requestDataCanOnlyInfluenceTheValidatedCapabilitiesAndTagValue() throws Exception {
        MavenRuntimeConfiguration runtime = runtime();
        Path root = repositoryRoot();
        MavenInvocation cart = MavenInvocationFactory.create(runtime, root, request("@cart", false));
        MavenInvocation catalog = MavenInvocationFactory.create(runtime, root, request("@catalog", true));

        assertThat(cart.javaExecutable()).isEqualTo(catalog.javaExecutable());
        assertThat(cart.workingDirectory()).isEqualTo(catalog.workingDirectory());
        assertThat(cart.arguments()).contains("-f", "regression-nextjs-commerce/pom.xml", "test",
                "-Denv=dev", "-Dui.headless=false", "-Dcucumber.filter.tags=(@cart) and not @wip");
        assertThat(catalog.arguments()).contains("-f", "regression-nextjs-commerce/pom.xml", "test",
                "-Denv=dev", "-Dui.headless=true", "-Dcucumber.filter.tags=(@catalog) and not @wip");
        assertThat(cart.arguments()).doesNotContain("-Dcucumber.execution.dry-run=true");
    }

    @Test
    void failsClosedWhenTrustedRuntimeOrRepositoryRootIsInvalid() throws Exception {
        MavenRuntimeConfiguration runtime = runtime();
        Path root = repositoryRoot();
        assertCode("MAVEN_RUNTIME_UNAVAILABLE",
                () -> MavenRuntimeConfiguration.fromTrustedPaths(temporaryDirectory.resolve("missing-java"), temporaryDirectory));
        assertCode("MAVEN_RUNTIME_UNAVAILABLE",
                () -> MavenInvocationFactory.create(runtime, temporaryDirectory.resolve("missing-root"), request("@cart", true)));
        assertCode("MAVEN_RUNTIME_UNAVAILABLE",
                () -> MavenInvocationFactory.create(null, root, request("@cart", true)));
        assertCode("INVALID_ARGUMENTS", () -> MavenInvocationFactory.create(runtime, root, null));
        assertThatThrownBy(() -> MavenRuntimeConfiguration.fromTrustedPaths(temporaryDirectory.resolve("missing-java"), temporaryDirectory))
                .extracting(Throwable::getMessage)
                .asString().doesNotContain(temporaryDirectory.toString());
    }

    @Test
    void defensivelyCopiesInvocationArgumentsAndRestrictsRuntimeConstructionToTheExecutionPackage() {
        List<String> arguments = new ArrayList<>(List.of("test"));
        MavenInvocation invocation = new MavenInvocation(Path.of("java.exe"), Path.of("."), arguments);
        arguments.clear();

        assertThat(invocation.arguments()).containsExactly("test");
        assertThatThrownBy(() -> invocation.arguments().add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(MavenRuntimeConfiguration.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("fromTrustedPaths"))
                .allMatch(method -> !java.lang.reflect.Modifier.isPublic(method.getModifiers()));
    }

    private MavenRuntimeConfiguration runtime() throws Exception {
        Path java = Files.createFile(temporaryDirectory.resolve("java.exe"));
        Path home = Files.createDirectories(temporaryDirectory.resolve("maven-home"));
        Files.createDirectories(home.resolve("bin"));
        Files.createFile(home.resolve("bin").resolve("m2.conf"));
        Path boot = Files.createDirectories(home.resolve("boot"));
        Files.createFile(boot.resolve("plexus-classworlds-2.11.0.jar"));
        Files.createDirectories(home.resolve("lib").resolve("jansi-native"));
        return MavenRuntimeConfiguration.fromTrustedPaths(java, home);
    }

    private Path repositoryRoot() throws Exception {
        Path root = Files.createDirectories(temporaryDirectory.resolve("repository"));
        Files.createFile(root.resolve("pom.xml"));
        return root;
    }

    private static ValidatedTestRunRequest request(String effectiveTags, boolean headless) {
        return new TestRunRequestValidator(List.of("regression-nextjs-commerce"))
                .validate(new StartTestRunRequest("regression-nextjs-commerce", effectiveTags, "dev", headless, 900));
    }

    private static void assertCode(String expectedCode, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(ExecutionPlanningException.class)
                .extracting(exception -> ((ExecutionPlanningException) exception).code())
                .isEqualTo(expectedCode);
    }
}
