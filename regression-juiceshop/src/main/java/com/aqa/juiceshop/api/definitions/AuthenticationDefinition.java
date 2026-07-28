package com.aqa.juiceshop.api.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.juiceshop.api.models.generated.LoginRequest;
import com.aqa.juiceshop.api.steps.AuthenticationStep;
import io.cucumber.java.en.When;

import java.util.Map;

import static com.aqa.core.Populator.populate;

public record AuthenticationDefinition(VariablesController variablesController, AuthenticationStep authenticationStep) {

    @When("api user authenticates and saves response to {string}:")
    public void apiUserAuthenticatesAndSavesResponseTo(String var, Map<String, String> map) {
        LoginRequest request = populate(map, LoginRequest.class);
        variablesController.setVar(var, authenticationStep().authenticationService().authenticate(request));
    }
}
