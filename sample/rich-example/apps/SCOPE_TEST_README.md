# Test case for #104 — mono-module multi-app

This `apps` module is a deliberate **mono-module** Java project containing two
"apps" segregated only by directory:

```
apps/
├── apps.iml                    ← single IntelliJ module
└── src/                        ← single source root → both apps share the classpath
    ├── A/
    │   ├── .cucumber-scope     ← scope anchor (forces the boundary at apps/src/A/)
    │   ├── StepDefsA.java
    │   ├── A.feature
    │   └── RunCukesA.java
    └── B/
        ├── .cucumber-scope
        ├── StepDefsB.java
        ├── B.feature
        └── RunCukesB.java
```

Both `StepDefsA` and `StepDefsB` declare:

```java
@Given("Place a {int}-drink order")
```

Without Cucumber+'s scope filter, IntelliJ's standard Gherkin resolver sees
both → `Cmd+Click` shows an ambiguous popup. **This is the exact #104 scenario.**

---

## How to switch the scope filter

- **Toolbar** of the Cucumber+ tool window → "Auto-scope step indexing per app"
  (filter icon).
- **Settings** → Tools → Cucumber+ → "Auto-scope step indexing per app folder".

Both invalidate the resolve cache live (no IDE restart required).

---

## Test matrix

| # | Action                                     | AUTO ON                       | AUTO OFF                       |
|---|--------------------------------------------|-------------------------------|--------------------------------|
| 1 | Cmd+Click on the homonym from `A.feature`  | jumps directly to `StepDefsA` | popup with **both** A and B    |
| 2 | Cmd+Click on the homonym from `B.feature`  | jumps directly to `StepDefsB` | popup with **both** A and B    |
| 3 | Completion in `A.feature` (`Ctrl+Space`)   | only A's steps shown          | A's + B's steps shown          |
| 4 | Completion in `B.feature`                  | only B's steps shown          | A's + B's steps shown          |
| 5 | Cmd+Click on a UNIQUE step (`the customer is "…"` in A) | A's def | A's def (no homonym to filter) |
| 6 | Find Usages from `StepDefsA.placeAnOrder` (gutter "Used by N scenarios") | only A.feature usages | A.feature + B.feature usages |
| 7 | Run `RunCukesA` (right-click → Run)        | passes                        | passes — runtime is unaffected by IDE scope |
| 8 | Run `RunCukesB`                            | passes                        | passes                         |

> The scope filter only affects **IDE behavior** (resolve, completion, Find Usages).
> It does **not** change Cucumber's runtime resolution — that's purely classpath-driven.

---

## Edge case — overriding the natural anchor

The walk-up algorithm tries markers in this order:

1. `.cucumber-scope`
2. `package.json`
3. `pom.xml`
4. `build.gradle.kts` / `build.gradle`

The `.cucumber-scope` files at `apps/src/A/` and `apps/src/B/` force the anchor
to be the app folder itself (otherwise the walk would reach `rich-example/build.gradle`
at the project root → no useful filter).

To experiment:
- Delete `apps/src/A/.cucumber-scope` → without a marker, the walk-up finds the
  project's root `build.gradle` → entire project is the scope → AUTO ON behaves
  like AUTO OFF for `A.feature`.
- Re-create it → filter restored.

---

## Edge case — balloon notification

When AUTO is ON and a step still resolves to multiple candidates, a one-shot
balloon notification suggests dropping a `.cucumber-scope` file. To re-trigger:

1. Open `.idea/workspace.xml`.
2. Find `<component name="ProjectCucumberState">`.
3. Delete the `<option name="stepScopeBalloonShown" />` line (or set value `false`).
4. Re-trigger an ambiguous Cmd+Click.

---

## Additional behaviors potentially impacted by the scope (to verify)

These features may need to adapt when the scope is on:

- **Cucumber+ tool window — Features tree**: currently shows ALL features in
  the project. Should it filter by scope too? *(open question)*
- **Tag completion** in the Filter panel: should it list tags from the current
  scope only? *(open question)*
- **Step line markers** (the "Used by N scenarios" gutter on step defs):
  already integrated — uses `StepScope.searchScopeFor` in
  `findStepUsages` (PsiUtils.kt).
- **Breakpoint synchronization** (Gherkin ↔ Java): unaffected (one-to-one
  navigation).
- **PDF export**: unaffected.

If during your testing you spot a behavior that "leaks" across apps, file a
finding so we can add it to the scope filter.
