package com.aqa.nextjscommerce.context;

import com.aqa.nextjscommerce.models.ProductSelection;

import static java.util.Objects.requireNonNull;

public final class CommerceScenarioContext {

    private ProductSelection selection;
    private String productName;

    public void remember(final ProductSelection value) {
        selection = requireNonNull(value, "Product selection must not be null");
    }

    public ProductSelection selection() {
        return requireNonNull(selection, "Product options have not been selected in this scenario");
    }

    public void rememberProductName(final String value) {
        productName = requireNonNull(value, "Product name must not be null");
    }

    public String productName() {
        return requireNonNull(productName, "Product name has not been remembered in this scenario");
    }
}
