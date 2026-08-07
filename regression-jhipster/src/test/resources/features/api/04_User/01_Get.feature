Feature: Get User

  Scenario: 4010101 Get user by login
    Given api user creates new user and saves to 'user':
      | login       | user_4010101               |
      | email       | user_4010101@aqa.email.com |
      | authorities | [ROLE_USER]                |
    When api user gets user and saves to 'result':
      | login | @{user.login} |
    Then var 'result' is equal to object:
      | activated        | true                                               |
      | createdBy        | ${admin}                                           |
      | createdDate      | regex:\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z |
      | email            | user_4010101@aqa.email.com                         |
      | firstName        |                                                    |
      | id               | @{user.id}                                         |
      | imageUrl         |                                                    |
      | langKey          | en                                                 |
      | lastModifiedBy   | ${admin}                                           |
      | lastModifiedDate | regex:\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z |
      | lastName         |                                                    |
      | login            | @{user.login}                                      |

  Scenario: 4010102 Get user by login with invalid data
    When api user tries to get user and saves to 'result':
      | path:login          | invalid_login |
      | response:statusCode | 404           |
    Then var 'result' is equal to object:
      | detail   | 404 NOT_FOUND                                          |
      | instance | /api/admin/users/invalid_login                         |
      | status   | 404                                                    |
      | title    | Not Found                                              |
      | type     | https://www.jhipster.tech/problem/problem-with-message |
      | message  | error.http.404                                         |
      | path     | /api/admin/users/invalid_login                         |