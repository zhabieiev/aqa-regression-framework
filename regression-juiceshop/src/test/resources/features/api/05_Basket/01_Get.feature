Feature: Get Products in Basket

  Scenario: 050101 Get products in basket
    Given api user creates new user and saves to 'result':
      | email                   | testuser050101@gmail.com |
      | password                | TestPassword050101!      |
      | passwordRepeat          | TestPassword050101!      |
      | securityQuestion.id     | 1                        |
      | securityQuestion.answer | admin                    |
    When api user authenticates and saves response to 'token':
      | email    | testuser050101@gmail.com |
      | password | TestPassword050101!      |
    And api user creates new basket and saves response to 'basket':
      | headers:Authorization | Bearer @{token.authentication.token} |
      | body:ProductId        | 1                                    |
      | body:BasketId         | 6                                    |
      | body:quantity         | 1                                    |
    Then var 'basket' is equal to object:
      | status         | success       |
      | data.id        | regex:(?s:.)+ |
      | data.ProductId | 1             |
      | data.BasketId  | 6             |
      | data.quantity  | 1             |
