Feature: Create Store Order

  Scenario Outline: 0301 Get store order
    Given api user creates store order and saves to 'storeOrder':
      | id       | <id>                  |
      | petId    | 1234567890123456789   |
      | quantity | 1                     |
      | shipDate | date:{now-2m/m (EST)} |
      | status   | placed                |
      | complete | true                  |
    When api user gets '@{storeOrder.id}' store order and saves to 'result'
    Then var 'result' is equal to object:
      | id       | <id>                                               |
      | petId    | 1234567890123456789                                |
      | quantity | 1                                                  |
      | shipDate | regex:\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z |
      | status   | placed                                             |
      | complete | true                                               |
    Examples:
      | id |
      | 1  |

  Scenario: 0302 Get store order with invalid ID
    Given api user tries to get '9999999' store order and saves to 'result':
      | response:statusCode | 404 |
    Then var 'result' is equal to string: '{"code":1,"type":"error","message":"Order not found"}'