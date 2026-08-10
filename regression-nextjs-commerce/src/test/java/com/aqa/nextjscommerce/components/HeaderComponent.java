package com.aqa.nextjscommerce.components;

import com.aqa.nextjscommerce.waits.WaitManager;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;

public final class HeaderComponent extends BaseComponent {

    private static final By ROOT = By.xpath("//nav[.//input[@name='q']]");

    private static final By SEARCH_INPUT = By.cssSelector("input[name='q']");

    private static final By OPEN_CART = By.cssSelector("button[aria-label='Open cart']");

    public HeaderComponent(final ComponentRoot parentRoot, final WaitManager wait) {
        super(ComponentRoot.element(parentRoot, ROOT), wait);
    }

    public void search(final String query) {
        final WebElement input = visible(SEARCH_INPUT);

        input.clear();
        input.sendKeys(query, Keys.ENTER);

        wait.urlContains("/search");
    }

    public void openCart() {
        clickable(OPEN_CART).click();
    }

    public int cartItemCount() {
        final String value = visible(OPEN_CART).getText().trim();

        return value.isEmpty() ? 0 : Integer.parseInt(value);
    }
}