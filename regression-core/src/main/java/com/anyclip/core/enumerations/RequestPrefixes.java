package com.anyclip.core.enumerations;

import lombok.Getter;

@Getter
public enum RequestPrefixes {

    QUERY("query:"),
    PATH("path:"),
    BODY("body:"),
    HEADERS("headers:"),
    RESPONSE("response:");

    private final String value;

    RequestPrefixes(String value) {
        this.value = value;
    }
}
