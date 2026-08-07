package com.aqa.jhipster.ui.hooks;

import com.aqa.core.controllers.PropertiesController;
import com.aqa.jhipster.ui.context.PlaywrightManager;
import com.aqa.jhipster.ui.context.UiScenarioContext;
import com.microsoft.playwright.BrowserContext;
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

@Slf4j
@SuppressWarnings("resource")
public class UiHooks {

    private static final Path TRACE_DIRECTORY =
            Path.of("target", "playwright", "traces");

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final PlaywrightManager playwrightManager;
    private final UiScenarioContext scenarioContext;

    public UiHooks(
            final PlaywrightManager playwrightManager,
            final UiScenarioContext scenarioContext
    ) {
        this.playwrightManager = playwrightManager;
        this.scenarioContext = scenarioContext;
    }

    @Before(value = "@ui", order = 0)
    public void startBrowser(final Scenario scenario) {
        playwrightManager.start(scenarioContext);

        if (isTraceEnabled()) {
            startTracing();
        }

        log.info(
                "Browser started for scenario '{}'",
                scenario.getName()
        );
    }

    @After(value = "@ui", order = 1000)
    public void closeBrowser(final Scenario scenario) {
        try {
            if (scenarioContext.isInitialized() && scenario.isFailed()) {
                attachScreenshot(scenario);
            }

            if (scenarioContext.isInitialized() && isTraceEnabled()) {
                stopTracing(scenario);
            }
        } finally {
            if (scenarioContext.isInitialized()) {
                scenarioContext.browserContext().close();
            }

            scenarioContext.clear();
            playwrightManager.close();

            log.info(
                    "Browser closed for scenario '{}'",
                    scenario.getName()
            );
        }
    }

    private void startTracing() {
        scenarioContext.browserContext()
                .tracing()
                .start(
                        new Tracing.StartOptions()
                                .setScreenshots(true)
                                .setSnapshots(true)
                                .setSources(true)
                );
    }

    private void stopTracing(final Scenario scenario) {
        if (scenario.isFailed()) {
            Path tracePath = createTracePath(scenario);

            scenarioContext.browserContext()
                    .tracing()
                    .stop(
                            new Tracing.StopOptions()
                                    .setPath(tracePath)
                    );

            log.info("Playwright trace saved to '{}'", tracePath);
        } else {
            scenarioContext.browserContext()
                    .tracing()
                    .stop();
        }
    }

    private void attachScreenshot(final Scenario scenario) {
        Page page = scenarioContext.page();

        byte[] screenshot = page.screenshot(
                new Page.ScreenshotOptions()
                        .setFullPage(true)
        );

        scenario.attach(
                screenshot,
                "image/png",
                "Failure screenshot"
        );

        log.error(
                "UI scenario failed. URL: '{}', title: '{}'",
                page.url(),
                page.title()
        );
    }

    private Path createTracePath(final Scenario scenario) {
        try {
            Files.createDirectories(TRACE_DIRECTORY);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Cannot create Playwright trace directory",
                    exception
            );
        }

        String scenarioName = scenario.getName()
                .replaceAll("[^a-zA-Z0-9-_]", "_");

        String timestamp = LocalDateTime.now()
                .format(TIMESTAMP_FORMAT);

        return TRACE_DIRECTORY.resolve(
                "%s-%s.zip".formatted(scenarioName, timestamp)
        );
    }

    private boolean isTraceEnabled() {
        return Boolean.parseBoolean(
                PropertiesController.getProperty("ui.trace")
        );
    }
}