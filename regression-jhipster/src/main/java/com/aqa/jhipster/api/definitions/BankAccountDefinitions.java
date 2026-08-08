package com.aqa.jhipster.api.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.jhipster.api.models.generated.BankAccount;
import com.aqa.jhipster.api.steps.BankAccountSteps;
import io.cucumber.java.en.Given;

import java.util.Map;

import static com.aqa.core.Populator.populate;
import static com.aqa.core.convertors.MapConvertor.convertMapKeysWithPrefix;
import static com.aqa.core.enumerations.RequestParams.STATUS_CODE;
import static com.aqa.core.enumerations.RequestPrefixes.PATH;
import static com.aqa.core.enumerations.RequestPrefixes.RESPONSE;
import static java.util.Set.of;

public record BankAccountDefinitions(VariablesController variablesController, BankAccountSteps bankAccountSteps) {

    @Given("api user creates new bank account and saves to {string}:")
    public void apiUserCreatesNewBankAccountAndSavesTo(String var, Map<String, String> map) {
        variablesController.setVar(var, bankAccountSteps.deleteAndCreate(populate(map, BankAccount.class)));
    }

    @Given("api user gets bank account and saves to {string}:")
    public void apiUserGetBankAccountAndSavesTo(String var, Map<String, String> map) {
        variablesController.setVar(var, bankAccountSteps.getBankAccount(populate(map, BankAccount.class).getId()));
    }

    @Given("api user tries to get bank account and saves to {string}:")
    public void apiUserTriesToGetBankAccountAndSavesTo(String var, Map<String, String> map) {
        variablesController.setVar(var, bankAccountSteps.getBankAccount(
                populate(convertMapKeysWithPrefix(map, of(PATH.getValue())), BankAccount.class).getId(),
                Integer.parseInt(map.get(RESPONSE.getValue() + STATUS_CODE.getValue()))));
    }

    @Given("api user deletes bank account with id:")
    public void apiUserDeletesBankAccount(Map<String, String> map) {
        bankAccountSteps.delete(
                populate(convertMapKeysWithPrefix(map, of(PATH.getValue())), BankAccount.class).getId());
    }

    @Given("api user deletes bank account by name:")
    public void apiUserDeletesBankAccountByName(Map<String, String> map) {
        bankAccountSteps.deleteByName(
                populate(convertMapKeysWithPrefix(map, of(PATH.getValue())), BankAccount.class).getName());
    }
}