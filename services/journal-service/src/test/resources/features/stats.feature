Feature: Stats

  Background:
    Given I am logged in as "bruno@email.com"

  Scenario: Stats reflect current entries
    Given I have 5 entries on consecutive days
    When I request journal stats
    Then I receive status 200
    And totalEntries is 5
    And currentStreak is 5
