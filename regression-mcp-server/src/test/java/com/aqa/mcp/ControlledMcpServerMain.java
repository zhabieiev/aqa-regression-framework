package com.aqa.mcp;

import com.aqa.mcp.execution.ControlledCoordinatorFactory;

/** Test-only executable STDIO composition. It is absent from the shaded production JAR. */
public final class ControlledMcpServerMain {
    private ControlledMcpServerMain() { }
    public static void main(String[] arguments) {
        RepositoryRoot root = RepositoryRootResolver.resolve(System.getenv());
        RegressionMcpServer.createServer(root, ControlledCoordinatorFactory.waitingCoordinator(root.path()));
    }
}
