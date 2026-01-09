package com.aqa.core.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.ext.ContextResolver;

import static com.aqa.core.utils.FileParseUtils.writeJson;
import static org.glassfish.jersey.client.ClientProperties.SUPPRESS_HTTP_COMPLIANCE_VALIDATION;

public class ClientController {

    private final static ContextResolver<ObjectMapper> contextResolver = new ContextResolver<ObjectMapper>() {
        @Override
        public ObjectMapper getContext(Class<?> type) {
            return writeJson();
        }
    };

    public static Client getClient() {
        return ClientBuilder.newBuilder()
                .property(SUPPRESS_HTTP_COMPLIANCE_VALIDATION, true)
                .register(contextResolver)
                .build();
    }
}
