package com.aqa.jhipster.ui.context;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import static java.util.Objects.requireNonNull;

public final class UiScenarioContext {

    private BrowserContext browserContext;
    private Page page;

    public void initialize(final BrowserContext browserContext, final Page page) {
        if (isInitialized()) {
            throw new IllegalStateException("UI scenario context has already been initialized");
        }

        this.browserContext = requireNonNull(browserContext, "Browser context must not be null");

        this.page = requireNonNull(page, "Playwright page must not be null");
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
}