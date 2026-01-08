package com.anyclip.core.controllers;


import com.fasterxml.jackson.databind.ObjectMapper;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.ext.ContextResolver;

import static com.anyclip.core.utils.FileParseUtils.writeJson;
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
