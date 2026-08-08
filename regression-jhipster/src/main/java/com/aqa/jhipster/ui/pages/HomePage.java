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
        page.waitForURL(url -> !url.contains(LOGIN_PATH));
        navigationBar.waitUntilDisplayed();
        return this;
    }

    public void assertAuthenticated() {
        navigationBar.assertAuthenticated();
    }
}