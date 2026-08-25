package com.aqa.nextjscommerce.config;

import java.util.Locale;

public enum BrowserType {
    CHROME;

    public static BrowserType from(final String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported browser '%s'. Supported values: chrome".formatted(value), exception);
        }
    }
}
