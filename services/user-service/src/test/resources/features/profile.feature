Feature: User Profile

  Background:
    Given a registered user with email "profile@test.com" and password "password123"
    And I log in with email "profile@test.com" and password "password123"
    And I am authenticated

  Scenario: Get my profile returns user information
    When I get my profile
    Then the response status is 200
    And the response email is "profile@test.com"

  Scenario: Update profile changes name only
    When I update my name to "New Name"
    Then the response status is 200
    And the response name is "New Name"
    And the response email is "profile@test.com"

  Scenario: Update profile returns 422 when name is blank
    When I update my name to ""
    Then the response status is 400
