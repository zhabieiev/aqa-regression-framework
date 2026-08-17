package com.aqa.mcp.execution;

/** Bounded, MIME-allow-listed bytes for one server-generated artifactId, paired with its derived metadata. */
public record ArtifactContent(FailureArtifact metadata, byte[] content) { }
