package com.aqa.juiceshop.api.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.core.enumerations.RequestPrefixes;
import com.aqa.juiceshop.api.models.generated.BasketItemCreateRequest;
import com.aqa.juiceshop.api.steps.BasketStep;
import io.cucumber.java.en.When;

import java.util.Map;

import static com.aqa.core.Populator.populate;
import static com.aqa.core.convertors.MapConvertor.convertMapKeysWithPrefix;
import static java.util.Set.of;

public record BasketDefinition(VariablesController variablesController, BasketStep basketStep) {

    @When("api user creates new basket and saves response to {string}:")
    public void apiUserCreatesNewBasketAndSavesResponseTo(String var, Map<String, String> map) {
        variablesController.setVar(var, basketStep.basketService()
                .createBasket(convertMapKeysWithPrefix(map, of(RequestPrefixes.HEADERS.getValue())),
                        populate(convertMapKeysWithPrefix(map, of(RequestPrefixes.BODY.getValue())),
                        BasketItemCreateRequest.class)));
    }

    @When("api user gets own basket and saves response to {string}:")
    public void apiUserGetsOwnBasket(String var, String basketId) {
        variablesController.setVar(var, basketStep.basketService().getBasket((basketId)));
    }
}
