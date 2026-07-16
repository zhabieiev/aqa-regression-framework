package com.aqa.petstore.api.services;

import com.aqa.petstore.api.models.generated.Order;

import static com.aqa.core.models.Request.request;
import static java.net.HttpURLConnection.HTTP_OK;

public class StoreService extends StoreApiService {

    final private static String STORE = "/store";
    final private static String STORE_ORDER = STORE + "/order";

    public Order createOrder(final Order body) {
        return getResponse(request().method("POST")
                .path(STORE_ORDER)
                .body(body)
                .statusCode(HTTP_OK)
                .build()).readEntity(Order.class);
    }
}
