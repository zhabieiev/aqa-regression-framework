package com.aqa.jhipster.ui.context;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

public class UiScenarioContext {

    private BrowserContext browserContext;
    private Page page;

    public BrowserContext browserContext() {
        if (browserContext == null) {
            throw new IllegalStateException(
                    "Browser context has not been initialized"
            );
        }
        return browserContext;
    }

    public void setBrowserContext(final BrowserContext browserContext) {
        this.browserContext = browserContext;
    }

    public Page page() {
        if (page == null) {
            throw new IllegalStateException(
                    "Playwright page has not been initialized"
            );
        }
        return page;
    }

    public void setPage(final Page page) {
        this.page = page;
    }

    public boolean isInitialized() {
        return browserContext != null && page != null;
    }

    public void clear() {
        page = null;
        browserContext = null;
    }
}