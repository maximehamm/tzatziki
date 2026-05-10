Feature: #104 step scope — App B

  Scenario: Order a drink for app B
    Given the supplier is "WorldDrinks"
    And Place a 3-drink order
    Then the order is registered for app B
