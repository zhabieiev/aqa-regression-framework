package com.anyclip.core.convertors;

import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class MapConvertor {

    public static Map<String, Object> convertMapKeys(Map<String, String> input) {
        return JsonConvertor.convertMapToJson(input).toMap();
    }

    public static List<Map<String, Object>> convertMapListKeys(List<Map<String, String>> input) {
        return input.stream().map(MapConvertor::convertMapKeys).filter(map -> !map.isEmpty()).toList();
    }

    public static Map<String, String> convertMapKeysWithPrefix(Map<String, String> input, Set<String> prefixes) {
        final Predicate<String> startsWithPrefix = key -> prefixes.stream().anyMatch(key::startsWith);
        final Function<String, String> removePrefix = key -> prefixes.stream()
                .filter(key::startsWith)
                .map(prefix -> StringUtils.substringAfter(key, prefix))
                .findFirst()
                .orElse(key);
        Map<String, String> filteredInput = input.entrySet()
                .stream()
                .filter(entry -> startsWithPrefix.test(entry.getKey()))
                .collect(HashMap::new, (m, v) -> m.put(removePrefix.apply(v.getKey()), v.getValue()), HashMap::putAll);
        return filteredInput;
    }
}