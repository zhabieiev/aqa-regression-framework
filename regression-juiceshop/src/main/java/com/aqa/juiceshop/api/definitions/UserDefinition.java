package com.aqa.juiceshop.api.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.juiceshop.api.models.generated.UserRegistrationRequest;
import com.aqa.juiceshop.api.steps.UserStep;
import io.cucumber.java.en.When;

import java.util.Map;

import static com.aqa.core.Populator.populate;

public record UserDefinition (VariablesController variablesController, UserStep userStep) {

    @When("api user creates new user and saves to {string}:")
    public void apiUserCreatesUserAndSavesTo(String var, Map<String, String> map) {
        UserRegistrationRequest body = populate(map, UserRegistrationRequest.class);
        variablesController.setVar(var, userStep().userService().create(body));
    }
}
