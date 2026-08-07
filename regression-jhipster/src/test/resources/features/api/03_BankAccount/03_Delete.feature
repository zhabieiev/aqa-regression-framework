@api
Feature: Delete Bank Account

  Scenario: 030301 Delete bank account
    Given api user creates new bank account and saves to 'account':
      | name    | Bank Account 030301 |
      | balance | 1000                |
    When api user deletes bank account with id:
      | path:id | @{account.id} |
    And api user tries to get bank account and saves to 'result':
      | path:id             | @{account.id} |
      | response:statusCode | 404           |
    Then var 'result' is equal to object:
      | detail   | 404 NOT_FOUND                                          |
      | instance | /api/bank-accounts/@{account.id}                       |
      | status   | 404                                                    |
      | title    | Not Found                                              |
      | type     | https://www.jhipster.tech/problem/problem-with-message |
      | message  | error.http.404                                         |
      | path     | /api/bank-accounts/@{account.id}                       |
