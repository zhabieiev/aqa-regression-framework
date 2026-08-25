package com.aqa.nextjscommerce.config;

import java.time.Duration;

import static com.aqa.nextjscommerce.config.CommerceProperty.UI_BROWSER;
import static com.aqa.nextjscommerce.config.CommerceProperty.UI_EXPLICIT_WAIT_SECONDS;
import static com.aqa.nextjscommerce.config.CommerceProperty.UI_HEADLESS;
import static com.aqa.nextjscommerce.config.CommerceProperty.UI_MOBILE_DEVICE;
import static com.aqa.nextjscommerce.config.CommerceProperty.UI_MOBILE_ENABLED;
import static com.aqa.nextjscommerce.config.CommerceProperty.UI_PAGE_LOAD_TIMEOUT_SECONDS;
import static com.aqa.nextjscommerce.config.CommerceProperty.UI_WINDOW_HEIGHT;
import static com.aqa.nextjscommerce.config.CommerceProperty.UI_WINDOW_WIDTH;
import static com.aqa.nextjscommerce.config.CommerceProperty.URL_UI;

public record UiSettings(
        String baseUrl,
        BrowserType browser,
        boolean headless,
        Duration explicitWait,
        Duration pageLoadTimeout,
        int windowWidth,
        int windowHeight,
        boolean mobileEnabled,
        String mobileDevice
) {
    public static UiSettings fromProperties() {
        return new UiSettings(
                stripTrailingSlash(URL_UI.read()),
                BrowserType.from(UI_BROWSER.read()),
                Boolean.parseBoolean(UI_HEADLESS.read()),
                Duration.ofSeconds(Long.parseLong(UI_EXPLICIT_WAIT_SECONDS.read())),
                Duration.ofSeconds(Long.parseLong(UI_PAGE_LOAD_TIMEOUT_SECONDS.read())),
                Integer.parseInt(UI_WINDOW_WIDTH.read()),
                Integer.parseInt(UI_WINDOW_HEIGHT.read()),
                Boolean.parseBoolean(UI_MOBILE_ENABLED.read()),
                UI_MOBILE_DEVICE.read()
        );
    }

    private static String stripTrailingSlash(final String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
