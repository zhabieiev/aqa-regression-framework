package com.aqa.core.services;

import com.aqa.core.controllers.ClientController;
import com.aqa.core.convertors.DateConverter;
import com.aqa.core.models.Request;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

import static java.util.Optional.ofNullable;

public abstract class GeneralApiService {

    protected Client client;
    protected String baseUri;

    public GeneralApiService(final String baseUri) {
        this.client = ClientController.getClient();
        this.baseUri = baseUri;
    }

    public Response getResponse(final Request parameters) {
        final String startTime = DateConverter.currentDateToString();
        Response response = invokeResponse(parameters);
        ofNullable(parameters.getStatusCode()).ifPresent(code -> validateResponse(response, code, startTime));
        return response;
    }

    private void validateResponse(final Response response, final Integer statusCode, final String startTime) {
        if (response.getStatus() != statusCode) {
            throw new AssertionError(
                    String.format("%s\n%s\n->[%s]\n<-[%s]", response.readEntity(String.class),
                            String.format("Expected status code <%s> but was <%s>.", statusCode, response.getStatus()),
                            startTime, DateConverter.currentDateToString()));
        }
    }

    private Response invokeResponse(final Request parameters) {
        WebTarget target = new WebTargetWrapper(client.target(baseUri))
                .properties(parameters.getProperties())
                .path(parameters.getPath())
                .pathParams(parameters.getPathParams())
                .queryParams(parameters.getQueryParams())
                .webTarget();

        Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);

        if (parameters.getHeaders() != null && !parameters.getHeaders().isEmpty()) {
            parameters.getHeaders().forEach(builder::header);
        }

        return builder
                .build(parameters.getMethod(), Entity.json(parameters.getBody()))
                .invoke();
    }

    private static class WebTargetWrapper {

        WebTarget webTarget;

        public WebTargetWrapper(final WebTarget copy) {
            this.webTarget = copy;
        }

        private WebTargetWrapper properties(final Map<String, ?> properties) {
            ofNullable(properties).ifPresent(props -> {
                props.forEach((k, v) -> webTarget.property(k, v));
            });
            return this;
        }

        private WebTargetWrapper path(final String path) {
            ofNullable(path).ifPresent(u -> webTarget = webTarget.path(path));
            return this;
        }

        private WebTargetWrapper pathParams(final Map<String, ?> pathParams) {
            ofNullable(pathParams).ifPresent(params ->
                    params.entrySet().forEach(param -> webTarget = webTarget.resolveTemplate(param.getKey(), param.getValue())));
            return this;
        }

        private WebTargetWrapper queryParams(final Map<String, ?> queryParams) {
            ofNullable(queryParams).ifPresent(params ->
                    params.entrySet().forEach(param -> {
                        if (param.getValue() instanceof List) {
                            webTarget = webTarget.queryParam(param.getKey(), ((List) param.getValue()).toArray());
                        } else {
                            webTarget = webTarget.queryParam(param.getKey(), param.getValue());
                        }
                    })
            );
            return this;
        }

        private WebTarget webTarget() {
            return webTarget;
        }
    }
}
