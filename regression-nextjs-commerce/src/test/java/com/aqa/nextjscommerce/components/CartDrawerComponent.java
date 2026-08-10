package com.aqa.nextjscommerce.components;

import com.aqa.nextjscommerce.models.CartItem;
import com.aqa.nextjscommerce.waits.WaitManager;
import org.openqa.selenium.By;

public final class CartDrawerComponent extends BaseComponent {

    private static final By ROOT = By.cssSelector("[role='dialog']");

    private static final By ITEM_NAME = By.cssSelector("a[href^='/product/'] span");

    private static final By ITEM_VARIANT = By.cssSelector("a[href^='/product/'] p");

    private static final By QUANTITY =
            By.xpath(".//button[@aria-label='Reduce item quantity']" + "/ancestor::div[1]/p/span");

    private static final By INCREASE = By.cssSelector("button[aria-label='Increase item quantity']");

    private static final By DECREASE = By.cssSelector("button[aria-label='Reduce item quantity']");

    private static final By REMOVE = By.cssSelector("button[aria-label='Remove cart item']");

    private static final By EMPTY_MESSAGE = By.xpath(".//p[normalize-space()='Your cart is empty.']");

    public CartDrawerComponent(final ComponentRoot parentRoot, final WaitManager wait) {
        super(ComponentRoot.element(parentRoot, ROOT), wait);
    }

    public void awaitOpen() {
        awaitAvailable();
    }

    public CartItem item() {
        awaitOpen();

        return new CartItem(visible(ITEM_NAME).getText().trim(), visible(ITEM_VARIANT).getText().trim(), quantity());
    }

    public int quantity() {
        return Integer.parseInt(visible(QUANTITY).getText().trim());
    }

    public void increaseQuantity() {
        final int currentQuantity = quantity();

        clickable(INCREASE).click();

        textIs(QUANTITY, Integer.toString(currentQuantity + 1));
    }

    public void decreaseQuantity() {
        final int currentQuantity = quantity();

        clickable(DECREASE).click();

        textIs(QUANTITY, Integer.toString(currentQuantity - 1));
    }

    public void removeItem() {
        clickable(REMOVE).click();
        visible(EMPTY_MESSAGE);
    }

    public boolean isEmpty() {
        return !root().findElements(EMPTY_MESSAGE).isEmpty();
    }
}