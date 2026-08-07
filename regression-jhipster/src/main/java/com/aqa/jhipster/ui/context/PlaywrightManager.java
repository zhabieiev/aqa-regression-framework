package com.aqa.jhipster.ui.context;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.util.Locale;

import static com.aqa.core.enumerations.Property.INTERVAL_10_SECONDS;
import static com.aqa.core.enumerations.Property.UI_BROWSER;
import static com.aqa.core.enumerations.Property.UI_HEADLESS;
import static com.aqa.core.enumerations.Property.UI_SLOW_MOTION;
import static com.aqa.core.enumerations.Property.URL_UI;
import static java.util.Objects.requireNonNull;

public final class PlaywrightManager implements AutoCloseable {

    private static final String TEST_ID_ATTRIBUTE = "data-cy";

    private static final String CHROMIUM = "chromium";
    private static final String FIREFOX = "firefox";
    private static final String WEBKIT = "webkit";

    private Playwright playwright;
    private Browser browser;

    public void start(final UiScenarioContext scenarioContext) {
        requireNonNull(scenarioContext, "UI scenario context must not be null");

        assertNotStarted();

        try {
            playwright = Playwright.create();

            playwright.selectors().setTestIdAttribute(TEST_ID_ATTRIBUTE);

            browser = browserType().launch(launchOptions());

            final BrowserContext browserContext = browser.newContext(contextOptions());

            configure(browserContext);

            final Page page = browserContext.newPage();

            scenarioContext.initialize(browserContext, page);
        } catch (RuntimeException exception) {
            close();

            throw new IllegalStateException("Failed to start Playwright", exception);
        }
    }

    private BrowserType browserType() {
        final String browserName = UI_BROWSER.read().trim().toLowerCase(Locale.ROOT);

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
        return new Browser.NewContextOptions().setBaseURL(URL_UI.read());
    }

    private void configure(final BrowserContext browserContext) {
        final double timeout = doubleProperty(INTERVAL_10_SECONDS.read(), INTERVAL_10_SECONDS.name());

        browserContext.setDefaultTimeout(timeout);
        browserContext.setDefaultNavigationTimeout(timeout);
    }

    private boolean booleanProperty(final String value, final String property) {
        final String normalized = value.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "true" -> true;
            case "false" -> false;

            default -> throw new IllegalArgumentException(
                    "Property '%s' must be 'true' or 'false', but was '%s'".formatted(property, value));
        };
    }

    private double doubleProperty(final String value, final String property) {
        try {
            final double result = Double.parseDouble(value.trim());

            if (result < 0) {
                throw new IllegalArgumentException(
                        "Property '%s' must not be negative, but was '%s'".formatted(property, value));
            }

            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Property '%s' must be numeric, but was '%s'".formatted(property, value),
                    exception);
        }
    }

    private void assertNotStarted() {
        if (playwright != null || browser != null) {
            throw new IllegalStateException("Playwright has already been started");
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

            if (playwright != null) {
                playwright.close();
                playwright = null;
            }
        }
    }
}