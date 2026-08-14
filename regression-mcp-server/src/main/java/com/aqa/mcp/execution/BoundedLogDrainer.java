package com.aqa.mcp.execution;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

final class BoundedLogDrainer implements Runnable {
    static final long FILE_LIMIT = 16L * 1024 * 1024;
    static final int TAIL_LIMIT = 64 * 1024;
    private final InputStream input; private final Path log; private final byte[] tail = new byte[TAIL_LIMIT]; private volatile long observed; private volatile long written; private volatile long dropped; private int tailSize; private int tailStart;
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    BoundedLogDrainer(InputStream input, Path log) { this.input = input; this.log = log; }
    public void run() {
        byte[] buffer = new byte[8192];
        try (input; OutputStream output = Files.newOutputStream(log)) { for (int count; (count = input.read(buffer)) >= 0;) { appendTail(buffer, count); observed += count; long writable = Math.max(0, FILE_LIMIT - written); int accepted = (int)Math.min(count, writable); if (accepted > 0) output.write(buffer, 0, accepted); written += accepted; dropped += count - accepted; } }
        catch (IOException ignored) { /* lifecycle converts launch/drain failure to a sanitized terminal error */ }
        finally { completion.complete(null); }
    }
    long bytes() { return written; } long observedBytes() { return observed; } boolean truncated() { return dropped > 0; }
    long droppedBytes() { return dropped; }
    CompletableFuture<Void> completion() { return completion; }
    synchronized byte[] tail() { byte[] result = new byte[tailSize]; for (int i = 0; i < tailSize; i++) result[i] = tail[(tailStart + i) % TAIL_LIMIT]; return result; }
    private synchronized void appendTail(byte[] source, int count) { for (int i = 0; i < count; i++) { if (tailSize < TAIL_LIMIT) tail[(tailStart + tailSize++) % TAIL_LIMIT] = source[i]; else { tail[tailStart] = source[i]; tailStart = (tailStart + 1) % TAIL_LIMIT; } } }
}
