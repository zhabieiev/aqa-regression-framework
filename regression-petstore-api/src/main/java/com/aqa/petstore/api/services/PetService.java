package com.aqa.petstore.api.services;

import com.aqa.petstore.api.models.generated.ModelApiResponse;
import com.aqa.petstore.api.models.generated.Pet;

import static com.aqa.core.models.Request.request;
import static jakarta.ws.rs.HttpMethod.DELETE;
import static jakarta.ws.rs.HttpMethod.POST;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_OK;

public class PetService extends PetStoreApiService {

    private static final String PETS = "/pet";
    private static final String PET_ID = PETS + "/%s";

    public Pet create(final Pet body) {
        return getResponse(request().method(POST)
                .path(PETS)
                .body(body)
                .statusCode(HTTP_OK)
                .build()).readEntity(Pet.class);
    }

    public ModelApiResponse delete(final Long id) {
        return getResponse(request().method(DELETE)
                .path(format(PET_ID, id))
                .statusCode(HTTP_OK)
                .build()).readEntity(ModelApiResponse.class);
    }
}