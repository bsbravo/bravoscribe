Feature: Tags

  Background:
    Given I am logged in as "bruno@email.com"

  Scenario: Create a tag
    When I create a tag named "learning"
    Then I receive status 201

  Scenario: Cannot create a tag longer than 50 characters
    When I create a tag with a name of 51 characters
    Then I receive status 400

  Scenario: List tags returns all user tags alphabetically
    Given I have created tags "work" and "family"
    When I list tags
    Then I receive status 200
    And the tags are "family" and "work" in alphabetical order

  Scenario: Delete tag removes it from entries
    Given I have a tag "work" assigned to an entry
    When I delete that tag
    Then I receive status 204
    And the entry no longer shows the "work" tag

  Scenario: Cannot add more than 10 tags to an entry
    Given I have 10 tags
    When I create an entry with all 10 tags plus one more
    Then I receive status 400
