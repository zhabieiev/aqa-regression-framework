package com.anyclip.core.definitions;

import com.anyclip.core.controllers.VariablesController;
import com.anyclip.core.utils.AssertionConfigurationUtils;
import io.cucumber.java.en.Then;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.anyclip.core.convertors.MapConvertor.convertMapKeys;
import static com.anyclip.core.convertors.MapConvertor.convertMapListKeys;
import static com.anyclip.core.convertors.ObjectConvertor.convertObjToActualMap;
import static com.anyclip.core.convertors.ObjectConvertor.convertObjToActualMapList;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public record GeneralDefinitions(VariablesController variablesController) {

    public static final RecursiveComparisonConfiguration CONFIG = AssertionConfigurationUtils.getRegexConfiguration();

    @Then("var {string} is equal to string: {convertString}")
    public void varIsEqualToString(String var, String string) {
        assertThat(variablesController.getVar(var)).isEqualTo(string);
    }

    @Then("var {string} is equal to int: {int}")
    public void varIsEqualToInt(String var, int intValue) {
        assertThat(variablesController.getVar(var)).isEqualTo(intValue);
    }

    @Then("var {string} is equal to object:")
    public void varIsEqualToObject(String var, Map<String, String> map) {
        Map<String, Object> expected = convertMapKeys(map);
        Map<String, Object> actual = convertObjToActualMap(variablesController.getVar(var), expected);
        assertThat(actual).usingRecursiveComparison(CONFIG).ignoringExpectedNullFields().isEqualTo(expected);
    }

    @Then("var {string} is equal to object in any order:")
    public void varIsEqualToObjectInAnyOrder(String var, Map<String, String> map) {
        Map<String, Object> expected = convertMapKeys(map);
        Map<String, Object> actual = convertObjToActualMap(variablesController.getVar(var), expected);
        assertThat(actual).usingRecursiveComparison(CONFIG)
                .ignoringExpectedNullFields()
                .ignoringCollectionOrder()
                .isEqualTo(expected);
    }

    @Then("var {string} is equal to object with {string} list in any order:")
    public void varIsEqualToObjectInAnyOrder(String var, String listIgnoreOrder, Map<String, String> map) {
        Map<String, Object> expected = convertMapKeys(map);
        Map<String, Object> actual = convertObjToActualMap(variablesController.getVar(var), expected);
        assertThat(actual).usingRecursiveComparison(CONFIG)
                .ignoringExpectedNullFields()
                .ignoringCollectionOrderInFields(listIgnoreOrder)
                .isEqualTo(expected);
    }

    @Then("var {string} is equal to list:")
    public void varIsEqualToList(String var, List<Map<String, String>> maps) {
        List<Map<String, Object>> expected = convertMapListKeys(maps);
        List<Object> actual = convertObjToActualMapList(variablesController.getVar(var), expected);
        assertThat(actual).usingRecursiveComparison(CONFIG).ignoringExpectedNullFields().isEqualTo(expected);
    }

    @Then("var {string} list contains item {convertString}")
    public void varListContainsItem(String var, String string) {
        Object actual = variablesController.getVar(var);
        List<String> actualAsList = ((List<?>) actual).stream().map(Object::toString).collect(Collectors.toList());
        String expected = string;
        boolean contains = actualAsList.contains(expected);
        assertThat(contains).as("Expected list '%s' to CONTAIN: %s\nActual list: %s", var, expected, actualAsList).isTrue();
    }

    @Then("var {string} list does not contain item {convertString}")
    public void varListDoesNotContainItem(String var, String string) {
        Object actual = variablesController.getVar(var);
        List<String> actualAsList = ((List<?>) actual).stream().map(Object::toString).collect(Collectors.toList());
        String expected = string;
        boolean contains = actualAsList.contains(expected);
        assertThat(contains).as("Expected list '%s' to NOT CONTAIN: %s\nActual list: %s", var, expected, actualAsList).isFalse();
    }

    @Then("var {string} is equal to list in any order:")
    public void varIsEqualToListInAnyOrder(String var, List<Map<String, String>> maps) {
        List<Map<String, Object>> expected = convertMapListKeys(maps);
        List<Object> actual = convertObjToActualMapList(variablesController.getVar(var), expected);
        assertThat(actual).usingRecursiveComparison(CONFIG)
                .ignoringExpectedNullFields()
                .ignoringCollectionOrder()
                .isEqualTo(expected);
    }
}