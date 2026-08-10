package com.aqa.nextjscommerce.steps;

import com.aqa.nextjscommerce.context.CommerceScenarioContext;
import com.aqa.nextjscommerce.models.ProductSelection;
import com.aqa.nextjscommerce.pages.CommercePages;
import com.aqa.nextjscommerce.pages.ProductPage;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public final class ProductSteps {

    private final CommercePages pages;
    private final CommerceScenarioContext scenarioContext;

    public ProductSteps(final CommercePages pages, final CommerceScenarioContext scenarioContext) {
        this.pages = requireNonNull(pages, "Commerce pages must not be null");
        this.scenarioContext = requireNonNull(scenarioContext, "Commerce scenario context must not be null");
    }

    public void selectOptions(final ProductSelection selection) {
        requireNonNull(selection, "Product selection must not be null");

        final ProductPage productPage = pages.product();

        assertThat(productPage.productName()).as("Product opened before selecting options")
                .isEqualTo(selection.product());

        productPage.select(selection);
        scenarioContext.remember(selection);
    }

    public void addToCart() {
        pages.product().addToCart();
    }
}