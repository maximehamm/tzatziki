Feature: Cocktail Ordering

  **This is Nice date !**
  - Revenue was off the chart
  - Profits were [higher than ever](https://www.google.com).
  - *Everything* is going according to **plan**. [Google](https://www.google.com)

  ![Chat](images/chart.svg)
  <img src='images/candles.png' style='width:40px; position:absolute; top:0px; left:330px'/>

  Scenario: Creating an empty order
    Given Romeo who wants to buy a drink
    When an order is declared for Juliette
    Then there is 0 cocktails in the order
    And Romeo pays his order
    Given I have the following books in the store
      | The Devil in the White City          | Erik Larson |
      | The Lion, the Witch and the Wardrobe | C.S. Lewis  |
      | In the Garden of Beasts              | Erik Larson |

  Scenario Outline: Sending a message with an order
    When  an order is declared for <to>
    And  a message saying "<message>" is added
    Then the ticket must say "<expected>"

    Examples:
      | to       | message       | expected                            |
      | Juliette | Wanna chat?   | From Romeo to Juliette: Wanna chat? |
      | Juliette | Wanna lunch ? | From Romeo to Jerry: Hei!           |
      | Jerry    | Hei!          | Nothing                                   |



