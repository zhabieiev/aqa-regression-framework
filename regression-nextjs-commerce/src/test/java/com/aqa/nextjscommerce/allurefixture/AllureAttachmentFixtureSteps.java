package com.aqa.nextjscommerce.allurefixture;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.Scenario;

/** No UI, hooks, pages, drivers, endpoints, or application state are reachable from this fixture glue. */
public final class AllureAttachmentFixtureSteps {
    @Given("an isolated Allure attachment fixture")
    public void isolatedFixture() {
        // The intentionally empty step establishes a single passing Cucumber scenario.
    }

    @Then("the configured Allure result directory is available")
    public void configuredResultDirectory() {
        String directory = System.getProperty("allure.results.directory");
        assertThat(directory).isNotBlank();
        assertThat(directory).isEqualTo(System.getProperty("fixture.expected.allure.resultsDirectory"));
        assertThat(Files.isDirectory(Path.of(directory))).isTrue();
    }

    @After
    public void attachProof(Scenario scenario) {
        scenario.attach("controlled fixture attachment", "text/plain", "fixture-attachment");
    }
}
