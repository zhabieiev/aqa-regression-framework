package com.aqa.nextjscommerce.pages;

import com.aqa.nextjscommerce.config.UiSettings;
import org.openqa.selenium.WebDriver;

import static java.util.Objects.requireNonNull;

public record PageContext(
        WebDriver driver,
        UiSettings settings
) {

    public PageContext {
        requireNonNull(driver, "WebDriver must not be null");
        requireNonNull(settings, "UI settings must not be null");
    }
}