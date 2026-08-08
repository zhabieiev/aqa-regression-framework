package com.aqa.jhipster.ui.pages;

import com.aqa.jhipster.ui.models.LoginBean;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static java.util.Objects.requireNonNull;

public class LoginPage extends BasePage {

    private static final String PATH = "/login";

    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String SUBMIT_BUTTON = "submit";
    private static final String AUTHENTICATION_ERROR = "loginError";

    private final Locator usernameInput;
    private final Locator passwordInput;
    private final Locator submitButton;
    private final Locator authenticationError;

    public LoginPage(final Page page) {
        super(page);

        usernameInput = byDataCy(USERNAME);
        passwordInput = byDataCy(PASSWORD);
        submitButton = byDataCy(SUBMIT_BUTTON);
        authenticationError = byDataCy(AUTHENTICATION_ERROR);
    }

    public LoginPage open() {
        navigateTo(PATH);
        return this;
    }

    @Override
    public LoginPage waitUntilLoaded() {
        assertUrlContains(PATH);
        assertThat(usernameInput).isVisible();
        assertThat(passwordInput).isVisible();
        assertThat(submitButton).isVisible();

        return this;
    }

    public HomePage login(final LoginBean credentials, final List<String> headers) {
        submitCredentials(credentials, headers);

        return new HomePage(page).waitUntilLoaded();
    }

    public LoginPage loginExpectingFailure(final LoginBean credentials, final List<String> headers) {
        submitCredentials(credentials, headers);
        return this;
    }

    public void assertAuthenticationError() {
        assertThat(authenticationError).isVisible();
    }

    private void submitCredentials(final LoginBean credentials, final List<String> headers) {
        fillCredentials(credentials, headers);
        submitButton.click();
    }

    private void fillCredentials(final LoginBean credentials, final List<String> headers) {
        requireNonNull(credentials, "Login credentials must not be null");

        requireNonNull(headers, "Login headers must not be null");

        headers.forEach(header -> {
            switch (header) {
                case USERNAME -> usernameInput.fill(requiredValue(USERNAME, credentials.getUsername()));

                case PASSWORD -> passwordInput.fill(requiredValue(PASSWORD, credentials.getPassword()));

                default -> throw new IllegalArgumentException("Unsupported login field: " + header);
            }
        });
    }

    private String requiredValue(final String field, final String value) {
        return requireNonNull(value, "Missing value for login field: " + field);
    }
}