package com.aqa.jhipster.ui.definitions;

import com.aqa.jhipster.ui.models.BankAccountBean;
import com.aqa.jhipster.ui.steps.BankAccountSteps;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import static com.aqa.core.convertors.DataTableConverter.convertToSingle;
import static com.aqa.core.convertors.DataTableConverter.getHeaders;

public record BankAccountDefinitions(BankAccountSteps bankAccountSteps) {

    @When("ui user creates a bank account:")
    public void userCreatesBankAccount(final DataTable table) {
        bankAccountSteps.createBankAccount(convertToSingle(table, BankAccountBean.class), getHeaders(table));
    }

    @Then("ui bank account is displayed:")
    public void bankAccountIsDisplayed(final DataTable table) {
        bankAccountSteps.assertBankAccountDisplayed(convertToSingle(table, BankAccountBean.class));
    }

    @When("ui user deletes bank account {string}")
    public void userDeletesBankAccount(final String name) {
        bankAccountSteps.deleteBankAccount(name);
    }

    @Then("ui bank account {string} is not displayed")
    public void bankAccountIsNotDisplayed(final String name) {
        bankAccountSteps.assertBankAccountNotDisplayed(name);
    }

    @Given("ui user opens the bank accounts page")
    public void userOpensBankAccountsPage() {
        bankAccountSteps.openBankAccountsPage();
    }
}