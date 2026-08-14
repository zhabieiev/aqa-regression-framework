package com.aqa.mcp.execution;

/** Package-private lifecycle seam; MCP requests never select an implementation or command. */
interface MavenProcessLauncher {
    Process launch(MavenInvocation invocation);
}
