Feature: Create New Pet

  Scenario: 0201 Create new pet with valid data
    Given api user creates pet and saves to 'pet':
      | name      | Doggie       |
      | photoUrls | ["test.jpg"] |
    Then var 'pet' is equal to object:
      | id        | regex:[0-9]{18,19} |
      | name      | Doggie             |
      | photoUrls | ["test.jpg"]       |
