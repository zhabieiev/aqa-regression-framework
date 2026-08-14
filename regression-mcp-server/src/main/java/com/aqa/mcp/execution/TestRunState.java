package com.aqa.mcp.execution;

public enum TestRunState {
    QUEUED(false),
    RUNNING(false),
    PASSED(true),
    FAILED(true),
    CANCELLED(true),
    TIMED_OUT(true),
    ERROR(true);

    private final boolean terminal;

    TestRunState(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
