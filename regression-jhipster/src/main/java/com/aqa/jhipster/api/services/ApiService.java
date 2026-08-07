package com.aqa.jhipster.api.services;

import com.aqa.core.services.GeneralApiService;
import jakarta.ws.rs.core.UriBuilder;

import static com.aqa.core.enumerations.Property.URL_API;

public abstract class ApiService extends GeneralApiService {

    protected ApiService() {
        super(UriBuilder.fromUri(URL_API.read()).toTemplate());
    }
}
