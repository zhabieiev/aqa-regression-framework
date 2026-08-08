package com.aqa.jhipster.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static java.util.Objects.requireNonNull;

public abstract class BasePage {

    protected final Page page;

    protected BasePage(final Page page) {
        this.page = requireNonNull(page, "Playwright page must not be null");
    }

    protected void navigateTo(final String path) {
        page.navigate(
                normalizePath(path),
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
        );

        waitUntilLoaded();
    }

    public abstract BasePage waitUntilLoaded();

    protected Locator byDataCy(final String value) {
        return page.getByTestId(value);
    }

    protected void assertUrlContains(final String value) {
        final String expectedValue = requireNonNull(value, "Expected URL value must not be null");

        if (expectedValue.isBlank()) {
            throw new IllegalArgumentException("Expected URL value must not be blank");
        }

        final Pattern expectedUrl = Pattern.compile(
                ".*" + expectedValue + ".*"
        );

        assertThat(page).hasURL(expectedUrl);
    }

    private static String normalizePath(final String path) {
        requireNonNull(path, "Page path must not be null");

        if (path.isBlank()) {
            throw new IllegalArgumentException("Page path must not be blank");
        }

        return path.startsWith("/") ? path : "/" + path;
    }
}