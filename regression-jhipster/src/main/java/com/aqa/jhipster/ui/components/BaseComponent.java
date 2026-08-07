package com.aqa.jhipster.ui.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static java.util.Objects.requireNonNull;

public abstract class BaseComponent {

    protected final Page page;
    protected final Locator root;

    protected BaseComponent(final Page page, final Locator root) {
        this.page = requireNonNull(page, "Playwright page must not be null");
        this.root = requireNonNull(root, "Component root locator must not be null");
    }

    public void waitUntilDisplayed() {
        assertThat(root).isVisible();
    }

    protected Locator byDataCy(final String value) {
        return page.getByTestId(value);
    }
}