package com.aqa.core.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

public class FileParseUtils {

    private final ObjectMapper jsonWriteObjMapper;
    private final ObjectMapper jsonReadObjMapper;
    private final ObjectMapper xmlMapper;

    private static ObjectMapper getFormat(final String format) {
        return switch (format) {
            case APPLICATION_XML -> readXML();
            default -> readJson();
        };
    }

    public FileParseUtils() {
        jsonWriteObjMapper = new ObjectMapper()
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        jsonReadObjMapper = new ObjectMapper()
                .configure(MapperFeature.USE_ANNOTATIONS, false)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL).setSerializationInclusion(
                        JsonInclude.Include.NON_EMPTY);
        xmlMapper = XmlMapper.builder().defaultUseWrapper(false).build();
    }

    public static ObjectMapper writeJson() {
        return getInstance().jsonWriteObjMapper;
    }

    public static ObjectMapper readJson() {
        return getInstance().jsonReadObjMapper;
    }

    public static ObjectMapper readXML() {
        return getInstance().xmlMapper;
    }

    public static <T> T read(final String src, final Class<T> valueType) {
        return read(src, valueType, APPLICATION_JSON);
    }

    public static <T> T read(final String src, final JavaType valueType) {
        return read(src, valueType, APPLICATION_JSON);
    }

    public static <T> T read(final String src, final Class<T> valueType, final String format) {
        try {
            return getFormat(format).readValue(src, valueType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T read(final String src, final JavaType valueType, final String format) {
        try {
            return getFormat(format).readValue(src, valueType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private static FileParseUtils getInstance() {
        return InstanceHolder.file;
    }

    private static class InstanceHolder {
        static FileParseUtils file = new FileParseUtils();
    }
}
