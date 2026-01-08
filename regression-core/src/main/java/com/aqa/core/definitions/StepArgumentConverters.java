package com.aqa.core.definitions;

import com.aqa.core.controllers.VariablesController;
import com.aqa.core.convertors.StringConvertor;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.DataTableType;
import io.cucumber.java.ParameterType;
import org.apache.commons.text.StringEscapeUtils;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;


public record StepArgumentConverters(VariablesController variablesController) {

    @DataTableType
    public Map<String, String> convertMap(DataTable table) {
        DataTable transposedTable = table.transpose();
        return convertMapValues(transposedTable.entries().getFirst());
    }

    @DataTableType
    public  List<Map<String, String>> convertList(DataTable table) {
        DataTable transposedTable = table.transpose();
        return transposedTable.entries().stream().map(this::convertMapValues).collect(toList());
    }

    @ParameterType(value = "'([^'\\\\]*(\\\\.[^'\\\\]*)*)'|\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"|([^\\s]+)")
    public String convertString(String singleQuoted, String doubleQuoted, String unquoted) {
        final String value = nonNull(singleQuoted) ? singleQuoted : nonNull(doubleQuoted) ? doubleQuoted : unquoted;
        final String unescapedValue = StringEscapeUtils.unescapeJava(value);
        return StringConvertor.convertString(unescapedValue, variablesController);
    }

    private Map<String, String> convertMapValues(Map<String, String> input) {
        Map<String, String> output = new TreeMap<>();
        input.forEach((k, v) -> {
            if (v != null) output.put(k, StringConvertor.convertString(v, variablesController));
        });
        return output;
    }
}
