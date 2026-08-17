package com.aqa.mcp.execution;

import java.util.List;

/** On-disk, run-bound index; readers consume this object and never discover reports by walking directories. */
record PublishedReportIndex(int schemaVersion, String runId, String kind, List<CaptureMetadata.IndexedFile> files,
        SurefireSummary summary) {
    PublishedReportIndex { files = List.copyOf(files == null ? List.of() : files); }
}
