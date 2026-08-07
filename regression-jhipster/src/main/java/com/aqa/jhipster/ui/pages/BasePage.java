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
        page.navigate(normalizePath(path), new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        waitUntilLoaded();
    }

    public abstract BasePage waitUntilLoaded();

    protected Locator byDataCy(final String value) {
        return page.getByTestId(value);
    }

    protected void assertUrlContains(final String value) {
        final Pattern expectedUrl = Pattern.compile(".*" + value + ".*");
        assertThat(page).hasURL(expectedUrl);
    }

    public String currentUrl() {
        return page.url();
    }

    public String title() {
        return page.title();
    }

    private String normalizePath(final String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}