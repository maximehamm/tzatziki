Feature: Calculator
  Cucumber+ JS/TS sandbox — mixed JavaScript + TypeScript step definitions.

  Drop a breakpoint on the body of any step-def (calculator.js / calculator.ts)
  and validate that:
   * the gutter icon becomes the green Cucumber+ disc (promotion → tzatziki.cucumber.code)
   * the matching Gherkin step also gets a green dot in the gutter (sync)
   * Goto Definition from the step jumps to the JS / TS function
   * the test tree shows step parameters in grey italic ("Clara", 22, …)

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
