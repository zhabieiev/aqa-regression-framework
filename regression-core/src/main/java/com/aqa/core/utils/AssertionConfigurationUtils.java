package com.aqa.core.utils;

import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.assertj.core.util.Strings;

import java.util.regex.Pattern;

import static java.util.Objects.nonNull;

public class AssertionConfigurationUtils {

    private final static String NULL = "null";
    private final static String REGEX_PREFIX = "regex:";

    public static RecursiveComparisonConfiguration getRegexConfiguration() {
        return RecursiveComparisonConfiguration.builder().withComparatorForType((o1, o2) -> {
            if (!Strings.isNullOrEmpty(o2) && o2.startsWith(REGEX_PREFIX)) {
                String regexPattern = o2.substring(REGEX_PREFIX.length());
                if (o1 == null) {
                    return regexPattern.equals(NULL) ? 0 : 1;
                }
                return Pattern.matches(regexPattern, o1) ? 0 : 1;
            }
            return nonNull(o1) ? o1.equals(o2) ? 0 : 1 : 1;
        }, String.class).build();
    }
}
