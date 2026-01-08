package com.anyclip.core.controllers;


import static com.anyclip.core.enumerations.Property.PACKAGE_MODELS;

public class ClassController {

    public static Class<?> getClazz(final String className) {
        try {
            return Class.forName("%s.%s".formatted(PACKAGE_MODELS.read(), className));
        } catch (final ClassNotFoundException ex) {
            throw new ClassCastException(String.format("\n%s", ex));
        }
    }
}