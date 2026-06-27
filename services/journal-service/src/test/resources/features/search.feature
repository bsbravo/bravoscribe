Feature: Search and pagination

  Background:
    Given I am logged in as "bruno@email.com"

  Scenario: Search returns matching entries
    Given I have entries with content "Azure decisions" and "Spring Boot"
    When I search entries with q "azure"
    Then I receive status 200
    And I receive only the entry containing "Azure"

  Scenario: Search with q longer than 200 chars returns 400
    When I search entries with a 201-character query
    Then I receive status 400

  Scenario: Page size is capped at 100
    Given I have 150 entries
    When I list entries with page size 200
    Then I receive status 200
    And I receive at most 100 entries
