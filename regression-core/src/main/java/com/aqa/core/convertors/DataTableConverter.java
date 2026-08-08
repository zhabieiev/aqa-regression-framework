package com.aqa.core.convertors;

import io.cucumber.datatable.DataTable;

import java.util.List;

import static com.aqa.core.Populator.populateList;
import static java.util.Objects.requireNonNull;

public final class DataTableConverter {

    private DataTableConverter() {
    }

    public static <T> T convertToSingle(final DataTable table, final Class<T> type) {
        final List<T> values = convertToList(table, type);
        if (values.size() != 1) {
            throw new IllegalArgumentException(
                    "Expected exactly one %s row, but found: %s".formatted(type.getSimpleName(), values.size()));
        }
        return values.getFirst();
    }

    public static List<String> getHeaders(final DataTable table) {
        requireNonNull(table, "Data table must not be null");
        final List<List<String>> rows = table.asLists(String.class);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Data table must contain a header row");
        }
        return List.copyOf(rows.getFirst());
    }

    private static <T> List<T> convertToList(final DataTable table, final Class<T> type) {
        requireNonNull(table, "Data table must not be null");
        requireNonNull(type, "Target type must not be null");
        return populateList(table.asMaps(String.class, String.class), type);
    }
}
