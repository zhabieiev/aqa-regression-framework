package com.aqa.petstore.api.services;

import com.aqa.core.services.GeneralApiService;
import jakarta.ws.rs.core.UriBuilder;

import static com.aqa.core.enumerations.Property.URL_API;
import static com.aqa.core.enumerations.Property.V2;

public class StoreApiService extends GeneralApiService {

    public StoreApiService() {
        super(UriBuilder.fromUri(URL_API.read()).path(V2.read()).toTemplate());
    }
}
