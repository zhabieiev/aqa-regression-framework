Feature: Get Products in Basket

  Background:
    Given api user creates and authenticates new user and saves the token to 'token':
      | email          | testuser050101@aqa-juiceshop.com |
      | password       | TestPassword050101!              |
      | passwordRepeat | TestPassword050101!              |

  Scenario: 050101 Get products in basket
    Given api user creates new basket and saves response to 'basket':
      | headers:Authorization | Bearer @{token} |
      | body:ProductId        | 1                                    |
      | body:BasketId         | 6                                    |
      | body:quantity         | 1                                    |
    Then var 'basket' is equal to object:
      | status         | success       |
      | data.id        | regex:(?s:.)+ |
      | data.ProductId | 1             |
      | data.BasketId  | 6             |
      | data.quantity  | 1             |
