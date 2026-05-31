@PyBackend
Feature: Calculator (Python / behave)
  Python BDD sandbox — step definitions implemented with behave.

  Used to probe whether IntelliJ IDEA Ultimate (with the Python plugin) resolves
  Gherkin steps to their behave step definitions, and how far Cucumber+ could
  extend that — breakpoint sync, gutter "usages" marker, test-tree decoration.

  @Adding
  Scenario: Adding two numbers
    Given a calculator with value 10
    When I add 5
    Then the result is 15

  @Multiplying
  Scenario: Multiplying two numbers
    Given a calculator with value 4
    When I multiply by 3
    Then the result is 12

  Scenario Outline: Dividing several operands
    Given a calculator with value <start>
    When I divide by <by>
    Then the result is <expected>

    Examples:
      | start | by | expected |
      | 100   | 4  | 25       |
      | 81    | 9  | 9        |
      | 1000  | 8  | 125      |
