Feature: Calculator
  Mixed JS + TS step definitions — used to validate that Cucumber+ can:
   * place a `tzatziki.cucumber.code` breakpoint on a JS / TS step-def body line
   * jump between a Gherkin step and the corresponding JS / TS function
   * surface the run gutter on a Scenario Outline example

  @JsBackend
  Scenario: Adding two numbers (JavaScript step defs)
    Given a calculator with value 10
    When I add 5
    Then the result is 15

  @TsBackend
  Scenario: Multiplying two numbers (TypeScript step defs)
    Given a typed calculator with value 4
    When I multiply by 3
    Then the typed result is 12

  @TsBackend
  Scenario Outline: Typed division with several operands
    Given a typed calculator with value <start>
    When I divide by <by>
    Then the typed result is <expected>

    Examples:
      | start | by | expected |
      | 100   | 4  | 25       |
      | 81    | 9  | 9        |
      | 1000  | 8  | 125      |
