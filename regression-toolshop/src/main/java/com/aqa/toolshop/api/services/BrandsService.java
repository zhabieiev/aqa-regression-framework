package com.aqa.toolshop.api.services;

import com.aqa.toolshop.api.models.generated.BrandRequest;
import com.aqa.toolshop.api.models.generated.BrandResponse;
import jakarta.ws.rs.core.GenericType;


import java.util.List;
import java.util.Map;

import static com.aqa.core.models.Request.request;
import static jakarta.ws.rs.HttpMethod.*;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_OK;

public class BrandsService extends ApiService {

    private static final String BRANDS = "/brands";
    private static final String BRANDS_ID = BRANDS + "/%s";
    private static final String BRANDS_SEARCH = BRANDS + "/search";

    public BrandResponse create(BrandRequest body) {
        return getResponse(request().method(POST).path(BRANDS).body(body).build()).readEntity(BrandResponse.class);
    }

    public List<BrandResponse> search(final String name) {
        return getResponse(request()
                .method(GET)
                .path(BRANDS_SEARCH)
                .queryParams(Map.of("q", name))
                .statusCode(HTTP_OK).build()).readEntity(new GenericType<>() {});
    }

    public void delete(final String id, final Map<String, String> headers) {
        getResponse(request()
                .method(DELETE)
                .path(format(BRANDS_ID, id))
                .headers(headers)
                .build());
    }
}
