package com.anyclip.core.enumerations;

import lombok.Getter;

@Getter
public enum RequestParams {

    STATUS_CODE("statusCode"),
    ID("id");

    private final String value;

    RequestParams(final String value) {
        this.value = value;
    }
}
