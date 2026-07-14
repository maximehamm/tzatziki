@demo @rider
Feature: Cucumber+ smoke test in Rider
  Basic Cucumber+ features that must work even in an IDE without the Java plugin
  (syntax highlighting, table formatting/editing, folding, tool window, PDF export).

  Background:
    Given the Cucumber+ plugin is installed

  @table
  Scenario: Edit and format a data table
    Given the following users:
      | Prénom   | Nom      | Ville     | Âge | Pays      | Score |
      | Alice    | Martin   | Paris     | 28  | France    | 92    |
      | Bob      | Dupont   | Lyon      | 34  | France    | 87    |
      | Clara    | Rossi    | Rome      | 22  | Italie    | 75.5  |
      | David    | Müller   | Berlin    | 41  | Allemagne | 88    |
    When I add a row by pressing Enter on the last cell
    And I add a column by pressing Pipe
    Then the table stays aligned as I type

  @outline
  Scenario Outline: Check the score for a given name
    Given the user named "<Prenom>" is <Age> years old
    Then the score should be <Score>

    Examples:
      | Prenom | Age | Score |
      | Alice  | 28  | 92    |
      | Bob    | 34  | 87    |
      | Clara  | 22  | 75    |
