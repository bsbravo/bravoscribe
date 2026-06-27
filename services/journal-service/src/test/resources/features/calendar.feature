Feature: Calendar — entry dates and date-based lookup

  Background:
    Given I am logged in as "bruno@email.com"

  Scenario: Get entry dates returns all dates with entries
    Given I have entries on "2026-06-10" and "2026-06-11"
    When I get entry dates for June 2026
    Then I receive status 200
    And the response contains dates "2026-06-10" and "2026-06-11"

  Scenario: Get entry by specific date
    Given I have an entry for today
    When I get the entry for today
    Then I receive status 200
    And the response contains today's entry

  Scenario: Get entry by date with no entry returns 404
    Given I have no entry for yesterday
    When I get the entry for yesterday
    Then I receive status 404
