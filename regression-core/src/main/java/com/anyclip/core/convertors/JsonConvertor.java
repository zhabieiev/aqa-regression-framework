package com.anyclip.core.convertors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Objects.isNull;
import static java.util.regex.Pattern.compile;

public class JsonConvertor {
    private static final String REGEX_INDEX = "\\d+";
    private static final String REGEX_ARRAY = "^\\[(.*?)]";
    private static final String REGEX_ARRAY_INDEX = format("\\[%s]", REGEX_INDEX);

    public static String convertMapToJsonString(final Map<String, String> map) {
        return convertMapToJson(map).toString();
    }

    public static JSONObject convertMapToJson(final Map<String, String> map) {
        JSONObject obj = new JSONObject();
        map.forEach((k, v) -> {
            Object value = compile(REGEX_ARRAY).matcher(v).find() ? new JSONArray(v) : v;
            if (k.contains(".")) {
                convertNestedField(obj, k, value);
            } else {
                obj.put(k, value);
            }
        });
        return obj;
    }

    private static void convertNestedField(JSONObject origin, final String field, final Object value) {
        JSONObject copy = origin;
        final List<String> fields = asList(field.split("\\."));
        for (int i = 0; i < fields.size() - 1; i++) {
            final String key = fields.get(i);
            if (compile(REGEX_ARRAY_INDEX).matcher(key).find()) {
                copy = populateArrayObject(copy, key);
            } else {
                copy = populateObject(copy, key);
            }
        }
        copy.put(fields.get(fields.size() - 1), value);
    }

    private static JSONObject populateObject(final JSONObject origin, final String key) {
        if (isNull(origin.optJSONObject(key))) {
            origin.put(key, new JSONObject());
        }
        return origin.getJSONObject(key);
    }

    private static JSONObject populateArrayObject(final JSONObject origin, String arrayKey) {
        final Matcher m = compile(REGEX_INDEX).matcher(arrayKey);
        m.find();
        final int index = parseInt(m.group(0));
        final String key = arrayKey.replaceFirst(REGEX_ARRAY_INDEX, "");
        if (isNull(origin.optJSONArray(key))) {
            origin.put(key, new JSONArray());
        }
        initArray(index, origin.getJSONArray(key));
        return origin.getJSONArray(key).getJSONObject(index);
    }

    private static void initArray(final int index, JSONArray array) {
        final int destArrayLength = index + 1;
        final int arrayLength = array.length();
        if (arrayLength < destArrayLength) {
            Stream.generate(JSONObject::new).limit(destArrayLength - arrayLength).forEach(array::put);
        }
    }
}
