package com.aqa.jhipster.ui.context;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.util.Locale;

import static com.aqa.core.enumerations.Property.INTERVAL_10_SECONDS;
import static com.aqa.core.enumerations.Property.URL_UI;
import static com.aqa.jhipster.config.Property.*;
import static java.util.Objects.requireNonNull;

public final class PlaywrightManager implements AutoCloseable {

    private static final String TEST_ID_ATTRIBUTE = "data-cy";

    private static final String CHROMIUM = "chromium";
    private static final String FIREFOX = "firefox";
    private static final String WEBKIT = "webkit";

    private Playwright playwright;
    private Browser browser;

    public void start(final UiScenarioContext scenarioContext) {
        final UiScenarioContext context = requireNonNull(scenarioContext, "UI scenario context must not be null");

        assertNotStarted();
        assertContextNotInitialized(context);

        try {
            playwright = Playwright.create();

            playwright.selectors().setTestIdAttribute(TEST_ID_ATTRIBUTE);

            browser = browserType().launch(launchOptions());

            final BrowserContext browserContext = browser.newContext(contextOptions());

            configure(browserContext);

            final Page page = browserContext.newPage();

            context.initialize(browserContext, page);
        } catch (RuntimeException exception) {
            try {
                close();
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            }

            throw new IllegalStateException("Failed to start Playwright", exception);
        }
    }

    private BrowserType browserType() {
        final String browserName = requiredProperty(UI_BROWSER.read(), UI_BROWSER.name()).toLowerCase(Locale.ROOT);

        return switch (browserName) {
            case CHROMIUM -> playwright.chromium();
            case FIREFOX -> playwright.firefox();
            case WEBKIT -> playwright.webkit();

            default -> throw new IllegalArgumentException(
                    "Unsupported browser '%s'. Supported values: %s, %s, %s".formatted(browserName, CHROMIUM, FIREFOX,
                            WEBKIT));
        };
    }

    private BrowserType.LaunchOptions launchOptions() {
        return new BrowserType.LaunchOptions().setHeadless(booleanProperty(UI_HEADLESS.read(), UI_HEADLESS.name()))
                .setSlowMo(doubleProperty(UI_SLOW_MOTION.read(), UI_SLOW_MOTION.name()));
    }

    private Browser.NewContextOptions contextOptions() {
        return new Browser.NewContextOptions().setBaseURL(requiredProperty(URL_UI.read(), URL_UI.name()));
    }

    private void configure(final BrowserContext browserContext) {
        final double timeout = doubleProperty(INTERVAL_10_SECONDS.read(), INTERVAL_10_SECONDS.name());

        browserContext.setDefaultTimeout(timeout);
        browserContext.setDefaultNavigationTimeout(timeout);
    }

    private static boolean booleanProperty(final String value, final String property) {
        final String normalized = requiredProperty(value, property).toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "true" -> true;
            case "false" -> false;

            default -> throw new IllegalArgumentException(
                    "Property '%s' must be 'true' or 'false', but was '%s'".formatted(property, value));
        };
    }

    private static double doubleProperty(final String value, final String property) {
        final String normalized = requiredProperty(value, property);

        try {
            final double result = Double.parseDouble(normalized);

            if (!Double.isFinite(result) || result < 0) {
                throw new IllegalArgumentException(
                        "Property '%s' must be a finite non-negative number, but was '%s'".formatted(property, value));
            }

            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Property '%s' must be numeric, but was '%s'".formatted(property, value),
                    exception);
        }
    }

    private static String requiredProperty(final String value, final String property) {
        final String result = requireNonNull(value, "Property '%s' must not be null".formatted(property)).trim();

        if (result.isBlank()) {
            throw new IllegalArgumentException("Property '%s' must not be blank".formatted(property));
        }

        return result;
    }

    private void assertNotStarted() {
        if (playwright != null || browser != null) {
            throw new IllegalStateException("Playwright has already been started");
        }
    }

    private void assertContextNotInitialized(final UiScenarioContext scenarioContext) {
        if (scenarioContext.isInitialized()) {
            throw new IllegalStateException("UI scenario context has already been initialized");
        }
    }

    @Override
    public void close() {
        try {
            if (browser != null) {
                browser.close();
            }
        } finally {
            browser = null;

            try {
                if (playwright != null) {
                    playwright.close();
                }
            } finally {
                playwright = null;
            }
        }
    }
}