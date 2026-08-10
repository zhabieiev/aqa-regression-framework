package com.aqa.nextjscommerce.pages;

import com.aqa.nextjscommerce.components.ProductGridComponent;
import org.openqa.selenium.By;

import java.util.List;

public final class SearchResultsPage extends BasePage {

    private static final String SEARCH_PATH = "/search";

    private static final By SEARCH_RESULTS = By.cssSelector("main");

    private final ProductGridComponent products;

    public SearchResultsPage(final PageContext context) {
        super(context);

        this.products = new ProductGridComponent(documentRoot, wait);
    }

    public List<String> productNames() {
        waitUntilLoaded();
        return products.productNames();
    }

    public void openProduct(final String productName) {
        waitUntilLoaded();
        products.openProduct(productName);
    }

    @Override
    protected void waitForPageContent() {
        wait.urlContains(SEARCH_PATH);
        wait.visible(SEARCH_RESULTS);
    }
}