package com.anyclip.core;

import com.anyclip.core.utils.FileParseUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.anyclip.core.convertors.JsonConvertor.convertMapToJsonString;

public class Populator {

    public static <T> T populate(final Map<String, String> map, final Class<T> returnType) {
        return FileParseUtils.read(convertMapToJsonString(map), returnType);
    }

    public static <T> List<T> populateList(final List<Map<String, String>> list, final Class<T> returnType) {
        return list.stream().map(map -> populate(map, returnType)).collect(Collectors.toList());
    }
}
