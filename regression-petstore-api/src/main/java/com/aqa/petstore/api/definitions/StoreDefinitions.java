package com.aqa.petstore.api.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.petstore.api.models.generated.Order;
import com.aqa.petstore.api.steps.StoreSteps;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;

import java.util.Map;

import static com.aqa.core.Populator.populate;
import static com.aqa.core.enumerations.RequestParams.STATUS_CODE;
import static com.aqa.core.enumerations.RequestPrefixes.RESPONSE;

public record StoreDefinitions(VariablesController variablesController, StoreSteps storeSteps) {

    @Given("api user creates store order and saves to {string}:")
    public void createOrder(String var, Map<String, String> map) {
        variablesController.setVar(var, storeSteps().storeService().create(populate(map, Order.class)));
    }

    @When("api user gets {convertString} store order and saves to {string}")
    public void getOrder(String orderId, String var) {
        variablesController.setVar(var, storeSteps().storeService().read(orderId));
    }

    @When("api user tries to get {convertString} store order and saves to {string}:")
    public void tryToGetOrder(String orderId, String var, Map<String, String> map) {
        variablesController.setVar(var, storeSteps().storeService()
                .read(orderId, Integer.parseInt(map.get(RESPONSE.getValue() + STATUS_CODE.getValue()))));
    }

    @Given("api user deletes {convertString} store order and saves to {string}")
    public void deleteOrder(String orderId, String var) {
        variablesController.setVar(var, storeSteps().storeService().delete(orderId));
    }
}
