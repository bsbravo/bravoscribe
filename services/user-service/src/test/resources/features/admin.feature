Feature: Admin Endpoints

  Scenario: Non-admin cannot access user list
    Given a registered user with email "user@test.com" and password "password123"
    When I log in with email "user@test.com" and password "password123"
    Then the response status is 200
    And I am authenticated
    When I get the user list
    Then the response status is 403

  Scenario: Unauthenticated request to user list returns 401
    When I get the user list without authentication
    Then the response status is 401

  Scenario: Admin can list users returns paginated results
    Given an admin user with email "admin@test.com" and password "password123"
    When I log in with email "admin@test.com" and password "password123"
    Then the response status is 200
    And I am authenticated
    When I get the user list
    Then the response status is 200
    And the response contains a list of users

  Scenario: Admin deactivates a user and the user loses access
    When I register a user with email "victim@test.com" and password "password123"
    Then the response status is 201
    And I save the returned user id
    And an admin user with email "admin2@test.com" and password "password123"
    When I log in with email "admin2@test.com" and password "password123"
    Then the response status is 200
    And I am authenticated
    When I deactivate the saved user
    Then the response status is 204
    When I log in with email "victim@test.com" and password "password123"
    Then the response status is 401
