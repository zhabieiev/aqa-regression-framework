package com.aqa.nextjscommerce.steps;

import com.aqa.nextjscommerce.context.CommerceScenarioContext;
import com.aqa.nextjscommerce.pages.CommercePages;
import com.aqa.nextjscommerce.pages.StorefrontPage;

import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public final class CatalogSteps {

    private final CommercePages pages;
    private final CommerceScenarioContext scenarioContext;

    public CatalogSteps(final CommercePages pages, final CommerceScenarioContext scenarioContext) {
        this.pages = requireNonNull(pages, "Commerce pages must not be null");
        this.scenarioContext = requireNonNull(scenarioContext, "Commerce scenario context must not be null");
    }

    public void openStorefront() {
        pages.storefront().open();
    }

    public void searchFor(final String query) {
        pages.storefront().header().search(query);
    }

    public void allResultsShouldContain(final String expectedText) {
        final List<String> actualProductNames = pages.searchResults().productNames();
        assertThat(actualProductNames).as("Products shown in search results")
                .isNotEmpty()
                .allSatisfy(productName -> assertThat(productName).containsIgnoringCase(expectedText));
    }

    public void openProduct(final String productName) {
        final StorefrontPage storefront = pages.storefront();
        storefront.products().openProduct(productName);
        assertThat(pages.product().productName()).as("Opened product name").isEqualTo(productName);
    }

    public void openFirstSearchResult() {
        final List<String> actualProductNames = pages.searchResults().productNames();
        assertThat(actualProductNames).as("Products shown in search results").isNotEmpty();

        final String firstProductName = actualProductNames.get(0);
        assertThat(firstProductName).as("First product name in search results").isNotBlank();

        scenarioContext.rememberProductName(firstProductName);
        pages.searchResults().openProduct(firstProductName);
    }

    public void openedProductShouldMatchRemembered() {
        assertThat(pages.product().productName()).as("Opened product name")
                .isEqualTo(scenarioContext.productName());
    }
}