Feature: User Authentication

  Scenario: Register a new user
    When I register a user with email "register@test.com" and password "password123"
    Then the response status is 201
    And the response contains an access token

  Scenario: Cannot register with duplicate email
    Given a registered user with email "dup@test.com" and password "password123"
    When I register a user with email "dup@test.com" and password "password123"
    Then the response status is 409

  Scenario: Login with valid credentials returns token and cookie
    Given a registered user with email "login@test.com" and password "password123"
    When I log in with email "login@test.com" and password "password123"
    Then the response status is 200
    And the response contains an access token
    And the response has an httpOnly cookie named "refreshToken"

  Scenario: Login with wrong password returns 401
    Given a registered user with email "wrongpass@test.com" and password "password123"
    When I log in with email "wrongpass@test.com" and password "wrongpassword"
    Then the response status is 401

  Scenario: Login with non-existent email returns 401
    When I log in with email "nobody@test.com" and password "password123"
    Then the response status is 401

  Scenario: Refresh token returns new access token and rotates cookie
    Given a registered user with email "refresh@test.com" and password "password123"
    When I log in with email "refresh@test.com" and password "password123"
    Then the response status is 200
    When I refresh the access token
    Then the response status is 200
    And the response contains an access token
    And the response has an httpOnly cookie named "refreshToken"

  Scenario: Refresh with no cookie returns 401
    When I refresh the access token
    Then the response status is 401

  Scenario: Logout clears the refresh token cookie
    Given a registered user with email "logout@test.com" and password "password123"
    When I log in with email "logout@test.com" and password "password123"
    When I log out
    Then the response status is 204

  Scenario: Logout invalidates the refresh token
    Given a registered user with email "logout-revoke@test.com" and password "password123"
    When I log in with email "logout-revoke@test.com" and password "password123"
    Then the response status is 200
    And I save my current refresh token
    When I log out
    Then the response status is 204
    When I refresh with the saved refresh token
    Then the response status is 401
