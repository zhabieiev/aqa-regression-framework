package com.aqa.nextjscommerce.definitions;

import com.aqa.nextjscommerce.models.ProductSelection;
import com.aqa.nextjscommerce.steps.ProductSteps;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.When;

import static com.aqa.core.convertors.DataTableConverter.convertToSingle;

public final class ProductDefinitions {

    private final ProductSteps steps;

    public ProductDefinitions(final ProductSteps steps) {
        this.steps = steps;
    }

    @When("the customer selects these product options:")
    public void selectOptions(final DataTable table) {
        steps.selectOptions(convertToSingle(table, ProductSelection.class));
    }

    @When("the customer adds the selected product to the cart")
    public void addToCart() {
        steps.addToCart();
    }
}