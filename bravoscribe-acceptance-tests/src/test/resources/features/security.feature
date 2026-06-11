Feature: Cross-service security rules

  Background:
    Given all services are running

  Scenario: Unauthenticated requests are rejected
    When I call GET /api/journal/entries without a token
    Then I receive status 401
    When I call GET /api/users/me without a token
    Then I receive status 401

  Scenario: User cannot access another user's journal entries
    Given user A is registered and has a journal entry
    And user B is registered and logged in
    When user B calls GET /api/journal/entries for user A's entry ID
    Then user B receives status 404

  Scenario: Expired token is rejected
    Given I am logged in and my access token has expired
    When I call GET /api/journal/entries with the expired token
    Then I receive status 401
