Feature: Authenticate Controller

  Scenario: 070201 User authenticates as admin
    Given api user authenticates as admin and saves headers to 'adminHeaders'
    Then var 'adminHeaders' is equal to object:
      | Authorization | regex:(.+) |

  Scenario: 070202 User authenticates as regular user
    Given api user authenticates and saves headers to 'userHeaders'
    Then var 'userHeaders' is equal to object:
      | Authorization | regex:(.+) |

  Scenario: 070203 Header validation for user authentication
    Given api user authenticates with credentials and saves headers to 'headers':
      | username | ${user.email}    |
      | password | ${user.password} |
    Then var 'headers' is equal to object:
      | Authorization | regex:(.+) |

  Scenario Outline: 070204 Token validation for user authentication
    Given api user authenticates with credentials and saves token to 'token':
      | username | <username> |
      | password | <password> |
    Then var 'token' is equal to object:
      | id_token | <id_token> |
    Examples:
      | username            | password               | id_token   |
      | ${user.admin.email} | ${user.admin.password} | regex:(.+) |
      | ${user.email}       | ${user.password}       | regex:(.+) |

  Scenario Outline: 070205 User authenticates with invalid credentials
    Given api user try to authenticate with invalid credentials and saves to 'result':
      | body:username       | <username>   |
      | body:password       | <password>   |
      | response:statusCode | <statusCode> |
    Then var 'result' is equal to object:
      | detail                    | <detail>                |
      | instance                  | /api/authenticate       |
      | status                    | <status>                |
      | title                     | <title>                 |
      | type                      | <type>                  |
      | message                   | <message>               |
      | path                      | /api/authenticate       |
      | fieldErrors[0].field      | <fieldErrorsField>      |
      | fieldErrors[0].message    | <fieldErrorsMessage>    |
      | fieldErrors[0].objectName | <fieldErrorsObjectName> |
    Examples:
      | username           | password | statusCode | detail                                                           | status | title                     | type                                                   | message          | fieldErrorsField | fieldErrorsMessage | fieldErrorsObjectName |
      |                    | password | 400        | Unexpected runtime exception                                     | 400    | Method argument not valid | https://www.jhipster.tech/problem/constraint-violation | error.validation | username         | must not be null   | loginVM               |
      | user@aqa_email.com |          | 400        | Unexpected runtime exception                                     | 400    | Method argument not valid | https://www.jhipster.tech/problem/constraint-violation | error.validation | password         | must not be null   | loginVM               |
      | user@aqa_email.com | password | 401        | User with email user@aqa_email.com was not found in the database | 401    | Unauthorized              | https://www.jhipster.tech/problem/problem-with-message | error.http.401   |                  |                    |                       |
