package com.aqa.jhipster.ui.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class DataTableComponent extends BaseComponent {

    private final Locator table;

    public DataTableComponent(final Page page, final Locator table) {
        super(page, table);
        this.table = table;
    }

    @Override
    public void waitUntilDisplayed() {
        assertThat(table).isVisible();
    }

    public Locator rowContaining(final String text) {
        return table.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(text));
    }

    public void assertRowDisplayed(final String text) {
        assertThat(rowContaining(text)).isVisible();
    }

    public void assertRowNotDisplayed(final String text) {
        assertThat(rowContaining(text)).isHidden();
    }

    public int rowCount() {
        return table.getByRole(AriaRole.ROW).count();
    }

    public Locator root() {
        return table;
    }
}