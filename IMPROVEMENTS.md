# Cucumber+ — Pistes d'amélioration

> Notes de brainstorming (2026-06-03). Beaucoup sont **ancrées sur des constats concrets**
> faits en testant C+ 23.0.1 sur 253 / 261 / 262 et en lisant le code.
> À reprendre / prioriser ensemble.

---

## 🔧 Robustesse & dette technique (constats concrets)

### 1. Support Kotlin K2 — *priorité haute, fort impact*
- **Constat** : sur **2026.1+** (IDE en mode **K2 par défaut**), l'intégration Kotlin de C+ est
  **désactivée par la plateforme**. Log observé :
  `Plugin … depends on Kotlin plugin via plugin-withKotlin.xml but the plugin is not compatible
  with the Kotlin plugin in the K2 mode. So, the plugin-withKotlin.xml was not loaded`.
- **Conséquence** : tous les utilisateurs Kotlin sur 2026.x **perdent** C+ côté Kotlin
  (résolution Gherkin↔step def, gutter « N scenarios », breakpoint-sync).
- **Piste** : déclarer le support K2 dans le plugin descriptor
  (`org.jetbrains.kotlin.supportsKotlinPluginMode supportsK2="true"`), puis auditer le code
  Kotlin (`extensions/kotlin`) pour qu'il n'utilise pas d'API K1-only (analysis API → nouvelle
  Analysis API K2). Tester sur 2026.1/2026.2.

