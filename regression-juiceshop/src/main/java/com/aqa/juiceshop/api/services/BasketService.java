package com.aqa.juiceshop.api.services;

import com.aqa.juiceshop.api.models.generated.BasketItemCreateRequest;
import com.aqa.juiceshop.api.models.generated.BasketItemResponse;
import com.aqa.juiceshop.api.models.generated.BasketResponse;

import java.util.Map;

import static com.aqa.core.models.Request.request;
import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.POST;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_OK;

public final class BasketService extends ApiServices {

    private static final String REST_BASKET_ID = "/rest/basket/%s";
    private static final String BASKET_ITEMS = "/api/BasketItems";

    public BasketResponse getBasket(final String basketId) {
        return getResponse(
                request().method(GET).path(format(REST_BASKET_ID, basketId)).statusCode(HTTP_OK).build()).readEntity(
                BasketResponse.class);
    }

    public BasketItemResponse createBasket(final Map<String, String> headers, final BasketItemCreateRequest body) {
        return getResponse(request()
                .method(POST)
                .path(BASKET_ITEMS)
                .headers(headers)
                .body(body)
                .statusCode(HTTP_OK)
                .build()).readEntity(BasketItemResponse.class);
    }
}
