package com.aqa.mcp.execution;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Notifies the coordinator on EOF/read failure while preserving successful reads and original failures. */
public final class CloseAwareInputStream extends InputStream {
    private final InputStream delegate;
    private final Runnable onClose;
    public CloseAwareInputStream(InputStream delegate, Runnable onClose) {
        this.delegate = Objects.requireNonNull(delegate); this.onClose = Objects.requireNonNull(onClose);
    }
    @Override public int read() throws IOException { try { int value = delegate.read(); if (value < 0) onClose.run(); return value; } catch (IOException e) { onClose.run(); throw e; } }
    @Override public int read(byte[] bytes, int off, int len) throws IOException { try { int value = delegate.read(bytes, off, len); if (value < 0) onClose.run(); return value; } catch (IOException e) { onClose.run(); throw e; } }
    @Override public void close() throws IOException { delegate.close(); }
}
