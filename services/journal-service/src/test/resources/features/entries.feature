Feature: Journal entries

  Background:
    Given I am logged in as "bruno@email.com"

  Scenario: Create entry for today
    When I create an entry with content "Had a great day" for today
    Then I receive status 201
    And the entry is returned with the correct date

  Scenario: Cannot create two entries for the same day
    Given I have an entry for today
    When I create another entry for today
    Then I receive status 409

  Scenario: Cannot create entry for a future date
    When I create an entry for tomorrow
    Then I receive status 400

  Scenario: Cannot read another user's entry
    Given another user has an entry
    When I request that entry
    Then I receive status 404

  Scenario: Cannot edit another user's entry
    Given another user has an entry
    When I update that entry
    Then I receive status 404

  Scenario: Soft delete does not appear in entry list
    Given I have an entry for today
    When I delete that entry
    Then I receive status 204
    And the entry list is empty
