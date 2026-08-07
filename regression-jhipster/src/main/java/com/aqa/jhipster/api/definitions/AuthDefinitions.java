package com.aqa.jhipster.api.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.jhipster.api.models.generated.LoginVM;
import com.aqa.jhipster.api.steps.AuthSteps;
import io.cucumber.java.en.Given;

import java.util.Map;

import static com.aqa.core.Populator.populate;
import static com.aqa.core.convertors.MapConvertor.convertMapKeysWithPrefix;
import static com.aqa.core.enumerations.RequestParams.STATUS_CODE;
import static com.aqa.core.enumerations.RequestPrefixes.BODY;
import static com.aqa.core.enumerations.RequestPrefixes.RESPONSE;
import static java.util.Set.of;

public record AuthDefinitions(VariablesController variablesController, AuthSteps authSteps) {

    @Given("api user authenticates as admin and saves headers to {string}")
    public void apiAdminAuthenticatesAndSavesHeaders(String var) {
        variablesController.setVar(var, authSteps.getAdminHeaders());
    }

    @Given("api user authenticates and saves headers to {string}")
    public void apiUserAuthenticatesAndSavesHeaders(String var) {
        variablesController.setVar(var, authSteps.getUserHeaders());
    }

    @Given("api user authenticates with credentials and saves headers to {string}:")
    public void apiUserAuthenticatesWithCredentialsAndSavesHeaders(String var, Map<String, String> credentials) {
        variablesController.setVar(var, authSteps.getHeaders(credentials));
    }

    @Given("api user authenticates with credentials and saves token to {string}:")
    public void apiUserAuthenticatesWithCredentialsAndSavesToken(String var, Map<String, String> credentials) {
        variablesController.setVar(var, authSteps.authService().login(populate(credentials, LoginVM.class)));
    }

    @Given("api user tries to authenticate with invalid credentials and saves to {string}:")
    public void apiUserTriesToAuthenticatesWithAdminCredentialsAndSavesToken(final String var,
                                                                           final Map<String, String> credentials) {
        variablesController.setVar(var, authSteps.authService()
                .login(populate(convertMapKeysWithPrefix(credentials, of(BODY.getValue())), LoginVM.class),
                        Integer.parseInt(credentials.get(RESPONSE.getValue() + STATUS_CODE.getValue()))));
    }
}