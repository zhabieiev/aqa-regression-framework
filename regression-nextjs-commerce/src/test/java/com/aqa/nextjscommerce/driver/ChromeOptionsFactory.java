package com.aqa.nextjscommerce.driver;

import com.aqa.nextjscommerce.config.UiSettings;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.Map;

public final class ChromeOptionsFactory {

    private ChromeOptionsFactory() {
    }

    public static ChromeOptions create(final UiSettings settings) {
        final ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--disable-dev-shm-usage",
                "--disable-notifications",
                "--disable-popup-blocking"
        );

        if (settings.headless()) {
            options.addArguments("--headless=new");
        }

        if (settings.mobileEnabled()) {
            options.setExperimentalOption("mobileEmulation", Map.of("deviceName", settings.mobileDevice()));
        } else {
            options.addArguments("--window-size=%d,%d".formatted(settings.windowWidth(), settings.windowHeight()));
        }
        return options;
    }
}
