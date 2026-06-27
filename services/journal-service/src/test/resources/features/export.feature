Feature: Export entries as zip

  Background:
    Given I am logged in as "bruno@email.com"

  Scenario: Export returns valid zip with MD file
    Given I have 3 entries this month
    When I export entries for this month
    Then I receive status 200
    And I receive a zip file
    And the zip contains a valid markdown file
    And the markdown contains no email or name

  Scenario: Export with no entries returns 404
    When I export entries for a range with no entries
    Then I receive status 404

  Scenario: Export range exceeding 366 days returns 400
    When I export entries for a range exceeding 366 days
    Then I receive status 400
