Feature: Login User

  Scenario: 010101 Authenticated user
    Given api user creates new user and saves to 'result':
      | email                   | testuser010101@gmail.com |
      | password                | TestPassword010101!      |
      | passwordRepeat          | TestPassword010101!      |
      | securityQuestion.id     | 1                        |
      | securityQuestion.answer | admin                    |
    When api user authenticates and saves response to 'token':
      | email    | testuser010101@gmail.com |
      | password | TestPassword010101!      |
    Then var 'token' is equal to object:
      | authentication.token | regex:(?s:.)+ |
