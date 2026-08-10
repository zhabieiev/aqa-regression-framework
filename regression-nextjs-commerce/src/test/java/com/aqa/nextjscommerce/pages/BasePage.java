package com.aqa.nextjscommerce.pages;

import com.aqa.nextjscommerce.components.CartDrawerComponent;
import com.aqa.nextjscommerce.components.ComponentRoot;
import com.aqa.nextjscommerce.components.HeaderComponent;
import com.aqa.nextjscommerce.waits.WaitManager;
import org.openqa.selenium.WebDriver;

import java.net.URI;

import static java.util.Objects.requireNonNull;

public abstract class BasePage {

    protected final WebDriver driver;
    protected final WaitManager wait;
    protected final ComponentRoot documentRoot;

    private final URI baseUri;
    private final HeaderComponent header;
    private final CartDrawerComponent cart;

    protected BasePage(final PageContext context) {
        final PageContext pageContext = requireNonNull(context, "Page context must not be null");

        this.driver = pageContext.driver();

        this.wait = new WaitManager(this.driver, pageContext.settings().explicitWait());

        this.documentRoot = ComponentRoot.document(this.driver);

        this.baseUri = createBaseUri(pageContext.settings().baseUrl());

        this.header = new HeaderComponent(this.documentRoot, this.wait);

        this.cart = new CartDrawerComponent(this.documentRoot, this.wait);
    }

    protected final void navigateTo(final String path) {
        driver.get(resolveUrl(path));
        waitUntilLoaded();
    }

    public final void waitUntilLoaded() {
        wait.documentReady();
        waitForPageContent();
    }

    protected abstract void waitForPageContent();

    public final HeaderComponent header() {
        return header;
    }

    public final CartDrawerComponent cart() {
        return cart;
    }

    public final String currentUrl() {
        return driver.getCurrentUrl();
    }

    public final String title() {
        return driver.getTitle();
    }

    private String resolveUrl(final String path) {
        final String relativePath = requireNonNull(path, "Page path must not be null").replaceFirst("^/+", "");

        return baseUri.resolve(relativePath).toString();
    }

    private static URI createBaseUri(final String baseUrl) {
        final String value = requireNonNull(baseUrl, "Base URL must not be null");

        return URI.create(value.endsWith("/") ? value : value + "/");
    }
}