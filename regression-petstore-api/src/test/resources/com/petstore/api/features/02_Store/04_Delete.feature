Feature: Delete Store Order

  Scenario Outline: 0401 Delete store order
    Given api user creates store order and saves to 'storeOrder':
      | id       | <id>                  |
      | petId    | 1                     |
      | quantity | 1                     |
      | shipDate | date:{now-2m/m (EST)} |
      | status   | placed                |
      | complete | true                  |
    When api user deletes '@{storeOrder.id}' store order and saves to 'result'
    Then var 'result' is equal to string: '{"code":200,"type":"unknown","message":"2"}'
    Examples:
      | id |
      | 2  |
