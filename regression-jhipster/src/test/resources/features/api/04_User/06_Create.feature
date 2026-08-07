@api
Feature: Create User

  Scenario: 040601 Create user
    Given api user creates new user and saves to 'result':
      | login       | user_040601               |
      | email       | user_040601@aqa.email.com |
      | authorities | [ROLE_USER]               |
    Then var 'result' is equal to object:
      | activated        | true                                               |
      | createdBy        | ${admin}                                           |
      | createdDate      | regex:\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z |
      | email            | user_040601@aqa.email.com                          |
      | firstName        |                                                    |
      | id               | @{result.id}                                       |
      | imageUrl         |                                                    |
      | langKey          | en                                                 |
      | lastModifiedBy   | ${admin}                                           |
      | lastModifiedDate | regex:\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z |
      | lastName         |                                                    |
      | login            | user_040601                                        |

  Scenario Outline: 040602 Create user with all fields
    Given api user creates new user and saves to 'result':
      | login       | user_<id>               |
      | firstName   | FirstName_<id>          |
      | lastName    | LastName_<id>           |
      | email       | user_<id>@aqa.email.com |
      | imageUrl    | <imageUrl>              |
      | activated   | <activated>             |
      | langKey     | <langKey>               |
      | authorities | <authorities>           |
    Then var 'result' is equal to object:
      | activated        | <activated>                                        |
      | createdBy        | ${admin}                                           |
      | createdDate      | regex:\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z |
      | email            | user_<id>@aqa.email.com                            |
      | firstName        | FirstName_<id>                                     |
      | id               | @{result.id}                                       |
      | imageUrl         | <imageUrl>                                         |
      | langKey          | <langKey>                                          |
      | lastModifiedBy   | ${admin}                                           |
      | lastModifiedDate | regex:\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z |
      | lastName         | LastName_<id>                                      |
      | login            | user_<id>                                          |
    Examples:
      | id       | imageUrl                      | activated | langKey | authorities             |
      | 04060201 | https://example.com/image.jpg | true      | en      | [ROLE_USER]             |
      | 04060202 |                               |           | de      | [ROLE_USER,ROLE_ADMIN ] |
