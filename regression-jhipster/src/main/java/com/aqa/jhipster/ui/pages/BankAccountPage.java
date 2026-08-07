package com.aqa.jhipster.ui.pages;

import com.aqa.jhipster.ui.components.DataTableComponent;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.math.BigDecimal;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class BankAccountPage extends BasePage {

    private static final String PATH = "/bank-account";

    private static final String HEADING = "BankAccountHeading";
    private static final String CREATE_BUTTON = "entityCreateButton";
    private static final String DELETE_BUTTON = "entityDeleteButton";
    private static final String CONFIRM_DELETE_BUTTON = "entityConfirmDeleteButton";

    private final Locator heading;
    private final Locator createButton;
    private final DataTableComponent accountsTable;

    public BankAccountPage(final Page page) {
        super(page);

        heading = byDataCy(HEADING);
        createButton = byDataCy(CREATE_BUTTON);

        accountsTable = new DataTableComponent(page, page.getByRole(AriaRole.TABLE));
    }

    public BankAccountPage open() {
        navigateTo(PATH);
        return this;
    }

    @Override
    public BankAccountPage waitUntilLoaded() {
        assertUrlContains(PATH);
        assertThat(heading).isVisible();
        assertThat(createButton).isVisible();

        return this;
    }

    public BankAccountFormPage openCreateForm() {
        createButton.click();

        return new BankAccountFormPage(page).waitUntilLoaded();
    }

    public BankAccountPage assertAccountDisplayed(final String name, final BigDecimal balance) {
        accountsTable.waitUntilDisplayed();

        final Locator row = uniqueRowByName(name);

        assertThat(row).isVisible();
        assertThat(row).containsText(balance.toPlainString());

        return this;
    }

    public BankAccountPage deleteAccount(final String name) {
        accountsTable.waitUntilDisplayed();

        uniqueRowByName(name).getByTestId(DELETE_BUTTON).click();

        final Locator confirmDeleteButton = byDataCy(CONFIRM_DELETE_BUTTON);

        assertThat(confirmDeleteButton).isVisible();
        confirmDeleteButton.click();

        accountsTable.assertRowNotDisplayed(name);

        return this;
    }

    public BankAccountPage assertAccountNotDisplayed(final String name) {
        accountsTable.assertRowNotDisplayed(name);
        return this;
    }

    private Locator uniqueRowByName(final String name) {
        final Locator row = rowByName(name);

        assertThat(row).hasCount(1);
        return row;
    }

    private Locator rowByName(final String name) {
        return accountsTable.rowContaining(name);
    }
}