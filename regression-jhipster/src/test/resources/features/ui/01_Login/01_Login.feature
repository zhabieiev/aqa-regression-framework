@ui
Feature: Login

  @smoke
  Scenario: User signs in with valid credentials
    Given ui user opens the login page
    When ui user signs in with credentials:
      | username | password |
      | user     | user     |
    Then ui user is authenticated

  Scenario: Administrator signs in
    Given ui user opens the login page
    When ui user signs in with credentials:
      | username | password |
      | admin    | admin    |
    Then ui user is authenticated

  Scenario: User cannot sign in with an invalid password
    Given ui user opens the login page
    When ui user signs in expecting failure:
      | username | password         |
      | user     | invalid-password |
    Then ui authentication error is displayed