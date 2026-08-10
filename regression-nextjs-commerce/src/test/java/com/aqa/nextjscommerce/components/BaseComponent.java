package com.aqa.nextjscommerce.components;

import com.aqa.nextjscommerce.waits.WaitManager;
import org.openqa.selenium.By;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;

import java.util.List;

import static java.util.Objects.requireNonNull;

public abstract class BaseComponent {

    protected final WaitManager wait;

    private final ComponentRoot root;

    protected BaseComponent(final ComponentRoot root, final WaitManager wait) {
        this.root = requireNonNull(root, "Component root must not be null");

        this.wait = requireNonNull(wait, "Wait manager must not be null");
    }

    public final void awaitAvailable() {
        wait.available(root::resolve);
    }

    protected final SearchContext root() {
        return wait.available(root::resolve);
    }

    protected final WebElement visible(final By locator) {
        return wait.visible(root::resolve, locator);
    }

    protected final List<WebElement> allVisible(final By locator) {
        return wait.allVisible(root::resolve, locator);
    }

    protected final WebElement clickable(final By locator) {
        return wait.clickable(root::resolve, locator);
    }

    protected final void textIs(final By locator, final String value) {
        wait.textIs(root::resolve, locator, value);
    }

    protected final void invisible(final By locator) {
        wait.invisible(root::resolve, locator);
    }
}