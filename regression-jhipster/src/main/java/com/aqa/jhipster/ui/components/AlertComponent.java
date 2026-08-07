package com.aqa.jhipster.ui.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class AlertComponent extends BaseComponent {

    private static final String SUCCESS_ALERT_SELECTOR = ".alert-success";
    private static final String ERROR_ALERT_SELECTOR = ".alert-danger";

    private final Locator successAlert;
    private final Locator errorAlert;

    public AlertComponent(final Page page) {
        super(page, page.locator(SUCCESS_ALERT_SELECTOR).or(page.locator(ERROR_ALERT_SELECTOR)).first());
        successAlert = page.locator(SUCCESS_ALERT_SELECTOR);
        errorAlert = page.locator(ERROR_ALERT_SELECTOR);
    }

    public void assertSuccessDisplayed() {
        assertThat(successAlert).isVisible();
    }

    public void assertSuccessContains(final String message) {
        assertThat(successAlert).containsText(message);
    }

    public void assertErrorDisplayed() {
        assertThat(errorAlert).isVisible();
    }

    public void assertErrorContains(final String message) {
        assertThat(errorAlert).containsText(message);
    }
}