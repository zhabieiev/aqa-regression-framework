package com.aqa.nextjscommerce.waits;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NoSuchShadowRootException;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public final class WaitManager {

    private final WebDriver driver;
    private final WebDriverWait wait;

    public WaitManager(final WebDriver driver, final Duration timeout) {
        this.driver = requireNonNull(driver, "WebDriver must not be null");

        final Duration waitTimeout = requireNonNull(timeout, "Wait timeout must not be null");

        if (waitTimeout.isZero() || waitTimeout.isNegative()) {
            throw new IllegalArgumentException("Wait timeout must be greater than zero");
        }

        this.wait = new WebDriverWait(this.driver, waitTimeout);
        this.wait.ignoring(StaleElementReferenceException.class);
    }

    public void documentReady() {
        wait.until(currentDriver -> "complete".equals(
                ((JavascriptExecutor) currentDriver).executeScript("return document.readyState")));
    }

    public SearchContext available(final Supplier<? extends SearchContext> contextSupplier) {
        final Supplier<? extends SearchContext> supplier =
                requireNonNull(contextSupplier, "Search context supplier must not be null");

        return wait.until(currentDriver -> {
            try {
                return supplier.get();
            } catch (NoSuchElementException | NoSuchShadowRootException | StaleElementReferenceException exception) {
                return null;
            }
        });
    }

    public WebElement visible(final By locator) {
        return visible(() -> driver, locator);
    }

    public WebElement visible(final SearchContext context, final By locator) {
        final SearchContext searchContext = requireNonNull(context, "Search context must not be null");

        return visible(() -> searchContext, locator);
    }

    public WebElement visible(final Supplier<? extends SearchContext> contextSupplier, final By locator) {
        final Supplier<? extends SearchContext> supplier =
                requireNonNull(contextSupplier, "Search context supplier must not be null");

        final By elementLocator = requireNonNull(locator, "Element locator must not be null");

        return wait.until(currentDriver -> {
            try {
                final WebElement element = supplier.get().findElement(elementLocator);

                return element.isDisplayed() ? element : null;
            } catch (NoSuchElementException | NoSuchShadowRootException | StaleElementReferenceException exception) {
                return null;
            }
        });
    }

    public List<WebElement> allVisible(final By locator) {
        return allVisible(() -> driver, locator);
    }

    public List<WebElement> allVisible(final SearchContext context, final By locator) {
        final SearchContext searchContext = requireNonNull(context, "Search context must not be null");

        return allVisible(() -> searchContext, locator);
    }

    public List<WebElement> allVisible(final Supplier<? extends SearchContext> contextSupplier, final By locator) {
        final Supplier<? extends SearchContext> supplier =
                requireNonNull(contextSupplier, "Search context supplier must not be null");

        final By elementLocator = requireNonNull(locator, "Element locator must not be null");

        return wait.until(currentDriver -> {
            try {
                final List<WebElement> elements = supplier.get().findElements(elementLocator);

                if (elements.isEmpty()) {
                    return null;
                }

                return elements.stream().allMatch(WebElement::isDisplayed) ? elements : null;
            } catch (NoSuchElementException | NoSuchShadowRootException | StaleElementReferenceException exception) {
                return null;
            }
        });
    }

    public WebElement clickable(final By locator) {
        return clickable(() -> driver, locator);
    }

    public WebElement clickable(final SearchContext context, final By locator) {
        final SearchContext searchContext = requireNonNull(context, "Search context must not be null");

        return clickable(() -> searchContext, locator);
    }

    public WebElement clickable(final Supplier<? extends SearchContext> contextSupplier, final By locator) {
        final Supplier<? extends SearchContext> supplier =
                requireNonNull(contextSupplier, "Search context supplier must not be null");

        final By elementLocator = requireNonNull(locator, "Element locator must not be null");

        return wait.until(currentDriver -> {
            try {
                final WebElement element = supplier.get().findElement(elementLocator);

                return element.isDisplayed() && element.isEnabled() ? element : null;
            } catch (NoSuchElementException | NoSuchShadowRootException | StaleElementReferenceException exception) {
                return null;
            }
        });
    }

    public WebElement clickable(final WebElement element) {
        return wait.until(
                ExpectedConditions.elementToBeClickable(requireNonNull(element, "WebElement must not be null")));
    }

    public void urlContains(final String value) {
        wait.until(ExpectedConditions.urlContains(requireNonNull(value, "URL value must not be null")));
    }

    public void textIs(final By locator, final String value) {
        textIs(() -> driver, locator, value);
    }

    public void textIs(final SearchContext context, final By locator, final String value) {
        final SearchContext searchContext = requireNonNull(context, "Search context must not be null");

        textIs(() -> searchContext, locator, value);
    }

    public void textIs(final Supplier<? extends SearchContext> contextSupplier, final By locator, final String value) {
        final Supplier<? extends SearchContext> supplier =
                requireNonNull(contextSupplier, "Search context supplier must not be null");

        final By elementLocator = requireNonNull(locator, "Element locator must not be null");

        final String expectedText = requireNonNull(value, "Expected text must not be null");

        wait.until(currentDriver -> {
            try {
                return expectedText.equals(supplier.get().findElement(elementLocator).getText());
            } catch (NoSuchElementException | NoSuchShadowRootException | StaleElementReferenceException exception) {
                return false;
            }
        });
    }

    public void invisible(final By locator) {
        invisible(() -> driver, locator);
    }

    public void invisible(final SearchContext context, final By locator) {
        final SearchContext searchContext = requireNonNull(context, "Search context must not be null");

        invisible(() -> searchContext, locator);
    }

    public void invisible(final Supplier<? extends SearchContext> contextSupplier, final By locator) {
        final Supplier<? extends SearchContext> supplier =
                requireNonNull(contextSupplier, "Search context supplier must not be null");

        final By elementLocator = requireNonNull(locator, "Element locator must not be null");

        wait.until(currentDriver -> {
            try {
                final List<WebElement> elements = supplier.get().findElements(elementLocator);

                return elements.isEmpty() || elements.stream().noneMatch(WebElement::isDisplayed);
            } catch (NoSuchElementException | NoSuchShadowRootException | StaleElementReferenceException exception) {
                return true;
            }
        });
    }

    public SearchContext shadowRoot(final By hostLocator) {
        return shadowRoot(() -> driver, hostLocator);
    }

    public SearchContext shadowRoot(final SearchContext context, final By hostLocator) {
        final SearchContext searchContext = requireNonNull(context, "Search context must not be null");

        return shadowRoot(() -> searchContext, hostLocator);
    }

    public SearchContext shadowRoot(final Supplier<? extends SearchContext> contextSupplier, final By hostLocator) {
        final Supplier<? extends SearchContext> supplier =
                requireNonNull(contextSupplier, "Search context supplier must not be null");

        final By locator = requireNonNull(hostLocator, "Shadow host locator must not be null");

        return available(() -> supplier.get().findElement(locator).getShadowRoot());
    }
}