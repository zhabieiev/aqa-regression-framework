package com.aqa.mcp.execution;

/** Capture outcome is intentionally separate from Maven's execution result. */
enum CaptureStatus {
    PENDING,
    COMPLETE,
    PARTIAL,
    UNAVAILABLE
}
