package com.aqa.nextjscommerce.definitions;

import com.aqa.nextjscommerce.steps.CartSteps;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public final class CartDefinitions {

    private final CartSteps steps;

    public CartDefinitions(final CartSteps steps) {
        this.steps = steps;
    }

    @When("the customer increases the cart item quantity")
    public void increaseQuantity() {
        steps.increaseQuantity();
    }

    @When("the customer decreases the cart item quantity")
    public void decreaseQuantity() {
        steps.decreaseQuantity();
    }

    @When("the customer removes the product from the cart")
    public void removeProduct() {
        steps.removeProduct();
    }

    @Then("the cart contains the selected product with quantity {int}")
    public void verifySelectedProduct(final int quantity) {
        steps.shouldContainSelectedProduct(quantity);
    }

    @Then("the cart item quantity is {int}")
    public void verifyQuantity(final int quantity) {
        steps.quantityShouldBe(quantity);
    }

    @Then("the cart is empty")
    public void verifyEmptyCart() {
        steps.shouldBeEmpty();
    }
}