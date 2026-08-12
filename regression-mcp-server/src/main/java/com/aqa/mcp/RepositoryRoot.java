package com.aqa.mcp;

import java.nio.file.Path;
import java.util.Objects;

record RepositoryRoot(Path path) {

    RepositoryRoot {
        Objects.requireNonNull(path, "path must not be null");
    }

    String displayPath() {
        return path.toString().replace('\\', '/');
    }
}
