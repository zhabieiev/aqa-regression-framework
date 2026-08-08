@api
Feature: Get User

  Scenario: 040101 Get user
    Given api user creates new user and saves to 'user':
      | login       | user_040101               |
      | email       | user_040101@aqa.email.com |
      | authorities | [ROLE_USER]                |
    When api user gets user and saves to 'result':
      | login | @{user.login} |
    Then var 'result' is equal to object:
      | activated        | true                                               |
      | createdBy        | ${admin}                                           |
      | createdDate      | regex:\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z |
      | email            | user_040101@aqa.email.com                         |
      | firstName        |                                                    |
      | id               | @{user.id}                                         |
      | imageUrl         |                                                    |
      | langKey          | en                                                 |
      | lastModifiedBy   | ${admin}                                           |
      | lastModifiedDate | regex:\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z |
      | lastName         |                                                    |
      | login            | @{user.login}                                      |

  Scenario: 040102 Get user with invalid data
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
