package com.aqa.petstore.api.services;

import com.aqa.petstore.api.models.generated.Order;

import static com.aqa.core.models.Request.request;
import static java.lang.String.format;
import static jakarta.ws.rs.HttpMethod.*;
import static java.net.HttpURLConnection.HTTP_OK;

public class StoreService extends StoreApiService {

    final private static String STORE = "/store";
    final private static String STORE_ORDER = STORE + "/order";
    final private static String STORE_ORDER_ID = STORE_ORDER + "/%s";

    public Order create(final Order body) {
        return getResponse(request().method(POST)
                .path(STORE_ORDER)
                .body(body)
                .statusCode(HTTP_OK)
                .build()).readEntity(Order.class);
    }

    public Order read(final String orderId) {
        return getResponse(request().method(GET)
                .path(format(STORE_ORDER_ID, orderId))
                .statusCode(HTTP_OK)
                .build()).readEntity(Order.class);
    }

    public String read(final String orderId, int statusCode) {
        return getResponse(request().method(GET)
                .path(format(STORE_ORDER_ID, orderId))
                .statusCode(statusCode)
                .build()).readEntity(String.class);
    }

    public String delete(final String orderId) {
        return getResponse(request().method(DELETE)
                .path(format(STORE_ORDER_ID, orderId))
                .statusCode(HTTP_OK)
                .build()).readEntity(String.class);
    }
}
