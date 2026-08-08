package com.aqa.jhipster.ui.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static java.util.Objects.requireNonNull;

public class DataTableComponent extends BaseComponent {

    private static final Pattern REGEX_SPECIAL_CHARACTER = Pattern.compile("([\\\\.^$|?*+()\\[\\]{}])");

    public DataTableComponent(final Page page, final Locator table) {
        super(page, table);
    }

    public Locator rowByExactCellText(final String column, final String value) {
        final int columnIndex = columnIndex(column);
        final Locator matchingCell = page.locator("td:nth-child(" + (columnIndex + 1) + ")")
                .filter(new Locator.FilterOptions().setHasText(exactText(value)));
        return dataRows().filter(new Locator.FilterOptions().setHas(matchingCell));
    }

    public Locator cell(final Locator row, final String column) {
        requireNonNull(row, "Table row locator must not be null");
        return row.locator("td").nth(columnIndex(column));
    }

    public void assertRowAbsent(final String column, final String value) {
        assertThat(rowByExactCellText(column, value)).hasCount(0);
    }

    private Locator dataRows() {
        return root.locator("tbody > tr");
    }

    private int columnIndex(final String column) {
        final Locator header =
                root.locator("thead th").filter(new Locator.FilterOptions().setHasText(exactText(column)));
        assertThat(header).hasCount(1);
        final Object index = header.evaluate("element => element.cellIndex");
        return ((Number) index).intValue();
    }

    private static Pattern exactText(final String value) {
        final String expectedText = requireNonNull(value, "Expected table text must not be null");
        if (expectedText.isBlank()) {
            throw new IllegalArgumentException("Expected table text must not be blank");
        }
        final String escapedText = REGEX_SPECIAL_CHARACTER.matcher(expectedText).replaceAll("\\\\$1");
        return Pattern.compile("^\\s*" + escapedText + "\\s*$");
    }
}