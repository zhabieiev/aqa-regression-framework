package com.aqa.petstore.api.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.petstore.api.models.generated.Order;
import com.aqa.petstore.api.steps.StoreSteps;
import io.cucumber.java.en.Given;

import java.util.Map;

import static com.aqa.core.Populator.populate;

public record StoreDefinitions(VariablesController  variablesController, StoreSteps storeSteps) {

    @Given("api user creates store order and saves to {string}:")
    public void createOrder(String var, Map<String, String> map) {
        variablesController.setVar(var, storeSteps().storeService().createOrder(populate(map, Order.class)));
    }
}
