# TODO — Tests d'intégration : synchronisation des breakpoints Cucumber+

> À coder **plus tard**. Stratégie choisie : **intégration bout-en-bout** (vrais breakpoints +
> vrai `TzBreakpointListener` asynchrone, attente via `PlatformTestUtil.waitWithEventsDispatching`).
> Harnais : `JavaCodeInsightFixtureTestCase` (voir `AbstractJavaTestCase` / `AbstractKotlinTestCase`
> / `AbstractJavascriptTestCase`). Logique sous test : `TzBreakpointListener` (refreshGherkin /
> refreshCode) + `TzPartialMutePresentation`.

## Matrice à couvrir

Pour **chaque langage** : **Java**, **Kotlin**, **JavaScript/TS**

Pour **chaque action** : **création**, **suppression**, **mute**, **unmute**

Pour **chaque côté initiateur** : action initiée **côté Gherkin (GK)** ou **côté code (J/K/JS)**

Pour **chaque contexte** : **avec BP frère** (impl partagée = plusieurs steps Gherkin → même
step definition) ou **sans BP frère** (référence unique)

## Comportements attendus à asserter

### Création
- **GK → code** : crée le BP code. **Ne propage PAS** de BP Gherkin sur les steps frères
  (impl partagée). Avec scenario outline : crée les BP d'examples.
- **Code → GK** : propage un BP Gherkin sur **tous** les steps mappés (frères inclus).

### Suppression
- **GK → code (réf unique)** : supprime le BP code.
- **GK → code (frères encore breakpointés)** : **conserve** le BP code (un autre step le référence).
- **Code → GK** : supprime les BP Gherkin liés.

### Mute / Unmute (état `enabled`)
- **GK → code** : le BP code = `OR(états des steps liés)` :
  - au moins un step actif → BP code **enabled** ;
  - tous mutés → BP code **disabled** (icône « cercle rouge » natif).
  - Le flip côté code **ne doit PAS** re-propager sur les steps frères (suppression via
    `pendingGherkinStateChange` / `fromGherkinChange`).
- **Mute un step parmi plusieurs (impl partagée)** → step seul muté, **frères inchangés**,
  BP code reste enabled.
- **Unmute un step depuis l'état tous-mutés** → step seul réactivé, **frères restent mutés**,
  BP code repasse enabled.
- **Code → GK** : mute/unmute du BP code **propage** à **tous** les steps Gherkin mappés.

### Icône « partielle » (half : demi-disque rouge + demi-anneau rouge + disque vert)
- Affichée quand les steps liés sont en état **mixte** (au moins un actif ET au moins un muté).
- Retirée (icône normale / disabled) quand l'état redevient **uniforme**.
- **Au démarrage** (`TzPartialMuteStartup`) : recalculée pour des BP restaurés du workspace.
- Vérifier **Java/Kotlin ET JavaScript** (régression historique : offset de résolution
  `findSteps` — utiliser l'offset du *statement* `codeElement.first.textRange.startOffset`,
  pas `getLineStartOffset`, sinon JS/TS ne résout pas le `JSCallExpression`).

## Points d'attention techniques
- Le listener est **asynchrone** (debounce 150 ms POOLED_THREAD + `executeOnPooledThread` +
  `ReadAction.nonBlocking` + `invokeLater`). Attendre la **condition attendue**, pas un délai fixe.
- Le listener est branché via `<projectListeners>` (topic `XBreakpointListener`) → actif en fixture.
- Setup step-defs : `setupForJava()` ajoute les libs cucumber ; voir `DeprecatedJavaTests` pour le
  pattern `configure(...)` (code) + `feature(...)`.
- Impl partagée = deux steps Gherkin avec un texte matchant la **même** regex/step-def.

## Liens code
- `plugin-tzatziki/.../breakpoints/TzBreakpointListener.kt` (refreshGherkin / refreshGherkinStep / refreshCode)
- `plugin-tzatziki/.../breakpoints/TzPartialMutePresentation.kt` (recompute / apply / startup)
- `extensions/javascript/.../JsTzatzikiExtensionPoint.kt` (findStepsAndBreakpoints / findBestPositionToAddBreakpoint)
