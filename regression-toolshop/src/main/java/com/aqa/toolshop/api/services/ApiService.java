package com.aqa.toolshop.api.services;

import com.aqa.core.services.GeneralApiService;
import jakarta.ws.rs.core.UriBuilder;

import static com.aqa.toolshop.api.enumeration.Property.URL_API;

public abstract class ApiService extends GeneralApiService {

    protected ApiService() {
        super(UriBuilder.fromUri(URL_API.read()).toTemplate());
    }
}
