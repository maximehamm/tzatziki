Publie le plugin Cucumber+ sur le JetBrains Marketplace.

## Étape 1 — Vérification de la Change note

Lis la section `changeNotes` dans `build.gradle.kts`.

Affiche les deux premières entrées `<li>` et demande :

> **"La Change note est-elle à jour pour cette publication ?"**
> Réponds O/N, ou dicte directement le texte de la nouvelle entrée.

Attendre la réponse. Si N ou reformulation → appliquer la modification dans `build.gradle.kts`, puis continuer.

## Étape 2 — Clean global

```bash
cd /Users/maxime/Development/projects-plugins/Tzatziki && ./gradlew clean 2>&1 | tail -5
```

Arrêter si `BUILD FAILED`.

## Étape 3 — Build global

```bash
./gradlew build -x test 2>&1 | grep -E "BUILD|error:"
```

Arrêter si `BUILD FAILED` et afficher les erreurs.

## Étape 4 — Tests automatiques du module plugin-tzatziki

```bash
./gradlew :plugin-tzatziki:test 2>&1 | tail -20
```

Si des tests échouent → **stopper immédiatement** :
> ❌ Tests KO — publication annulée. Corriger les échecs avant de relancer `/publish-cucumber+`.

Afficher le résumé des tests (passés / échoués).

## Étape 5 — buildPlugin

```bash
./gradlew :plugin-tzatziki:buildPlugin 2>&1 | grep -E "BUILD|error:"
```

Arrêter si `BUILD FAILED`.

## Étape 6 — Confirmation finale avant publication

Afficher un récapitulatif :

```
Version dans build.gradle.kts : X.Y.Z
Change note (entrée de tête) : <texte>

✅ Clean      OK
✅ Build      OK
✅ Tests      OK  (N passed)
✅ buildPlugin OK
```

Demander :
> **"Tout est bon — je publie sur le Marketplace ?"**

Attendre confirmation explicite (O/oui). Tout autre réponse → stopper.

## Étape 7 — git push

```bash
cd /Users/maxime/Development/projects-plugins/Tzatziki && git push origin master 2>&1 | tail -5
```

Si le commit de version n'est pas encore créé, le faire avant de push.

## Étape 8 — publishPlugin (manuel — l'utilisateur s'en charge)

**Ne PAS lancer `publishPlugin` automatiquement.** L'utilisateur publie lui-même via Gradle/IDE.
Indiquer le ZIP prêt :

```
ZIP prêt : plugin-tzatziki/build/distributions/plugin-tzatziki-X.Y.Z.zip
À publier manuellement : ./gradlew :plugin-tzatziki:publishPlugin -DPublishToken=…
```

## Étape 9 — Résumé final

```
✅ Cucumber+ vX.Y.Z buildé, vérifié et poussé. Publication Marketplace à faire manuellement.
```
