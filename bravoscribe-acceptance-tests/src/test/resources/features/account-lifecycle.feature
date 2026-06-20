Feature: Account lifecycle across services

  Background:
    Given all services are running

  Scenario: New user registration triggers welcome email
    When I register with email "bruno@email.com" and password "P@ssword123"
    Then I receive status 201
    And the notification service sends a welcome email to "bruno@email.com"

  Scenario: Password reset flow end to end
    Given I am registered as "bruno@email.com"
    When I request a password reset for "bruno@email.com"
    Then I receive status 204
    And a reset email is sent containing a "#token=" hash fragment link
    When I confirm the reset with a valid token and new password "NewP@ss123"
    Then I can login with the new password
    And the old password no longer works

  Scenario: Deactivated user cannot access any resource
    Given I am registered and logged in as "bruno@email.com"
    When an admin deactivates my account
    Then login returns 401
    And GET /api/journal/entries returns 401
