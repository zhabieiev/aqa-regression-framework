package com.aqa.jhipster.ui.steps;

import com.aqa.jhipster.ui.context.UiScenarioContext;
import com.aqa.jhipster.ui.models.BankAccountBean;
import com.aqa.jhipster.ui.pages.BankAccountPage;

import java.util.List;

public class BankAccountSteps {

    private final UiScenarioContext scenarioContext;

    private BankAccountPage bankAccountPage;

    public BankAccountSteps(final UiScenarioContext scenarioContext) {
        this.scenarioContext = scenarioContext;
    }

    public void openBankAccountsPage() {
        bankAccountPage = new BankAccountPage(scenarioContext.page()).open();
    }

    public void createBankAccount(final BankAccountBean account, final List<String> headers) {
        bankAccountPage = currentBankAccountPage().openCreateForm().fillAccount(account, headers).save();
    }

    public void assertBankAccountDisplayed(final BankAccountBean account) {
        currentBankAccountPage().assertAccountDisplayed(account.getName(), account.getBalance());
    }

    public void deleteBankAccount(final String name) {
        currentBankAccountPage().deleteAccount(name);
    }

    public void assertBankAccountNotDisplayed(final String name) {
        currentBankAccountPage().assertAccountNotDisplayed(name);
    }

    private BankAccountPage currentBankAccountPage() {
        if (bankAccountPage == null) {
            throw new IllegalStateException("Bank account page has not been opened");
        }

        return bankAccountPage;
    }
}