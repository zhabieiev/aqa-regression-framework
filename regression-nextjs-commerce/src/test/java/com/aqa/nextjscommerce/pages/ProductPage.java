package com.aqa.nextjscommerce.pages;

import com.aqa.nextjscommerce.models.ProductSelection;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public final class ProductPage extends BasePage {

    private static final String PRODUCT_PATH = "/product/";

    private static final By PRODUCT_NAME = By.cssSelector("main h1");

    private static final By ADD_TO_CART = By.cssSelector("button[aria-label='Add to cart']");

    private static final String OPTION_BUTTON_XPATH = "//button[@title=%s]";

    public ProductPage(final PageContext context) {
        super(context);
    }

    public String productName() {
        waitUntilLoaded();
        return wait.visible(PRODUCT_NAME).getText().trim();
    }

    public void select(final ProductSelection selection) {
        waitUntilLoaded();
        selectOption("Color", selection.color(), "color");
        selectOption("Size", selection.size(), "size");
    }

    public void addToCart() {
        waitUntilLoaded();
        wait.clickable(ADD_TO_CART).click();
        cart().awaitOpen();
    }

    @Override
    protected void waitForPageContent() {
        wait.urlContains(PRODUCT_PATH);
        wait.visible(PRODUCT_NAME);
    }

    private void selectOption(final String option, final String value, final String queryParameter) {
        final WebElement button = wait.clickable(optionButton(option, value));

        button.click();

        wait.urlContains("%s=%s".formatted(queryParameter, value));
    }

    private static By optionButton(final String option, final String value) {
        final String title = "%s %s".formatted(option, value);

        return By.xpath(OPTION_BUTTON_XPATH.formatted(xpathLiteral(title)));
    }

    private static String xpathLiteral(final String value) {
        if (!value.contains("'")) {
            return "'%s'".formatted(value);
        }

        if (!value.contains("\"")) {
            return "\"%s\"".formatted(value);
        }

        return "concat('%s')".formatted(value.replace("'", "', \"'\", '"));
    }
}