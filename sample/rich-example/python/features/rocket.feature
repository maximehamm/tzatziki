@PyBackend
Feature: Rocket launch (Python / behave)
  Python BDD sandbox — step definitions implemented with behave.

  Deliberately uses a domain (rocket / fuel / boosters / altitude) whose step
  wording does NOT overlap with the Java/JS samples, so Ctrl+Click resolves to
  the Python step defs and not to a same-worded step elsewhere.

  Used to probe whether IntelliJ IDEA Ultimate (with the Python plugin) resolves
  Gherkin steps to their behave step definitions, and how far Cucumber+ could
  extend that — breakpoint sync, gutter "usages" marker, test-tree decoration.

  @Fuel
  Scenario: Burning fuel during ascent
    Given a rocket loaded with 500 tons of fuel
    When I burn 120 tons during ascent
    Then the remaining fuel is 380 tons

  @Boosters
  Scenario: Jettisoning boosters
    Given a rocket fitted with 4 boosters
    When I jettison 2 boosters
    Then 2 boosters remain

  Scenario Outline: Climbing to orbit
    Given a rocket cruising at <altitude> km
    When I climb by <delta> km
    Then the altitude is <expected> km

    Examples:
      | altitude | delta | expected |
      | 100      | 50    | 150      |
      | 200      | 200   | 400      |
      | 300      | 100   | 400      |
