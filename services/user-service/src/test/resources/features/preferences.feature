Feature: User Preferences

  Background:
    Given a registered user with email "prefs@test.com" and password "password123"
    And I log in with email "prefs@test.com" and password "password123"
    And I am authenticated

  Scenario: Update preferences sets reminderTime and weeklySummaryEnabled
    When I update my preferences with reminderTime "21:00" and weeklySummaryEnabled true
    Then the response status is 200
    And the preference reminderTime is "21:00"
    And the preference weeklySummaryEnabled is true

  Scenario: Get my profile returns user data after preferences are updated
    When I update my preferences with reminderTime "08:00" and weeklySummaryEnabled false
    Then the response status is 200
    When I get my profile
    Then the response status is 200
    And the response email is "prefs@test.com"
