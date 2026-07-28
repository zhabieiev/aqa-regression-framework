package com.aqa.juiceshop.api.services;

import com.aqa.core.services.GeneralApiService;
import jakarta.ws.rs.core.UriBuilder;

import static com.aqa.juiceshop.api.enumeration.Property.URL_API;

public abstract class ApiServices extends GeneralApiService {

    protected ApiServices() {
        super(UriBuilder.fromUri(URL_API.read()).toTemplate());
    }
}
