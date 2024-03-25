Feature: Tzatziki y Cucumber
  Scenario Outline: Auto formating
    When I enter any character into <NAF> or <Ready> or <Details>
    Then The Cucumber table is formatted :
      | XXX | Ready |
      | 78  | Yes   |
      | 79  | No    |
      | XXX | Ready |
    Examples:
      | XXX | Ready |
      | 78  | Yes   |
      | 79  | No    |
      | XXX | Ready |


    Then FInished !
