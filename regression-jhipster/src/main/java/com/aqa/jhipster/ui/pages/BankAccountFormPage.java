package com.aqa.jhipster.ui.pages;

import com.aqa.jhipster.ui.models.BankAccountBean;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static java.util.Objects.requireNonNull;

public class BankAccountFormPage extends BasePage {

    private static final String BANK_ACCOUNT_PATH = "/bank-account";

    private static final String NAME = "name";
    private static final String BALANCE = "balance";
    private static final String USER = "user";

    private static final String HEADING = "BankAccountCreateUpdateHeading";
    private static final String SAVE_BUTTON = "entityCreateSaveButton";

    private final Locator heading;
    private final Locator nameInput;
    private final Locator balanceInput;
    private final Locator userSelect;
    private final Locator saveButton;

    public BankAccountFormPage(final Page page) {
        super(page);

        heading = byDataCy(HEADING);
        nameInput = byDataCy(NAME);
        balanceInput = byDataCy(BALANCE);
        userSelect = byDataCy(USER);
        saveButton = byDataCy(SAVE_BUTTON);
    }

    @Override
    public BankAccountFormPage waitUntilLoaded() {
        assertUrlContains(BANK_ACCOUNT_PATH);
        assertThat(heading).isVisible();
        assertThat(nameInput).isVisible();
        assertThat(balanceInput).isVisible();
        assertThat(saveButton).isVisible();

        return this;
    }

    public BankAccountFormPage fillAccount(final BankAccountBean account, final List<String> headers) {
        requireNonNull(account, "Bank account must not be null");
        requireNonNull(headers, "Bank account headers must not be null");

        headers.forEach(header -> {
            switch (header) {
                case NAME -> nameInput.fill(requiredValue(NAME, account.getName()));

                case BALANCE -> balanceInput.fill(requiredValue(BALANCE, account.getBalance()).toPlainString());

                case USER ->
                        userSelect.selectOption(new SelectOption().setLabel(requiredValue(USER, account.getUser())));

                default -> throw new IllegalArgumentException("Unsupported bank account field: " + header);
            }
        });

        return this;
    }

    public BankAccountPage save() {
        saveButton.click();
        return new BankAccountPage(page).waitUntilLoaded();
    }

    private <T> T requiredValue(final String field, final T value) {
        return requireNonNull(value, "Missing value for bank account field: " + field);
    }
}