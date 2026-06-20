Feature: Password Change

  Background:
    Given a registered user with email "pwchange@test.com" and password "oldPassword1"
    And I log in with email "pwchange@test.com" and password "oldPassword1"
    And I am authenticated

  Scenario: Change password with correct current password succeeds
    When I change my password from "oldPassword1" to "newPassword1"
    Then the response status is 204

  Scenario: Change password with wrong current password returns 422
    When I change my password from "wrongPassword" to "newPassword1"
    Then the response status is 422

  Scenario: After password change, old credentials no longer work
    When I change my password from "oldPassword1" to "changedPwd1"
    Then the response status is 204
    When I log in with email "pwchange@test.com" and password "oldPassword1"
    Then the response status is 401
    When I log in with email "pwchange@test.com" and password "changedPwd1"
    Then the response status is 200
