Feature: Create Store Order

  Scenario: 020101 Create new store order with valid data
    Given api user creates store order and saves to 'storeOrder':
      | petId    | 1234567890123456789   |
      | quantity | 1                     |
      | shipDate | date:{now-2m/m (EST)} |
      | status   | placed                |
      | complete | true                  |
    Then var 'storeOrder' is equal to object:
      | id       | regex:[0-9]{19}                                    |
      | petId    | 1234567890123456789                                |
      | quantity | 1                                                  |
      | shipDate | regex:\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z |
      | status   | placed                                             |
      | complete | true                                               |