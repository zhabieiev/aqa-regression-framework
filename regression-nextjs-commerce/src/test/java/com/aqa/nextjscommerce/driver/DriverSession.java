package com.aqa.nextjscommerce.driver;

import com.aqa.nextjscommerce.config.UiSettings;
import org.openqa.selenium.WebDriver;

public final class DriverSession {

    private final UiSettings settings = UiSettings.fromProperties();
    private WebDriver driver;

    public WebDriver driver() {
        if (driver == null) {
            driver = DriverFactory.create(settings);
            driver.manage().timeouts().pageLoadTimeout(settings.pageLoadTimeout());
        }
        return driver;
    }

    public boolean isStarted() {
        return driver != null;
    }

    public UiSettings settings() {
        return settings;
    }

    public void quit() {
        if (driver != null) {
            try {
                driver.quit();
            } finally {
                driver = null;
            }
        }
    }
}
