package com.aqa.nextjscommerce.hooks;

import com.aqa.nextjscommerce.driver.DriverSession;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;

public final class UiHooks {

    private final DriverSession session;

    public UiHooks(final DriverSession session) {
        this.session = requireNonNull(session, "Driver session must not be null");
    }

    @Before("@ui")
    public void startBrowser() {
        session.driver();
    }

    @After("@ui")
    public void stopBrowser(final Scenario scenario) {
        if (!session.isStarted()) {
            return;
        }

        try {
            if (scenario.isFailed()) {
                attachFailureDetails(scenario, session.driver());
            }
        } finally {
            session.quit();
        }
    }

    private void attachFailureDetails(final Scenario scenario, final WebDriver driver) {
        safelyAttach(scenario, "screenshot-error", () -> attachScreenshot(scenario, driver));

        safelyAttach(scenario, "url-error",
                () -> scenario.attach(driver.getCurrentUrl(), "text/uri-list", "current-url"));

        safelyAttach(scenario, "title-error", () -> scenario.attach(driver.getTitle(), "text/plain", "page-title"));

        safelyAttach(scenario, "page-source-error",
                () -> scenario.attach(driver.getPageSource().getBytes(StandardCharsets.UTF_8), "text/html",
                        "page-source"));
    }

    private void attachScreenshot(final Scenario scenario, final WebDriver driver) {
        if (!(driver instanceof TakesScreenshot screenshotDriver)) {
            throw new IllegalStateException("WebDriver does not support screenshots");
        }

        scenario.attach(screenshotDriver.getScreenshotAs(OutputType.BYTES), "image/png", "failure-screenshot");
    }

    private void safelyAttach(final Scenario scenario, final String errorName, final Runnable attachment) {
        try {
            attachment.run();
        } catch (final RuntimeException exception) {
            scenario.attach(exception.toString(), "text/plain", errorName);
        }
    }
}