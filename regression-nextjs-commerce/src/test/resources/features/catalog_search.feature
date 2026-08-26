@ui @catalog
Feature: Product catalog search

  @smoke
  Scenario: Search returns the expected product
    Given the customer opens the Next.js Commerce storefront
    When the customer searches for "hoodie"
    And all returned product names contain "hoodie"

  @smoke
  Scenario: The first search result opens to its own product page
    Given the customer opens the Next.js Commerce storefront
    When the customer searches for "hoodie"
    When the customer opens the first search result
    Then the product page shows that product