package com.aqa.nextjscommerce.components;

import com.aqa.nextjscommerce.waits.WaitManager;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import java.util.List;

public final class ProductGridComponent extends BaseComponent {

    private static final By ROOT = By.cssSelector("main");

    private static final By PRODUCT_TITLES = By.cssSelector("a[href^='/product/'] h3");

    private static final By PRODUCT_LINK = By.xpath("./ancestor::a[starts-with(@href, '/product/')][1]");

    public ProductGridComponent(final ComponentRoot parentRoot, final WaitManager wait) {
        super(ComponentRoot.element(parentRoot, ROOT), wait);
    }

    public List<String> productNames() {
        return allVisible(PRODUCT_TITLES).stream().map(WebElement::getText).map(String::trim).distinct().toList();
    }

    public void openProduct(final String productName) {
        final WebElement title = allVisible(PRODUCT_TITLES).stream()
                .filter(element -> productName.equals(element.getText().trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Product '%s' was not found. Available products: %s".formatted(productName, productNames())));

        final WebElement link = title.findElement(PRODUCT_LINK);

        wait.clickable(link).click();
        wait.urlContains("/product/");
    }
}