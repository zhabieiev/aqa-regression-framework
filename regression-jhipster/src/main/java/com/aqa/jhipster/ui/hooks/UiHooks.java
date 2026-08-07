package com.aqa.jhipster.ui.hooks;

import com.aqa.jhipster.ui.context.PlaywrightManager;
import com.aqa.jhipster.ui.context.UiScenarioContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static com.aqa.core.enumerations.Property.UI_TRACE;

@Slf4j
@SuppressWarnings("resource")
public class UiHooks {

    private static final Path TRACE_DIRECTORY = Path.of("target", "playwright", "traces");

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final PlaywrightManager playwrightManager;
    private final UiScenarioContext scenarioContext;

    private boolean tracingStarted;

    public UiHooks(final PlaywrightManager playwrightManager, final UiScenarioContext scenarioContext) {
        this.playwrightManager = playwrightManager;
        this.scenarioContext = scenarioContext;
    }

    @Before(value = "@ui", order = 0)
    public void startBrowser(final Scenario scenario) {
        playwrightManager.start(scenarioContext);

        if (isTraceEnabled()) {
            startTracing();
            tracingStarted = true;
        }

        log.info("Browser started for scenario '{}'", scenario.getName());
    }

    @After(value = "@ui", order = 1000)
    public void closeBrowser(final Scenario scenario) {
        try {
            captureFailureScreenshot(scenario);
            finishTracing(scenario);
        } finally {
            closeResources(scenario);
        }
    }

    private void captureFailureScreenshot(final Scenario scenario) {
        if (!scenario.isFailed() || !scenarioContext.isInitialized()) {
            return;
        }

        try {
            attachScreenshot(scenario);
        } catch (RuntimeException exception) {
            log.warn("Could not capture screenshot for scenario '{}'", scenario.getName(), exception);
        }
    }

    private void finishTracing(final Scenario scenario) {
        if (!tracingStarted || !scenarioContext.isInitialized()) {
            return;
        }

        try {
            stopTracing(scenario);
        } catch (RuntimeException exception) {
            log.warn("Could not save Playwright trace for scenario '{}'", scenario.getName(), exception);
        } finally {
            tracingStarted = false;
        }
    }

    private void startTracing() {
        scenarioContext.browserContext()
                .tracing()
                .start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
    }

    private void stopTracing(final Scenario scenario) {
        if (scenario.isFailed()) {
            final Path tracePath = createTracePath(scenario);

            scenarioContext.browserContext().tracing().stop(new Tracing.StopOptions().setPath(tracePath));

            log.info("Playwright trace saved to '{}'", tracePath);

            return;
        }

        scenarioContext.browserContext().tracing().stop();
    }

    private void attachScreenshot(final Scenario scenario) {
        final Page page = scenarioContext.page();

        final byte[] screenshot = page.screenshot(new Page.ScreenshotOptions().setFullPage(true));

        scenario.attach(screenshot, "image/png", "Failure screenshot");

        log.error("UI scenario failed. URL: '{}', title: '{}'", page.url(), page.title());
    }

    private Path createTracePath(final Scenario scenario) {
        createTraceDirectory();

        final String scenarioName = scenario.getName().replaceAll("[^a-zA-Z0-9-_]", "_");

        final String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        final String uniqueId = UUID.randomUUID().toString().substring(0, 8);

        return TRACE_DIRECTORY.resolve("%s-%s-%s.zip".formatted(scenarioName, timestamp, uniqueId));
    }

    private void createTraceDirectory() {
        try {
            Files.createDirectories(TRACE_DIRECTORY);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create Playwright trace directory", exception);
        }
    }

    private void closeResources(final Scenario scenario) {
        final boolean browserWasInitialized = scenarioContext.isInitialized();

        try {
            if (browserWasInitialized) {
                scenarioContext.browserContext().close();
            }
        } catch (RuntimeException exception) {
            log.warn("Could not close browser context for scenario '{}'", scenario.getName(), exception);
        } finally {
            scenarioContext.clear();
            closePlaywright(scenario);
        }

        if (browserWasInitialized) {
            log.info("Browser closed for scenario '{}'", scenario.getName());
        }
    }

    private void closePlaywright(final Scenario scenario) {
        try {
            playwrightManager.close();
        } catch (RuntimeException exception) {
            log.warn("Could not close Playwright resources for scenario '{}'", scenario.getName(), exception);
        }
    }

    private boolean isTraceEnabled() {
        return Boolean.parseBoolean(UI_TRACE.read());
    }
}