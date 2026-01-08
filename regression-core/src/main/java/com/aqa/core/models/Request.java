package com.aqa.core.models;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;

@Getter
public class Request {

    private final String method;
    private final String path;
    private final Map<String, ?> pathParams;
    private final Map<String, ?> queryParams;
    private final Map<String, String> headers;
    private final Object body;
    private final Integer statusCode;
    private final Map<String, ?> properties;

    public Request(final ApiRequestBuilder builder) {
        this.method = builder.method;
        this.path = builder.path;
        this.pathParams = builder.pathParams;
        this.queryParams = builder.queryParams;
        this.headers = builder.headers;
        this.body = builder.body;
        this.statusCode = builder.statusCode;
        this.properties = builder.properties;
    }

    public static ApiRequestBuilder request() {
        return new ApiRequestBuilder();
    }

    @Setter
    @Accessors(fluent = true)
    public static class ApiRequestBuilder {

        private String method;
        private String path;
        private Map<String, ?> pathParams;
        private Map<String, ?> queryParams;
        private Object body;
        private Map<String, String> headers = new HashMap<>();
        private Integer statusCode;
        private Map<String, ?> properties;

        public ApiRequestBuilder headers(Map<String, String> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }

        public Request build() {
            return new Request(this);
        }

    }
}
