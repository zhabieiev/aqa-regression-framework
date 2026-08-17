Feature: Allure attachment fixture

  Scenario: A no-browser Cucumber scenario emits an attachment
    Given an isolated Allure attachment fixture
    Then the configured Allure result directory is available
