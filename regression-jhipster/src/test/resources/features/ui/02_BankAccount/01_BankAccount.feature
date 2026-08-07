@ui
Feature: Bank account management

  Background:
    Given ui user opens the login page
    When ui user signs in with credentials:
      | username | password |
      | admin    | admin    |
    Then ui user is authenticated

  @hybrid
  Scenario: Administrator creates a bank account through UI
    Given api user deletes bank account by name:
      | path:name | UI Created Bank Account 1 |
    And ui user opens the bank accounts page
    When ui user creates a bank account:
      | name                      | balance | user  |
      | UI Created Bank Account 1 | 1000    | admin |
    Then ui bank account is displayed:
      | name                      | balance |
      | UI Created Bank Account 1 | 1000    |

  @hybrid
  Scenario: Administrator deletes a bank account through UI
    Given api user creates new bank account and saves to 'account':
      | name    | UI Bank Account For Deletion |
      | balance | 2000                         |
    And ui user opens the bank accounts page
    Then ui bank account is displayed:
      | name                         | balance |
      | UI Bank Account For Deletion | 2000    |
    And ui user deletes bank account "UI Bank Account For Deletion"
    Then ui bank account "UI Bank Account For Deletion" is not displayed
