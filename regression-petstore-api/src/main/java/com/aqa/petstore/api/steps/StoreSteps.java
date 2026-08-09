package com.aqa.petstore.api.steps;

import com.aqa.petstore.api.models.generated.ModelApiResponse;
import com.aqa.petstore.api.models.generated.Order;
import com.aqa.petstore.api.services.StoreService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record StoreSteps(StoreService storeService) {

    public Order create(final Order body) {
        final Order response = storeService.create(body);

        log.info("Store order with id {} is created", response.getId());

        return response;
    }

    public Order get(final Long orderId) {
        return storeService.get(orderId);
    }

    public ModelApiResponse get(final Long orderId, final int statusCode) {
        return storeService.get(orderId, statusCode);
    }

    public ModelApiResponse delete(final Long orderId) {
        final ModelApiResponse response = storeService.delete(orderId);

        log.info("Store order with id {} is deleted", orderId);

        return response;
    }
}