### 2. Déporter le travail PSI hors de l'EDT
- **Constat** : sur 262, **9 `SlowOperations` SEVERE** attribuées à `Cucumber + 23.0.1`
  (la plateforme signale des opérations PSI/résolution faites en synchrone sur l'EDT).
- **Conséquence** : freezes potentiels, notifications rouges, et la plateforme **durcit** cette
  règle à chaque version (finira par lever des exceptions bloquantes).
- **Piste** : passer la résolution et les line-markers en **background read actions**
  (`ReadAction.nonBlocking { … }`), caches déjà en place (`findStepUsages`).

### 3. Réduire les usages d'API internes / dépréciées / expérimentales
- **Constat** (verifier C+ 23.0.1) : **38 usages d'API interne**, + *deprecated*, + *experimental*,
  + *scheduled for removal*. Exemples connus qui ont déjà cassé : `CucumberStepHelper.getExtensionCount`
  (retiré en 262), `refactoring.suggested.UtilsKt.getStartOffset` (interne),
  `TargetPresentationBuilder` (expérimental).
- **Conséquence** : c'est **la** source récurrente des casses cross-version → builds par-ligne d'IDE.
- **Piste** : remplacer progressivement par des API publiques ; isoler les rares incontournables
  derrière une petite couche d'abstraction + test de garde (cf. `ReflectionApiTest`).

### 4. `untilBuild = "264.*"` invalide
- **Constat** : le verifier lève un *plugin configuration defect* (« 2026.4 n'existe pas »).
- **Piste** : borne réelle (ou ouverte) pour supprimer le defect.

---

## 🧪 Tests
### 5. Étendre la couverture
- **Dé-ignorer les tests breakpoint-sync Python** (`BreakpointSyncPythonTests`, aujourd'hui
  `@Ignore` car `JavaCodeInsightFixtureTestCase` n'active pas le langage Python) → trouver/monter
  une fixture Python-aware.
- **Ajouter des tests du producer #127** : clic en-tête → pas de no-op ; numérotation « Example n° ».
- Base existante saine : breakpoint-sync **Java / Kotlin / JS** verts.

---

## ✨ Idées produit

### 6. Inspection — cohérence des paramètres d'un step
- **Idée** : pour un step Gherkin résolu vers sa step def, **vérifier que les paramètres
  correspondent** et lever une **erreur / warning** sinon.
- **Vérifications** :
  - **Nombre** : nb de paramètres du pattern (groupes regex / placeholders cucumber-expression
    `{int}`, `{word}`, `{string}`…) == nb de paramètres de la fonction/méthode de la step def,
    *hors* paramètres injectés (`context`/`self` en Python/behave, DI / `Scenario` en Java, etc.).
    Mismatch → **erreur**.
  - **Type** : si le pattern type ses paramètres (`{int}`, `{float}`, `{biginteger}`… ou regex
    typée) :
    - vérifier que la **valeur** fournie dans le step Gherkin est compatible
      (ex : pattern `{int}` mais le step écrit `abc` → warning/erreur) ;
    - et/ou que le **type du paramètre** de la fonction correspond (ex : `{int}` ↔ param `int`/`Integer`).
  - **Scenario Outline** : les `<placeholders>` → confronter aux **colonnes de la table Examples**
    (placeholder inexistant, colonne en trop, type de la cellule incompatible avec `{int}`…).
- **Niveau** : warning configurable (le type-check est best-effort selon le langage et le degré
  d'info de signature disponible).
- **Faisable** : la signature de la step def est déjà accessible via les extensions par langage
  (`*TzatzikiExtensionPoint`) ; il faut parser le cucumber-expression / la regex du pattern pour en
  extraire **arité + types**, puis comparer à la signature et aux valeurs du step.
- **Bonus** : quick-fixes — « ajouter le paramètre manquant à la step def », « corriger la valeur du
  step », « renommer/typer le paramètre ».

### 7. Rename refactoring synchronisé Gherkin ↔ step def
- Voir section dédiée ci-dessous (#8).

### 8. Per-scenario mute — autres langages
- Le runtime per-scenario mute couvre Java + (générique) pydevd/Node. À étendre à d'autres
  debuggers (Ruby, Go…) si on vise ces langages.

---

## 🔁 #8 (détaillé) — Rename refactoring synchronisé Gherkin ↔ step def

### Objectif
Renommer **le texte d'un step Gherkin** et propager automatiquement :
1. au **pattern de la step definition** (`@Given("…")` / `Given(/…/)` / `@given('…')`),
2. à **tous les steps Gherkin frères** qui matchent la même step def (pour qu'ils restent liés).
Et idéalement l'inverse (éditer le pattern → mettre à jour les steps).

### Pourquoi c'est faisable ici
- Le lien Gherkin → step def **existe déjà** : `TzCucumberStepReference.resolveToDefinitions`.
- Les **usages** (steps frères) sont déjà trouvables : `findStepUsages` (avec cache).
- Il y a déjà un **point d'extension par langage** (`TzatzikiExtensionPoint` :
  `JavaTzatzikiExtensionPoint`, `KotlinTzatzikiExtensionPoint`, `ScalaTzatzikiExtensionPoint`,
  `PyTzatzikiExtensionPoint`, JS) → on peut y ajouter une capacité
  `locatePattern()` / `rewritePattern(newText)`.

### Mécanisme IntelliJ envisagé
- Un **`RenameHandler`** déclenché par Shift+F6 sur un `GherkinStep` (PSI, donc compatible avec les
  ~70 dialectes Gherkin — on ne touche pas au mot-clé, seulement au texte).
- Il ouvre la **prévisualisation de refactoring** standard (liste des steps + la step def impactés),
  puis applique en **une transaction write action** :
  1. réécrit le **littéral** du pattern de la step def (via l'extension du langage concerné),
  2. réécrit le **texte** de tous les steps Gherkin frères.

### Déclenchement UX — **sans** Shift+F6 (essentiel : personne n'y pensera)
Il faut **suggérer le renommage pendant que l'utilisateur tape directement dans le step Gherkin**.
- **Option A — framework natif « Suggested Refactoring »** (`com.intellij.refactoring.suggested.*`,
  le moteur du popup « Refactor: update usages »). Bémols : (a) il est **centré sur la déclaration**
  → colle au sens *éditer le pattern → MAJ des steps*, mais pas au sens *éditer le step → MAJ de la
  def* (le step est un usage, pas une déclaration) ; (b) API **interne/expérimentale** → +dette
  (cf. #3, le verifier flag déjà `refactoring.suggested`).
- **Option B — suggestion maison (recommandée)**, contrôle total + 0 API interne :
  1. quand le caret **entre** sur un step → **snapshot** (texte + step def résolue + steps frères) ;
  2. à l'**édition** du step, si l'ancien texte matchait une def et que le nouveau **ne matche plus**
     → on **infère un renommage** et on **propose** :
     - une **ampoule d'intention** (Alt+Enter / bulle auto) « Propager le renommage → step def + N
       steps frères » — visible sans Shift+F6 ; *(Phase 1)*
     - ou un **popup/inlay transient** auto après stabilisation de la frappe
       (`HintManager`/`EditorNotification`), façon Suggested Refactoring. *(Phase 2)*

### Distinguer « renommage » vs « nouveau step »
Comment savoir si l'utilisateur **renomme** (garder le lien) ou **écrit un step neuf** (→ « create
step definition ») ? → le **snapshot avant édition** (ancien texte → def) tranche : ancien matchait
+ nouveau ne matche plus = **renommage candidat** → on propose ; sinon, flux normal.

### ⚠️ Perf — NE JAMAIS résoudre / chercher des usages à chaque frappe
Risque majeur de l'UX « pendant la frappe ». Principe : **séparer « suggestion dispo ? » (gratuit)
de « calculer les usages » (coûteux)**.
1. **Snapshot une seule fois à l'ENTRÉE du caret** sur le step (pas par touche) : réutiliser la
   **référence déjà résolue** par le daemon (highlighting/go-to-decl) — **ne pas forcer** un resolve.
   Stocker juste *ancien texte* + *réf. légère def*. **Pas** de recherche des frères ici. Resolve
   éventuel en **background read action** + caches existants (`findStepUsages`, résolution).
2. **Pendant la frappe** : seulement **comparaison de chaînes** courant↔snapshot → **O(1)**.
3. **Surfaçage debouncé via la passe de highlighting** (déjà throttlée, en read action background) :
   ne consulte **que le snapshot**, **aucune recherche globale**.
4. **Le coûteux (chercher tous les steps frères + preview) UNIQUEMENT à l'invocation** de l'action
   (Alt+Enter / popup), dans le refactoring standard (barre « Searching for usages… »).
   **Jamais pendant la frappe.**
- Gater aux steps **déjà résolus** par le daemon (lire l'état, ne pas le provoquer).
- Resolve pas encore dispo → **ne rien afficher** (pas d'attente/blocage) ; réapparaît à la passe
  suivante.
- Net : **coût par frappe ≈ un `String.equals`**.

### Le vrai défi : les paramètres
Les patterns ne sont pas du texte pur :
- littéral : `@Given("a shared step")` ↔ `Given a shared step` → **mapping 1:1**, facile.
- cucumber expression : `@Given("I have {int} cukes")` ↔ `I have 5 cukes`.
- regex : `@Given("I have (\\d+) cukes")`.
- placeholders d'Outline : `Given I have <count> cukes`.

→ **MVP** : ne gérer d'abord que les **steps littéraux** (sans paramètre) — rename bidirectionnel
complet. Puis étendre : ne renommer que les **segments littéraux**, en **préservant** les
`{…}` / groupes regex / `<placeholders>` (mapping token par token entre pattern et step).

### Garde-fous
- **Prévisualisation obligatoire** (une step def matchée par une regex large peut couvrir d'autres
  phrasings → ne pas casser les autres matches).
- Si plusieurs step defs matchent, ou si le pattern contient des paramètres en MVP → proposer mais
  signaler la limite (ou désactiver).
- PSI-only (jamais de regex sur mots-clés anglais — cf. dialectes Gherkin).

### Découpage proposé
1. Ajouter `rewriteStepDefinitionPattern(newText)` + `getPatternLiteralElement()` à
   `TzatzikiExtensionPoint` (impl par langage).
2. `RenameHandler` sur `GherkinStep` (littéral uniquement) → réécrit pattern + steps frères, avec
   preview. + tests.
3. Étendre aux steps paramétrés (segments littéraux), + sens inverse (depuis la step def).

---

## Priorités suggérées
**Fort ROI court terme** : **(1) Kotlin K2**, **(2) SlowOperations/EDT**, **(#127) run config Outline**.
**Moyen terme** : (3) nettoyage API internes, (5) tests, (6) inspection step def inutilisée.
**Feature ambitieuse** : (7/#8) rename synchronisé.
