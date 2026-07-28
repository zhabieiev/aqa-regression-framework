Feature: Create New Pet

  Scenario Outline: 0201 Create new pet with valid data
    Given api user creates pet and saves to 'pet':
      | name      | <name>      |
      | photoUrls | <photoUrls> |
    Then var 'pet' is equal to object:
      | id        | regex:[0-9]{18,19} |
      | name      | <name>             |
      | photoUrls | <photoUrls>       |
    Examples:
      | name   | photoUrls    |
      | Doggie | ["test.jpg"] |
      | @&@*   | []           |
