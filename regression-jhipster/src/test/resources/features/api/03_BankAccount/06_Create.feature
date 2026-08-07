Feature: Create Bank Account

    Scenario: 030601 Create bank account
        Given api user creates new bank account and saves to 'account':
        | name    | Bank Account 030601 |
        | balance | 1000                |
        Then var 'account' is equal to object:
        | id      | @{account.id}        |
        | name    | Bank Account 030601 |
        | balance | 1000                |