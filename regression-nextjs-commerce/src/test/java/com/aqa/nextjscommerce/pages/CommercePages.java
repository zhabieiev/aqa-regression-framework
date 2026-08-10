package com.aqa.nextjscommerce.pages;

import com.aqa.nextjscommerce.driver.DriverSession;

import static java.util.Objects.requireNonNull;

public final class CommercePages {

    private final DriverSession session;
    private PageContext context;

    public CommercePages(final DriverSession session) {
        this.session = requireNonNull(session, "Driver session must not be null");
    }

    public StorefrontPage storefront() {
        return new StorefrontPage(context());
    }

    public SearchResultsPage searchResults() {
        return new SearchResultsPage(context());
    }

    public ProductPage product() {
        return new ProductPage(context());
    }

    private PageContext context() {
        if (context == null) {
            context = new PageContext(session.driver(), session.settings());
        }

        return context;
    }
}