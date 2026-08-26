package com.aqa.nextjscommerce.definitions;

import com.aqa.nextjscommerce.steps.CatalogSteps;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public final class CatalogDefinitions {

    private final CatalogSteps steps;

    public CatalogDefinitions(final CatalogSteps steps) {
        this.steps = steps;
    }

    @Given("the customer opens the Next.js Commerce storefront")
    public void openStorefront() {
        steps.openStorefront();
    }

    @When("the customer searches for {string}")
    public void searchFor(final String query) {
        steps.searchFor(query);
    }

    @When("the customer opens the product {string}")
    public void openProduct(final String productName) {
        steps.openProduct(productName);
    }

    @Then("all returned product names contain {string}")
    public void verifyAllResults(final String text) {
        steps.allResultsShouldContain(text);
    }

    @When("the customer opens the first search result")
    public void openFirstSearchResult() {
        steps.openFirstSearchResult();
    }

    @Then("the product page shows that product")
    public void verifyOpenedProductMatchesRemembered() {
        steps.openedProductShouldMatchRemembered();
    }
}