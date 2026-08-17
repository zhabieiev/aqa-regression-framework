package com.aqa.mcp.execution;

import java.util.List;

/** Internal persisted capture state. It contains no absolute filesystem paths. */
record CaptureMetadata(int schemaVersion, CaptureStatus status, String nonce, CaptureSet surefire, CaptureSet allure) {
    CaptureMetadata {
        if (schemaVersion < 1 || status == null || nonce == null || nonce.isBlank()) {
            throw new IllegalArgumentException("Capture metadata is incomplete.");
        }
    }

    static CaptureMetadata pending(String nonce) {
        return new CaptureMetadata(1, CaptureStatus.PENDING, nonce, null, null);
    }

    record CaptureSet(String status, List<IndexedFile> files, String indexSha256) {
        CaptureSet { files = List.copyOf(files == null ? List.of() : files); }
    }

    record IndexedFile(String path, long size, String sha256) { }
}
