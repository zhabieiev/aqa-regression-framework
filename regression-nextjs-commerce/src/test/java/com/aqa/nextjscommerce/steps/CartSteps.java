package com.aqa.nextjscommerce.steps;

import com.aqa.nextjscommerce.components.CartDrawerComponent;
import com.aqa.nextjscommerce.context.CommerceScenarioContext;
import com.aqa.nextjscommerce.models.CartItem;
import com.aqa.nextjscommerce.models.ProductSelection;
import com.aqa.nextjscommerce.pages.CommercePages;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public final class CartSteps {

    private final CommercePages pages;
    private final CommerceScenarioContext scenarioContext;

    public CartSteps(final CommercePages pages, final CommerceScenarioContext scenarioContext) {
        this.pages = requireNonNull(pages, "Commerce pages must not be null");
        this.scenarioContext = requireNonNull(scenarioContext, "Commerce scenario context must not be null");
    }

    public void shouldContainSelectedProduct(final int expectedQuantity) {
        final ProductSelection selection = scenarioContext.selection();
        final CartItem expectedCartItem =
                new CartItem(selection.product(), "%s / %s".formatted(selection.color(), selection.size()),
                        expectedQuantity);
        final CartItem actualCartItem = cart().item();
        assertThat(actualCartItem).as("Product displayed in the cart").isEqualTo(expectedCartItem);
    }

    public void increaseQuantity() {
        cart().increaseQuantity();
    }

    public void decreaseQuantity() {
        cart().decreaseQuantity();
    }

    public void quantityShouldBe(final int expectedQuantity) {
        assertThat(cart().quantity()).as("Cart item quantity").isEqualTo(expectedQuantity);
    }

    public void removeProduct() {
        cart().removeItem();
    }

    public void shouldBeEmpty() {
        assertThat(cart().isEmpty()).as("Cart empty state").isTrue();
    }

    private CartDrawerComponent cart() {
        return pages.product().cart();
    }
}