package com.aqa.jhipster.ui.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class DataTableComponent extends BaseComponent {

    public DataTableComponent(final Page page, final Locator table) {
        super(page, table);
    }

    public Locator rowContaining(final String text) {
        return root.getByRole(AriaRole.ROW).filter(new Locator.FilterOptions().setHasText(text));
    }

    public void assertRowNotDisplayed(final String text) {
        assertThat(rowContaining(text)).isHidden();
    }

    public int dataRowCount() {
        final int totalRows = root.getByRole(AriaRole.ROW).count();

        return Math.max(totalRows - 1, 0);
    }
}