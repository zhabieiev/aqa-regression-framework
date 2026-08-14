package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

class TestRunStateTest {

    @Test
    void explicitlyClassifiesTerminalStates() {
        assertThat(EnumSet.allOf(TestRunState.class).stream().filter(TestRunState::isTerminal))
                .containsExactlyInAnyOrder(TestRunState.PASSED, TestRunState.FAILED, TestRunState.CANCELLED,
                        TestRunState.TIMED_OUT, TestRunState.ERROR);
        assertThat(TestRunState.QUEUED.isTerminal()).isFalse();
        assertThat(TestRunState.RUNNING.isTerminal()).isFalse();
    }
}
