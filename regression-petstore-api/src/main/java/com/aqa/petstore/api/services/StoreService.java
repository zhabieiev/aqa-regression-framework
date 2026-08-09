package com.aqa.petstore.api.services;

import com.aqa.petstore.api.models.generated.ModelApiResponse;
import com.aqa.petstore.api.models.generated.Order;

import static com.aqa.core.models.Request.request;
import static jakarta.ws.rs.HttpMethod.DELETE;
import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.POST;
import static java.lang.String.format;
import static java.net.HttpURLConnection.HTTP_OK;

public class StoreService extends PetStoreApiService {

    private static final String STORE_ORDERS = "/store/order";
    private static final String STORE_ORDER_ID = STORE_ORDERS + "/%s";

    public Order create(final Order body) {
        return getResponse(request().method(POST)
                .path(STORE_ORDERS)
                .body(body)
                .statusCode(HTTP_OK)
                .build()).readEntity(Order.class);
    }

    public Order get(final Long orderId) {
        return getResponse(request().method(GET)
                .path(format(STORE_ORDER_ID, orderId))
                .statusCode(HTTP_OK)
                .build()).readEntity(Order.class);
    }

    public ModelApiResponse get(final Long orderId, final int statusCode) {
        return getResponse(request().method(GET)
                .path(format(STORE_ORDER_ID, orderId))
                .statusCode(statusCode)
                .build()).readEntity(ModelApiResponse.class);
    }

    public ModelApiResponse delete(final Long orderId) {
        return getResponse(request().method(DELETE)
                .path(format(STORE_ORDER_ID, orderId))
                .statusCode(HTTP_OK)
                .build()).readEntity(ModelApiResponse.class);
    }
}