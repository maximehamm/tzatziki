Feature: #104 step scope — App A

  Scenario: Order a drink for app A
    Given the customer is "Romeo"
    And Place a 3-drink order
    Then the order is registered for app A

    And Place a 3-drink order

