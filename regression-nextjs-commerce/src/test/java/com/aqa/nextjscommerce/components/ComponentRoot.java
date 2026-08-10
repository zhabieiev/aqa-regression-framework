package com.aqa.nextjscommerce.components;

import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebDriver;

import static java.util.Objects.requireNonNull;

@FunctionalInterface
public interface ComponentRoot {

    SearchContext resolve();

    static ComponentRoot document(final WebDriver driver) {
        final WebDriver webDriver = requireNonNull(driver, "WebDriver must not be null");

        return () -> webDriver;
    }

    static ComponentRoot element(final ComponentRoot parent, final By rootLocator) {
        final ComponentRoot parentRoot = requireNonNull(parent, "Parent component root must not be null");

        final By locator = requireNonNull(rootLocator, "Root locator must not be null");

        return () -> parentRoot.resolve().findElement(locator);
    }

    static ComponentRoot shadow(final ComponentRoot parent, final By shadowHostLocator) {
        final ComponentRoot parentRoot = requireNonNull(parent, "Parent component root must not be null");

        final By locator = requireNonNull(shadowHostLocator, "Shadow host locator must not be null");

        return () -> parentRoot.resolve().findElement(locator).getShadowRoot();
    }
}