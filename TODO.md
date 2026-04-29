# TODO

## Add row and column insertion from the table popup (not urgent)

Currently the table popup (accessible by hovering the table frame borders) offers
`@header:` actions and shift up/down/left/right. It should also offer:

- **Insert row above / below** — inserts a blank formatted row at the hovered position
- **Insert column left / right** — inserts a blank formatted column

These can reuse the existing `format()` logic. Low priority.

---

## Rounded corners on table borders

Currently the horizontal and vertical border lines are drawn by independent per-line
renderers (`TableBorderRenderer`, `TableVerticalLineRenderer`). To draw rounded corners,
a single table-level renderer would need to know the full table extent (first row top Y,
last row bottom Y, first/last pipe X positions) and draw the whole frame in one pass using
`g2.draw(RoundRectangle2D.Float(..., arcW, arcH))`.

Requires refactoring from per-line renderers to one global renderer per table.

---

## Implement visual framing of Gherkin tables (like Markdown rendering)

Add a visual mechanism similar to what `.md` files do for Markdown tables: render
Gherkin `| … |` tables with visible cell borders and optional column alignment,
directly in the editor (custom `EditorCustomElementRenderer` or `Annotator` approach).

Sample table to play with (6 columns × 10 rows):

| Prénom    | Âge | Nom        | Ville         | Pays   | Score |
|-----------|-----|------------|---------------|--------|-------|
| Alice     | 28  | Martin     | Paris         | France | 92    |
| Bob       | 34  | Dupont     | Lyon          | France | 87    |
| Clara     | 22  | Rossi      | Rome          | Italie | 75    |
| David     | 41  | Müller     | Berlin        | Allemagne | 88 |
| Eva       | 30  | García     | Madrid        | Espagne | 95  |
| François  | 27  | Bernard    | Bordeaux      | France | 60    |
| Grace     | 35  | Kim        | Séoul         | Corée  | 78    |
| Hugo      | 29  | Ferreira   | Lisbonne      | Portugal | 82  |
| Isabelle  | 45  | Dubois     | Bruxelles     | Belgique | 91  |
| Julien    | 23  | Leroy      | Nantes        | France | 70    |

Equivalent Gherkin — form 1 (`Examples:` in a `Scenario Outline`):

```gherkin
Feature: Scoring

  Scenario Outline: Check score for <Prénom>
    Given the user "<Prénom>" "<Nom>" is <Âge> years old
    And lives in "<Ville>", "<Pays>"
    Then their score should be <Score>

    Examples:
      | Prénom   | Âge | Nom      | Ville     | Pays      | Score |
      | Alice    | 28  | Martin   | Paris     | France    | 92    |
      | Bob      | 34  | Dupont   | Lyon      | France    | 87    |
      | Clara    | 22  | Rossi    | Rome      | Italie    | 75    |
      | David    | 41  | Müller   | Berlin    | Allemagne | 88    |
      | Eva      | 30  | García   | Madrid    | Espagne   | 95    |
      | François | 27  | Bernard  | Bordeaux  | France    | 60    |
      | Grace    | 35  | Kim      | Séoul     | Corée     | 78    |
      | Hugo     | 29  | Ferreira | Lisbonne  | Portugal  | 82    |
      | Isabelle | 45  | Dubois   | Bruxelles | Belgique  | 91    |
      | Julien   | 23  | Leroy    | Nantes    | France    | 70    |
```

Equivalent Gherkin — form 2 (`DataTable` in a single step):

```gherkin
Feature: Scoring

  Scenario: Check scores for all users
    Given the following users and scores:
      | Prénom    | Âge | Nom        | Ville         | Pays      | Score |
      | Alice     | 28  | Martin     | Paris         | France    | 92    |
      | Bob       | 34  | Dupont     | Lyon          | France    | 87    |
      | Clara     | 22  | Rossi      | Rome          | Italie    | 75    |
      | David     | 41  | Müller     | Berlin        | Allemagne | 88    |
      | Eva       | 30  | García     | Madrid        | Espagne   | 95    |
      | François  | 27  | Bernard    | Bordeaux      | France    | 60    |
      | Grace     | 35  | Kim        | Séoul         | Corée     | 78    |
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
```

---

## créer un algorithme pour détecter si la première ligne d'un tableau est un entête
  
  Si le tableau est précédés de "Exemples"
    - 100% si le tableau est précédés de "Exemples"
  Sinon les réponses vraies permettent d'augmenter la probabilité d'être sur un entête :
    - si toutes les cellules de la première ligne sans renseignées
    - si le format de toutes les cellules de l'entête est alphabétique ou Alpha numérique
    - si le format de certaines cellules est Alpha numérique alors que les valeurs dessous dans la colonne sont numérique ou vide
    - d'autres idées Claude ?

## Make the plugin gracefully handle missing `cucumber-java` plugin

When the user installs Cucumber+ in an IDE that has Java enabled but the
`cucumber-java` marketplace plugin not installed, the `plugin-withJava.xml`
optional descriptor still loads (its only `<depends>` are
`com.intellij.modules.java` + `cucumber-java`, but optional descriptors don't
auto-install missing dependencies). At first project open the platform tries
to instantiate `TzCucumberJavaRunConfigurationProducer`, which extends
`org.jetbrains.plugins.cucumber.java.run.CucumberJavaFeatureRunConfigurationProducer`,
and crashes with:

```
SEVERE c.i.o.e.i.ExtensionPointImpl - Cannot create extension
  (class=io.nimbly.tzatziki.TzCucumberJavaRunConfigurationProducer)
Caused by: NoClassDefFoundError:
  org/jetbrains/plugins/cucumber/java/run/CucumberJavaFeatureRunConfigurationProducer
```

User has to manually install `cucumber-java` to make Cucumber+ work — but the
plugin should fail soft.

Options to investigate:
- Split `plugin-withJava.xml` so the `<runConfigurationProducer>`
  registration is conditional on `cucumber-java` being present. Maybe via a
  separate optional descriptor that only loads when both
  `com.intellij.modules.java` AND `cucumber-java` are loaded.
- Lazily resolve the cucumber-java parent class via reflection in the
  producer, so missing classes are tolerated.
- Document that `cucumber-java` is a hard prerequisite, and surface the
  requirement in the plugin description.
