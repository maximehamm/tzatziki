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
    Given Romeo who wants to buy a drink
    Then there is 0 cocktails in the order
    And Romeo pays his order
    Given I have the following books in the store
      | Erik Larson | In the Garden of Beasts              |
      | Erik Larson | The Devil in the White City          |
      | C.S. Lewis  | The Lion, the Witch and the Wardrobe |

  @Advanced @Chrome
  Scenario: This is the scenario

    Given Romeo who wants to buy a drink
      # @header: column
      | Valeur 1 | Valeur 2 |
      | Test     | Truc     |

    Given Bidule trucs
      # @header: column
      | Type   | B12    | B13   |
      | Color  | Yellow | Green |
      | Syntax | Ok     | No    |
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


  Scenario Outline: Vérifier le score de <Prénom>

    Given l'utilisateur "<Prenom>" a <Age> ans
    Then son score devrait être <Score>

    Examples:
      | Prenom | Age | Score |
      | Alice  | 28  | 92    |
      | Bob    | 34  | 87    |
      | Clara  | 22  | 75    |