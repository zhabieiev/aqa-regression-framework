package com.anyclip.core.convertors;

import com.anyclip.core.controllers.PropertiesController;
import com.anyclip.core.controllers.VariablesController;
import com.anyclip.core.utils.FileUtils;

import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringConvertor {

    public static final String VALUE = ".*";
    public static final String EMPTY_VALUE = "\"\"";
    public static final String ARRAY = "\\[(.*)]";
    public static final String DATETIME = "(.*)date:\\{([^}]+)}(.*)";
    public static final String PROPERTY = "(.*)\\$\\{([^}]+)}(.*)";
    public static final String VARIABLE = "(.*)@\\{([^}]+)}(.*)";
    public static final String FILE = "(.*)file:\\{([^}]+)}(.*)";
    public static final String REGEX = "regex:(.+)";

    //The conversion sequence must be preserved
    public static String convertString(String value, VariablesController variablesController) {
        while (Objects.requireNonNull(value).matches("%s|%s|%s|%s|%s".formatted(EMPTY_VALUE, DATETIME, PROPERTY, VARIABLE, FILE))) {
            if (value.matches(EMPTY_VALUE)) {
                value = "";
            } else if (value.matches(PROPERTY)) {
                value = convertProperty(value);
            } else if (value.matches(VARIABLE)) {
                value = convertVariable(value, variablesController);
            } else if (value.matches(DATETIME)) {
                value = convertDate(value);
            } else if ((value.matches(FILE))) {
                value = convertFile(value);
            }
        }
        return value;
    }

    private static String convertDate(String value) {
        return replace(DATETIME, value, DateConverter::convertByDateTimeVariables);
    }

    private static String convertProperty(String value) {
        return replace(PROPERTY, value, PropertiesController::getProperty);
    }

    private static String convertVariable(String value, VariablesController variablesController) {
        return replace(VARIABLE, value, variablesController::getVarString);
    }

    private static String convertFile(String value) {
        return replace(FILE, value, FileUtils::readFile);
    }

    private static String replace(final String regex, final String input, UnaryOperator<String> convertor) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(input);
        if (m.matches()) {
            return "%s%s%s".formatted(m.group(1), convertor.apply(m.group(2)), m.group(3));
        } else {
            throw new IllegalStateException("No match available\nRegex: %s\nInput: %s".formatted(regex, input));
        }
    }
}