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
    Then there is 1 cocktails in the order
    And Romeo pays his order
    Given I have the following books in the store
      | Erik Larson | The Devil in the White City          |
      | C.S. Lewis  | The Lion, the Witch and the Wardrobe |
      | Erik Larson | In the Garden of Beasts              |

  @Advanced @Chrome
  Scenario: This is the scenario
    Given Romeo who wants to buy a drink
      | Valeur 1 | Valeur 2 |

  Scenario: Check scores for all users

    Given the following users and scores:
      | Prénom    | Âge | Nom        | Ville         | Pays      | Score |
      | Alice     | 28  | Martin     | Paris         | France    | 92    |
      | Bob       | 34  | Dupont     | Lyon          | France    | 87    |
      | Clara     | 22  | Rossi      | Rome          | Italie    | 75    |
      # test séparartion
      | David     | 41  | Müller     | Berlin        | Allemagne | 88    |
      | Eva       | 30  | García     | Madrid        | Espagne   | 95    |
      | François  | 27  | Bernard    | Bordeaux      | France    | 60    |
      | Grace     | 35  | Kim        | Séoul         | Corée     | 78    |
      # test séparartion
      | Hugo      | 29  | Ferreira   | Lisbonne      | Portugal  | 82    |
      | Isabelle  | 45  | Dubois     | Bruxelles     | Belgique  | 91    |
      | Julien    | 23  | Leroy      | Nantes        | France    | 70    |


  Scenario Outline: Vérifier le score de <Prénom>
    Given l'utilisateur "<Prénom>" a <Âge> ans
    Then son score devrait être <Score>

    Examples:
      | Prénom   | Âge | Score |
      | Alice    | 28  | 92    |
      | Bob      | 34  | 87    |
      | Clara    | 22  | 75    |