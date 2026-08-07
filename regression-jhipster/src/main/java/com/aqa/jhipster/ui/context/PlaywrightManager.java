package com.aqa.jhipster.ui.context;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.util.Locale;

import static com.aqa.core.enumerations.Property.INTERVAL_10_SECONDS;
import static com.aqa.core.enumerations.Property.UI_BROWSER;
import static com.aqa.core.enumerations.Property.UI_HEADLESS;
import static com.aqa.core.enumerations.Property.UI_SLOW_MOTION;
import static com.aqa.core.enumerations.Property.URL_UI;

public class PlaywrightManager implements AutoCloseable {

    private static final String TEST_ID_ATTRIBUTE = "data-cy";

    private Playwright playwright;
    private Browser browser;

    public void start(final UiScenarioContext scenarioContext) {
        if (playwright != null || browser != null) {
            throw new IllegalStateException(
                    "Playwright has already been started"
            );
        }

        playwright = Playwright.create();
        playwright.selectors().setTestIdAttribute(TEST_ID_ATTRIBUTE);

        browser = browserType().launch(launchOptions());

        BrowserContext browserContext = browser.newContext(
                new Browser.NewContextOptions()
                        .setBaseURL(URL_UI.read())
        );

        browserContext.setDefaultTimeout(timeout());

        scenarioContext.setBrowserContext(browserContext);
        scenarioContext.setPage(browserContext.newPage());
    }

    private BrowserType browserType() {
        String browserName = UI_BROWSER.read()
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (browserName) {
            case "chromium" -> playwright.chromium();
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> throw new IllegalArgumentException(
                    "Unsupported browser: " + browserName
            );
        };
    }

    private BrowserType.LaunchOptions launchOptions() {
        return new BrowserType.LaunchOptions()
                .setHeadless(Boolean.parseBoolean(UI_HEADLESS.read()))
                .setSlowMo(Double.parseDouble(UI_SLOW_MOTION.read()));
    }

    private double timeout() {
        return Double.parseDouble(INTERVAL_10_SECONDS.read());
    }

    @Override
    public void close() {
        if (browser != null) {
            browser.close();
            browser = null;
        }

        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }
}