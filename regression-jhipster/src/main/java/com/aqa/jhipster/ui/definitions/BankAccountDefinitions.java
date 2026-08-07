package com.aqa.jhipster.ui.definitions;

import com.aqa.jhipster.ui.models.BankAccountBean;
import com.aqa.jhipster.ui.steps.BankAccountSteps;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.util.List;

import static com.aqa.core.Populator.populateList;

public class BankAccountDefinitions {

    private final BankAccountSteps bankAccountSteps;

    public BankAccountDefinitions(final BankAccountSteps bankAccountSteps) {
        this.bankAccountSteps = bankAccountSteps;
    }

    @When("ui user creates a bank account:")
    public void userCreatesBankAccount(final DataTable table) {
        final List<BankAccountBean> accounts =
                populateList(table.asMaps(String.class, String.class), BankAccountBean.class);
        final BankAccountBean account = requireSingleAccount(accounts);
        final List<String> headers = table.asLists(String.class).getFirst();
        bankAccountSteps.createBankAccount(account, headers);
    }

    @Then("ui bank account is displayed:")
    public void bankAccountIsDisplayed(final DataTable table) {
        final List<BankAccountBean> accounts =
                populateList(table.asMaps(String.class, String.class), BankAccountBean.class);

        bankAccountSteps.assertBankAccountDisplayed(requireSingleAccount(accounts));
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

    private BankAccountBean requireSingleAccount(final List<BankAccountBean> accounts) {
        if (accounts.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one bank account, but found: " + accounts.size());
        }
        return accounts.getFirst();
    }
}