package com.aqa.nextjscommerce.driver;

import com.aqa.nextjscommerce.config.UiSettings;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

public final class DriverFactory {

    private DriverFactory() {
    }

    public static WebDriver create(final UiSettings settings) {
        return switch (settings.browser()) {
            case CHROME -> new ChromeDriver(ChromeOptionsFactory.create(settings));
        };
    }
}
