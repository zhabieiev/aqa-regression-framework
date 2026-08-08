package com.aqa.jhipster.ui.context;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import static java.util.Objects.requireNonNull;

public final class UiScenarioContext {

    private BrowserContext browserContext;
    private Page page;

    public void initialize(final BrowserContext browserContext, final Page page) {
        assertNotInitialized();

        final BrowserContext initializedBrowserContext =
                requireNonNull(browserContext, "Browser context must not be null");
        final Page initializedPage = requireNonNull(page, "Playwright page must not be null");

        if (initializedPage.context() != initializedBrowserContext) {
            throw new IllegalArgumentException("Playwright page must belong to the provided browser context");
        }

        this.browserContext = initializedBrowserContext;
        this.page = initializedPage;
    }

    public BrowserContext browserContext() {
        if (browserContext == null) {
            throw new IllegalStateException("Browser context has not been initialized");
        }

        return browserContext;
    }

    public Page page() {
        if (page == null) {
            throw new IllegalStateException("Playwright page has not been initialized");
        }

        return page;
    }

    public boolean isInitialized() {
        return browserContext != null && page != null;
    }

    public void clear() {
        page = null;
        browserContext = null;
    }

    private void assertNotInitialized() {
        if (browserContext != null || page != null) {
            throw new IllegalStateException("UI scenario context has already been initialized");
        }
    }
}