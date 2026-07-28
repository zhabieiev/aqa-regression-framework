Feature: Create New User

  Scenario: 020101 Create new user
    Given api user creates new user and saves to 'result':
      | email                   | testuser020101@gmail.com |
      | password                | TestPassword020101!      |
      | passwordRepeat          | TestPassword020101!      |
      | securityQuestion.id     | 1                        |
      | securityQuestion.answer | admin                    |
    Then var 'result' is equal to object:
      | status     | success                  |
      | data.email | testuser020101@gmail.com |
