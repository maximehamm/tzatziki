@Help
Business Need: toto
  Business needs explanation
  - Revenue was off the chart
  - Profits were [higher than ever](https://www.google.com).
  - *Everything* is going according to **plan**. [Google](https://www.google.com)

Feature: Cocktail Ordering
  **This is Nice date !**
  - Revenue was off the chart
  - Profits were [higher than ever](https://www.google.com).
  - *Everything* is going according to **plan**. [Google](https://www.google.com)

  ![Chat](images/chart.svg)
  <img src='images/candles.png' style='width:40px; position:absolute; top:0px; left:330px'/>

  Ceci est plutot cool !

  @Production @Chrome
  Scenario: Creating an empty order
    And Romeo pays his order
    Given Romeo who wants to buy a drink
    Then there is 1 cocktails in the order

    Given I have the following books in the store
      # @header: column
      | Erik Larson | In the Garden of Beasts              |
      | Erik Larson | The Devil in the White City          |
      | C.S. Lewis  | The Lion, the Witch and the Wardrobe |

  @Advanced @Chrome
  Scenario: This is the scenario
    **This is Nice date !**
    - Revenue was off the chart
    - Profits were [higher than ever](https://www.google.com).
    - *Everything* is going according to **plan**. [Google](https://www.google.com)

  #  Then there is 2 cocktails in the order
    Given Romeo who wants to buy a drink

    Given Bidule trucs
      # @header: column
      | Color  | Yellow | Green |
      | Syntax | Ok     | No    |
      | Type   | B12    | B13   |
      | Size   | 12.5   | 15.3  |

  Scenario: Check scores for all users

    Given the following users and scores:
      # @header: row
      | Prénom   | Nom      | Ville     | Âge | Pays      | Score |
      | Alice    | Martin   | Paris     | 28  | France    | 92    |
      | Bob      | Dupont   | Lyon      | 34  | France    | 87    |
      | Clara    | Rossi    | Rome      | 22  | Italie    | 75.5  |
      # test séparartion
      | David    | Müller   | Berlin    | 41  | Allemagne | 88    |
      | Eva      | García   | Madrid    | 30  | Espagne   | 95    |
      | François | Bernard  | Bordeaux  | 27  | France    | 60    |
      | Grace    | Kim      | Séoul     | 35  | Corée     | 78    |

      # test séparartion
      # sdfsdf

      | Hugo     | Ferreira | Lisbonne  | 29  | Portugal  | 82    |
      | Isabelle | Dubois   | Bruxelles | 45  | Belgique  | 91    |
      | Julien   | @Leroy   | Nantes    | 23  | France    | 70    |

    Then Bidule trucs
    And zoup la

    @Test 
  Scenario Outline: Vérifier le score de du prénom

    Given l'utilisateur "<Prenom>" a <Age> ans
    When Bidule trucs
    Then son score devrait être <Score>

    Scenarios:
      | Age | Score | Prenom |
      | 22  | 75    | Clara  |
      | 28  | 92    | Alice  |
      | 34  | 87    | Bob    |

