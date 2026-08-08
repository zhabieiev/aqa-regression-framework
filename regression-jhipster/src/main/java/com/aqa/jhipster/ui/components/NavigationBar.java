package com.aqa.jhipster.ui.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class NavigationBar extends BaseComponent {

    private static final String ACCOUNT_MENU = "accountMenu";
    private static final String LOGOUT_BUTTON = "logout";

    private final Locator logoutButton;

    public NavigationBar(final Page page) {
        super(page, page.getByTestId(ACCOUNT_MENU));
        logoutButton = byDataCy(LOGOUT_BUTTON);
    }

    public void assertAuthenticated() {
        root.click();
        assertThat(logoutButton).isVisible();
        page.keyboard().press("Escape");
    }
}