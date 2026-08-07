package com.aqa.jhipster.api.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.jhipster.api.models.generated.BankAccount;
import com.aqa.jhipster.api.steps.BankAccountSteps;
import io.cucumber.java.en.Given;

import java.util.Map;

import static com.aqa.core.Populator.populate;

public record BankAccountDefinitions(VariablesController variablesController, BankAccountSteps bankAccountSteps) {

    @Given("api user creates new bank account and saves to {string}:")
    public void apiUserCreatesNewBankAccountAndSavesTo(String var, Map<String, String> map) {
        variablesController.setVar(var, bankAccountSteps.deleteAndCreate(populate(map, BankAccount.class)));
    }
}
