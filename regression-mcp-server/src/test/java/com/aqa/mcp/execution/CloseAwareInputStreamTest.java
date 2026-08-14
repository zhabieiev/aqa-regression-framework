package com.aqa.mcp.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class CloseAwareInputStreamTest {
    @Test
    void invokesCallbackOnEofAndReadFailure() throws Exception {
        AtomicInteger eofCalls = new AtomicInteger();
        CloseAwareInputStream eof = new CloseAwareInputStream(InputStream.nullInputStream(), eofCalls::incrementAndGet);
        assertThat(eof.read()).isEqualTo(-1);
        assertThat(eofCalls).hasValue(1);

        AtomicInteger failureCalls = new AtomicInteger();
        CloseAwareInputStream failing = new CloseAwareInputStream(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("controlled read failure"); }
        }, failureCalls::incrementAndGet);
        assertThatThrownBy(failing::read).isInstanceOf(IOException.class);
        assertThat(failureCalls).hasValue(1);
    }
}
