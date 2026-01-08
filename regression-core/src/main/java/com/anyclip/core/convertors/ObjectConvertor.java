package com.anyclip.core.convertors;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static java.util.Objects.nonNull;

public class ObjectConvertor {

    public static Map<String, Object> convertObjToActualMap(Object actualObj, Map<String, Object> expected) {
        Map<String, Object> actual = convertObjToMapAndStringify(actualObj);
        retain(actual, expected);
        return actual;
    }

    public static List<Object> convertObjToActualMapList(Object actualObj, List<Map<String, Object>> expected) {
        List<Object> actual = convertListToMapList(actualObj);
        retain(actual, expected);
        return actual;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertObjToMap(Object input) {
        return new ObjectMapper().convertValue(input, Map.class);
    }

    private static List<Object> convertListToMapList(Object input) {
        return (List) stringify(new ObjectMapper().convertValue(input, List.class));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertObjToMapAndStringify(Object input) {
        return (Map<String, Object>) stringify(convertObjToMap(input));
    }

    private static Object stringify(Object origin) {
        if (origin != null) {
            if (origin instanceof Map map) {
                map.replaceAll((k, v) -> stringify(map.get(k)));
            } else if (origin instanceof List list) {
                list.replaceAll(ObjectConvertor::stringify);
            } else {
                origin = String.valueOf(origin);
            }
        }
        return origin;
    }

    private static void retain(Object actual, Object expected) {
        if (expected instanceof Map map) {
            map.forEach((key, value) -> {
                Object actValue = ((Map<String, Object>) actual).get(key);
                if (nonNull(actValue)) {
                    retain(actValue, value);
                }
            });
            ((Map<String, Object>) actual).keySet().retainAll(map.keySet());
        } else if (expected instanceof List list) {
            for (int i = 0; i < list.size(); i++) {
                try {
                    retain(((List) actual).get(i), list.get(i));
                } catch (IndexOutOfBoundsException e) {
                    throw new IllegalArgumentException(
                            "\nActual list:\n%s\nExpected list:\n%s".formatted(actual.toString(), expected.toString()),
                            e);
                }
            }
        }
    }
}