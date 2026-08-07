package com.aqa.jhipster.ui.pages;

import com.aqa.jhipster.ui.components.NavigationBar;
import com.microsoft.playwright.Page;

public class HomePage extends BasePage {

    private static final String LOGIN_PATH = "/login";

    private final NavigationBar navigationBar;

    public HomePage(final Page page) {
        super(page);
        navigationBar = new NavigationBar(page);
    }

    @Override
    public HomePage waitUntilLoaded() {
        navigationBar.waitUntilDisplayed();
        return this;
    }

    public HomePage waitUntilAuthenticated() {
        page.waitForURL(url -> !url.contains(LOGIN_PATH));
        waitUntilLoaded();
        navigationBar.assertAuthenticated();
        return this;
    }
}