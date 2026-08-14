package com.aqa.mcp.execution;

import java.io.IOException;
import java.util.List;

final class DirectMavenProcessLauncher implements MavenProcessLauncher {
    @Override
    public Process launch(MavenInvocation invocation) {
        List<String> command = new java.util.ArrayList<>(); command.add(invocation.javaExecutable().toString()); command.addAll(invocation.arguments());
        try { return new ProcessBuilder(command).directory(invocation.workingDirectory().toFile()).redirectErrorStream(false).start(); }
        catch (IOException e) { throw new ExecutionPlanningException("MAVEN_LAUNCH_FAILED", "Maven could not be started."); }
    }
}
