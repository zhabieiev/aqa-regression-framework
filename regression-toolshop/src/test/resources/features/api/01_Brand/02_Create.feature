Feature: Create Brand

  Scenario: 010201 Create new brand
    Given api user creates new brand and saves to 'result':
      | name | TestBrand010201 |
      | slug | testbrand010201 |
    Then var 'result' is equal to object:
      | name | TestBrand010201           |
      | slug | testbrand010201           |
      | id   | regex:^[A-Za-z0-9-_]{26}$ |