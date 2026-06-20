Feature: Password Reset

  Scenario: Request password reset for existing email returns 204
    Given a registered user with email "resetme@test.com" and password "password123"
    When I request a password reset for "resetme@test.com"
    Then the response status is 204

  Scenario: Request password reset for unknown email also returns 204 (anti-enumeration)
    When I request a password reset for "nobody@nowhere.com"
    Then the response status is 204

  Scenario: Confirm password reset with invalid token returns 400
    When I confirm a password reset with token "invalid-token" and new password "newPassword1"
    Then the response status is 400
