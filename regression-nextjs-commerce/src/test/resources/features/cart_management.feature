@ui @cart
Feature: Shopping cart management

  @smoke
  Scenario: Add a configured product and manage its quantity
    Given the customer opens the Next.js Commerce storefront
    When the customer opens the product "Acme Circles T-Shirt"
    And the customer selects these product options:
      | product              | color | size |
      | Acme Circles T-Shirt | Black | S    |
    And the customer adds the selected product to the cart
    Then the cart contains the selected product with quantity 1
    When the customer increases the cart item quantity
    Then the cart item quantity is 2
    When the customer decreases the cart item quantity
    Then the cart item quantity is 1
    When the customer removes the product from the cart
    Then the cart is empty