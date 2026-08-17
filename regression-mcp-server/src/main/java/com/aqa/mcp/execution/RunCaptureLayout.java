package com.aqa.mcp.execution;

import java.nio.file.Path;
import java.util.Objects;

/** Server-generated, run-bound locations. This is deliberately never serialized as public metadata. */
record RunCaptureLayout(Path runDirectory, Path surefireStaging, Path allureStaging, Path surefireFinal,
        Path allureFinal, Path surefireIndex, Path allureIndex) {
    RunCaptureLayout {
        Objects.requireNonNull(runDirectory, "runDirectory must not be null");
        Objects.requireNonNull(surefireStaging, "surefireStaging must not be null");
        Objects.requireNonNull(allureStaging, "allureStaging must not be null");
        Objects.requireNonNull(surefireFinal, "surefireFinal must not be null");
        Objects.requireNonNull(allureFinal, "allureFinal must not be null");
        Objects.requireNonNull(surefireIndex, "surefireIndex must not be null");
        Objects.requireNonNull(allureIndex, "allureIndex must not be null");
    }
}
