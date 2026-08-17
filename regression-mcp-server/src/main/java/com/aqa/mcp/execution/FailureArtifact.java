package com.aqa.mcp.execution;

/** Derived, read-time-computed view over one file in a published capture index. Never itself persisted. */
public record FailureArtifact(String artifactId, String name, String mimeType, long size, String relativePath) { }
