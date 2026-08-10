package com.aqa.nextjscommerce.pages;

import com.aqa.nextjscommerce.components.ProductGridComponent;
import org.openqa.selenium.By;

public final class StorefrontPage extends BasePage {

    private static final String STOREFRONT_PATH = "";

    private static final By PRODUCT_GRID = By.cssSelector("main a[href^='/product/']");

    private final ProductGridComponent products;

    public StorefrontPage(final PageContext context) {
        super(context);

        this.products = new ProductGridComponent(documentRoot, wait);
    }

    public StorefrontPage open() {
        navigateTo(STOREFRONT_PATH);
        return this;
    }

    public ProductGridComponent products() {
        return products;
    }

    @Override
    protected void waitForPageContent() {
        wait.visible(PRODUCT_GRID);
    }
}