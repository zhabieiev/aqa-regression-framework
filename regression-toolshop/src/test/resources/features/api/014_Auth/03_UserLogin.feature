Feature: Login User

  Scenario: 0140301 Authenticate existing user
    Given api user gets access token and saves to 'token':
      | email    | customer@practicesoftwaretesting.com |
      | password | welcome01                            |
    Then var 'token' is equal to object:
      | access_token | regex:(?s:.)+ |
      | token_type   | bearer        |
      | expires_in   | 300           |

  Scenario: 0140302
    Given api user search user and saves to 'result':
      | q | customer2@practicesoftwaretesting.com |
      | page  | 1                                     |
    Then var 'result' is equal to object:
      | current_page  | 1                                     |
      | data[0].email | customer2@practicesoftwaretesting.com |