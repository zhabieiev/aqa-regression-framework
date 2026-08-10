@ui @catalog
Feature: Product catalog search

  @smoke
  Scenario: Search returns the expected product
    Given the customer opens the Next.js Commerce storefront
    When the customer searches for "hoodie"
    Then the search results contain the product "Acme Hoodie"
    And all returned product names contain "hoodie"