Feature: User registration and journaling flow

  Background:
    Given all services are running

  Scenario: New user registers and writes first entry
    When I register with email "bruno@email.com" and password "P@ssword123"
    Then I receive status 201
    And I login with those credentials
    And I create a journal entry with content "My first entry"
    Then the entry is saved successfully
    And my stats show totalEntries: 1 and currentStreak: 1

  Scenario: Deactivated user loses access to their journal
    Given I am registered and logged in as "bruno@email.com"
    And I have 3 journal entries
    When an admin deactivates my account
    Then I cannot login anymore
    And my existing entries are soft-deleted

  Scenario: Password reset does not affect journal entries
    Given I am registered and logged in as "bruno@email.com"
    And I have a journal entry for today
    When I reset my password via the reset flow
    And I login with the new password
    Then my journal entry is still accessible

  Scenario: Account deactivation publishes event consumed by journal service
    Given I am registered and logged in as "bruno@email.com"
    And I have 2 journal entries
    When an admin deactivates my account via the User Service API
    Then within 5 seconds all my journal entries have deleted=true in the database
    And I cannot login with my credentials anymore
