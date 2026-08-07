package com.aqa.jhipster.api.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.jhipster.api.models.generated.AdminUserDTO;
import com.aqa.jhipster.api.models.generated.LoginVM;
import com.aqa.jhipster.api.steps.UserSteps;
import io.cucumber.java.en.Given;

import java.util.Map;

import static com.aqa.core.Populator.populate;
import static com.aqa.core.convertors.MapConvertor.convertMapKeysWithPrefix;
import static com.aqa.core.enumerations.RequestParams.STATUS_CODE;
import static com.aqa.core.enumerations.RequestPrefixes.*;
import static java.util.Set.of;

public record UserDefinitions(VariablesController variablesController, UserSteps userSteps) {

    @Given("api user creates new user and saves to {string}:")
    public void apiUserCreatesNewUserAndSavesToUser(String var, Map<String, String> map) {
        variablesController.setVar(var, userSteps.deleteAndCreate(populate(map, AdminUserDTO.class)));
    }

    @Given("api user gets user and saves to {string}:")
    public void apiUserGetUserAndSavesToUser(String var, Map<String, String> map) {
        variablesController.setVar(var, userSteps.userService()
                .get(populate(map, AdminUserDTO.class).getLogin(), userSteps.authService().getAdminHeaders()));
    }

    @Given("api user tries to get user and saves to {string}:")
    public void apiUserTriesToGetUserAndSavesToUser(String var, Map<String, String> map) {
        variablesController.setVar(var, userSteps.userService()
                .get(populate(convertMapKeysWithPrefix(map, of(PATH.getValue())), AdminUserDTO.class).getLogin(),
                        userSteps.authService().getAdminHeaders(),
                        Integer.parseInt(map.get(RESPONSE.getValue() + STATUS_CODE.getValue()))));
    }
}
