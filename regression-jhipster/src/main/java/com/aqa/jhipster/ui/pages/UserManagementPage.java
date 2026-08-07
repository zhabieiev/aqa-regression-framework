package com.aqa.jhipster.ui.pages;

import com.aqa.jhipster.ui.components.DataTableComponent;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class UserManagementPage extends BasePage {

    private static final String PATH = "/admin/user-management";

    private static final String HEADING = "userManagementPageHeading";
    private static final String CREATE_BUTTON = "entityCreateButton";

    private final Locator heading;
    private final Locator createButton;
    private final DataTableComponent usersTable;

    public UserManagementPage(final Page page) {
        super(page);
        heading = byDataCy(HEADING);
        createButton = byDataCy(CREATE_BUTTON);
        usersTable = new DataTableComponent(page, page.getByRole(AriaRole.TABLE));
    }

    public UserManagementPage open() {
        navigateTo(PATH);
        return this;
    }

    @Override
    public UserManagementPage waitUntilLoaded() {
        assertUrlContains(PATH);
        assertThat(heading).isVisible();
        assertThat(createButton).isVisible();
        usersTable.waitUntilDisplayed();
        return this;
    }

    public UserManagementPage assertUserDisplayed(final String login) {
        final Locator row = uniqueRowByLogin(login);
        assertThat(row).isVisible();
        assertThat(row).containsText(login);
        return this;
    }

    public UserManagementPage assertUserNotDisplayed(final String login) {
        usersTable.assertRowNotDisplayed(login);
        return this;
    }

    public boolean isUserDisplayed(final String login) {
        return rowByLogin(login).count() > 0;
    }

    public int userCount() {
        return Math.max(usersTable.rowCount() - 1, 0);
    }

    private Locator uniqueRowByLogin(final String login) {
        final Locator row = rowByLogin(login);
        assertThat(row).hasCount(1);
        return row;
    }

    private Locator rowByLogin(final String login) {
        return usersTable.rowContaining(login);
    }
}