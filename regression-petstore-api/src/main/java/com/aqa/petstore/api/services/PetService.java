package com.aqa.petstore.api.services;

import com.aqa.petstore.api.models.generated.Pet;

import static com.aqa.core.models.Request.request;
import static jakarta.ws.rs.HttpMethod.POST;
import static java.net.HttpURLConnection.HTTP_OK;

public class PetService extends StoreApiService{

    final private static String PET = "/pet";

    public Pet create(final Pet body) {
        return getResponse(request().method(POST)
                .path(PET)
                .body(body)
                .statusCode(HTTP_OK)
                .build()).readEntity(Pet.class);
    }
}
