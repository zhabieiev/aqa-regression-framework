package com.aqa.toolshop.api.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.toolshop.api.models.generated.AccountRequest;
import com.aqa.toolshop.api.models.generated.UserResponse;
import com.aqa.toolshop.api.steps.AuthSteps;
import io.cucumber.java.en.When;

import java.util.List;
import java.util.Map;

import static com.aqa.core.Populator.populate;

public record AuthDefinitions(VariablesController variablesController, AuthSteps authSteps) {

    @When("api user gets access token and saves to {string}:")
    public void apiUserGetsAccessTokenAndSavesTo(String var, Map<String, String> map) {
        AccountRequest body = populate(map, AccountRequest.class);
        variablesController.setVar(var, authSteps.authService().login(body));
    }

    @When("api user search user and saves to {string}:")
    public void apiUserSearchUsers(String var, Map<String, String> map) {
        variablesController.setVar(var, authSteps.searchUsers(map));
    }
}
