Feature: Journal export across services

  Background:
    Given all services are running
    And I am registered and logged in

  Scenario: Export returns valid zip with markdown entries
    Given I have 3 journal entries
    When I call GET /api/journal/entries/export
    Then I receive a zip file
    And the zip contains a markdown file starting with "# Bravoscribe Export"
    And the markdown contains all 3 entries

  Scenario: Export with date range
    Given I have entries spanning multiple months
    When I call GET /api/journal/entries/export?from=2026-01-01&to=2026-03-31
    Then the zip contains only entries within that range

  Scenario: Export with no entries returns 404
    Given I have no journal entries
    When I call GET /api/journal/entries/export
    Then I receive status 404
