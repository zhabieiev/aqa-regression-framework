package com.aqa.core.enumerations;

import lombok.Getter;

@Getter
public enum S3RequestPrefixes {

    META("meta:"),
    DATA("data:");

    private final String value;

    S3RequestPrefixes(String value) {
        this.value = value;
    }
}