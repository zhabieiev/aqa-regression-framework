package com.aqa.jhipster.ui.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class NavigationBar extends BaseComponent {

    private static final String ACCOUNT_MENU = "accountMenu";
    private static final String SETTINGS_BUTTON = "settings";
    private static final String LOGOUT_BUTTON = "logout";
    private static final String ADMINISTRATION_MENU = "adminMenu";
    private static final String USER_MANAGEMENT_BUTTON = "userManagement";
    private static final String ENTITIES_MENU = "entity";
    private static final String BANK_ACCOUNT_BUTTON = "bankAccount";

    private final Locator accountMenu;
    private final Locator settingsButton;
    private final Locator logoutButton;
    private final Locator administrationMenu;
    private final Locator userManagementButton;
    private final Locator entitiesMenu;
    private final Locator bankAccountButton;

    public NavigationBar(final Page page) {
        super(page, page.getByTestId(ACCOUNT_MENU));
        accountMenu = root;
        settingsButton = byDataCy(SETTINGS_BUTTON);
        logoutButton = byDataCy(LOGOUT_BUTTON);
        administrationMenu = byDataCy(ADMINISTRATION_MENU);
        userManagementButton = byDataCy(USER_MANAGEMENT_BUTTON);
        entitiesMenu = byDataCy(ENTITIES_MENU);
        bankAccountButton = byDataCy(BANK_ACCOUNT_BUTTON);
    }

    public void openAccountSettings() {
        accountMenu.click();
        settingsButton.click();
    }

    public void logout() {
        accountMenu.click();
        logoutButton.click();
    }

    public void openUserManagement() {
        administrationMenu.click();
        userManagementButton.click();
    }

    public void openBankAccounts() {
        entitiesMenu.click();
        bankAccountButton.click();
    }

    public void assertAuthenticated() {
        accountMenu.click();
        assertThat(logoutButton).isVisible();
        page.keyboard().press("Escape");
    }
}