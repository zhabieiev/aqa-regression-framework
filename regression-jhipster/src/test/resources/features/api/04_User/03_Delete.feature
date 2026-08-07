Feature: Delete User

  Scenario: 040301 Delete user
    Given api user creates new user and saves to 'user':
      | login       | user_040301               |
      | email       | user_040301@aqa.email.com |
      | authorities | [ROLE_USER]               |
    When api user deletes user:
      | login | @{user.login} |
    And api user tries to get user and saves to 'result':
      | path:login          | @{user.login} |
      | response:statusCode | 404           |
    Then var 'result' is equal to object:
      | detail   | 404 NOT_FOUND                                          |
      | instance | /api/admin/users/@{user.login}                         |
      | status   | 404                                                    |
      | title    | Not Found                                              |
      | type     | https://www.jhipster.tech/problem/problem-with-message |
      | message  | error.http.404                                         |
      | path     | /api/admin/users/@{user.login}                         |