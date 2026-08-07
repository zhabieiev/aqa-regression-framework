package com.aqa.jhipster.ui.pages;

import com.aqa.jhipster.ui.components.NavigationBar;
import com.microsoft.playwright.Page;

public class HomePage extends BasePage {

    private static final String PATH = "/";
    private static final String LOGIN_PATH = "/login";

    private final NavigationBar navigationBar;

    public HomePage(final Page page) {
        super(page);
        navigationBar = new NavigationBar(page);
    }

    public HomePage open() {
        navigateTo(PATH);
        return this;
    }

    @Override
    public HomePage waitUntilLoaded() {
        navigationBar.waitUntilDisplayed();
        return this;
    }

    public HomePage waitUntilAuthenticated() {
        page.waitForURL(url -> !url.contains(LOGIN_PATH));
        navigationBar.assertAuthenticated();
        return this;
    }

    public LoginPage logout() {
        navigationBar.logout();
        return new LoginPage(page).waitUntilLoaded();
    }

    public UserManagementPage openUserManagement() {
        navigationBar.openUserManagement();
        return new UserManagementPage(page).waitUntilLoaded();
    }

    public BankAccountPage openBankAccounts() {
        navigationBar.openBankAccounts();
        return new BankAccountPage(page).waitUntilLoaded();
    }
}