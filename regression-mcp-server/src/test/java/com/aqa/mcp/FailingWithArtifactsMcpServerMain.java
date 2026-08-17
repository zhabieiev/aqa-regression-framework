package com.aqa.mcp;

import com.aqa.mcp.execution.ControlledCoordinatorFactory;

/** Test-only executable STDIO composition producing a genuine failing run with real captured Surefire/Allure
 * artifacts. Absent from the shaded production JAR, the same as {@link ControlledMcpServerMain}. */
public final class FailingWithArtifactsMcpServerMain {
    private FailingWithArtifactsMcpServerMain() { }
    public static void main(String[] arguments) {
        RepositoryRoot root = RepositoryRootResolver.resolve(System.getenv());
        RegressionMcpServer.createServer(root, ControlledCoordinatorFactory.failingCoordinatorWithArtifacts(root.path()));
    }
}
