# Cucumber+ JavaScript / TypeScript step-def support — branch notes

Status: **work in progress, do NOT merge to master yet.**

## What works

| Feature | JS | TS |
|---|---|---|
| Code → Gherkin sync (add BP) | ✅ | ✅ |
| Gherkin → code sync (add BP) | ✅ | ✅ |
| Mute / enable propagation (both directions) | ✅ | ✅ |
| BP promotion to `tzatziki.cucumber.code.javascript` | ✅ | ✅ |
| Green Cucumber+ gutter icon (unverified state) | ✅ | ✅ |
| BP actually fires at debug time | ✅ | partial |

## Known limitations

- **Cucumber+ icon flashes back to native red once the JS debugger verifies the BP.**
  Cause: `XLineBreakpointType` doesn't expose a `createBreakpoint` hook the way
  `JavaLineBreakpointType` does, so we can't override `getVerifiedIcon()` on the
  instance — the JS debugger paints its own icon for the verified state. Platform
  limitation; would need a custom `XBreakpointPresentationProvider` or upstream API
  change.
- **No "stop only on the example row I clicked" filtering for Scenario Outlines.**
  `TzRunCodeListener` (Java-only — gated on `JavaDebugProcess` and run-config type
  `"Cucumber Java"`) implements that filtering for the JVM debugger. Need a JS
  twin (`TzRunNodeListener`) that hooks the Node / Chrome DAP debug session and
  parses the cucumber-js stdout to track the current example. ~150 LoC + investigation.
- **cucumber-js step-def stub index goes stale after sandbox restart.**
  Dev-only annoyance: `CucumberJavaScriptExtension.loadStepsFor` returns an empty
  list until the user runs `File → Invalidate Caches`. `JsCucumberIndexRefresher`
  (project activity) requests a re-index of every `.js / .ts` file under the
  project root at startup as a workaround. End users don't rebuild the plugin
  jar mid-session, so they shouldn't see this.
- **TypeScript step-defs need `-r ts-node/register` passed to Node.**
  `cucumber.cjs` config alone is ignored by the IntelliJ cucumber-js plugin run
  config — it passes its own `--require` flags that don't pick up our
  `requireModule`. We ship a hand-crafted `.idea/runConfigurations/*.xml`
  template for the sample. Users would need similar setup.
- **Zero real-world testing.** Only the sample under
  `sample/rich-example/javascript/` has been exercised. No coverage on
  `@cucumber/cucumber` v7 / v8 / v11, ESM-only projects, monorepos, projects
  using BDD-frameworks layered on top of cucumber-js, etc.
- **Perf hardening still TODO.** The JVM side took multiple rounds of issues
  (#122, #124, #124-bis) before we added the right caches + debouncing to keep
  the EDT responsive during heavy debug sessions. The JS path mirrors the same
  hot loops without any of that hardening — places to revisit when we have
  signals from real users:
    * `JsTzatzikiExtensionPoint.findStepsAndBreakpoints` walks **every**
      `.feature` file in the project scope on every `breakpointAdded /
      Changed / Removed` event. Mirror the per-`(vfile, offset, PsiModCount)`
      cache that already exists in `TzBreakpointListener.findStepsAndBreakpointsCached`.
    * `TzBreakpointListener` already coalesces JVM-side bursts via a 600ms
      `Alarm` — we delegate JS promotion to it so we inherit the debounce, but
      worth verifying once a JS user reports perf issues.
    * `TzNodeExecutionTrackerListener.update` runs a `ReadAction.compute` per
      SMTRunner event (suite + test). On large features with many scenarios /
      examples this is N read-actions per run. Consider batching or caching
      the PSI lookups (e.g. cache the "is data row?" verdict per `(vfileUrl, line)`).
    * `TzRunNodeListener.handlePause` schedules a 250ms `Thread.sleep` on a
      pooled thread for every pause to wait for the SMTRunner events to catch
      up — could starve the pool if many BPs fire fast. Replace with a proper
      `Alarm` or a condition wait.
    * `JsCucumberIndexRefresher` calls `FileBasedIndex.requestReindex` on every
      JS/TS file under the project root at startup. Cheap on the sample, but on
      a 100k-file monorepo this would dominate startup time. Add a guard that
      only requests reindex for files matching `features/step_definitions/**`
      or similar before shipping.
- **Test tree: @tag suffixes missing on JS / TS suites.**
  The `TzCucumberSuiteNameDecorator` reads scenario tags via `readScenarioTags`
  → `resolveFeatureVFile(locationUrl)`. cucumber-js emits relative location URLs
  (`file:///features/calculator.feature:24`) which our resolver can't map to a
  real `VirtualFile`, so the PSI lookup bails and no tags get attached. Same
  underlying fix as TODO #11 (cache + smarter resolution): teach
  `resolveFeatureVFile` to use `proxy.getLocation(project, scope).virtualFile`
  (the same trick `TzNodeExecutionTrackerListener.update` already uses), or
  thread the resolved `VirtualFile` through the decoration path so we don't
  re-walk it for every suite.
- **Test tree node names have noisy `Scenario: ` / `Step: ` prefixes (JS only).**
  cucumber-js emits SMTRunner suites named `Scenario: Adding two numbers
  (JavaScript step defs)` and tests named `Step: a calculator with value 10` —
  whereas cucumber-jvm emits clean names without the prefix. Strip these via
  `TzCucumberSuiteNameDecorator` / the styled renderer so the run/debug tree
  reads consistently across languages.
- **No gutter progression bar during JS / TS debug runs.** The vertical
  "currently-running step" bar painted in the `.feature` margin (via
  `TzExecutionCucumberListener.processStarting` → `MyGutterRenderer` →
  `highlightProgression`) is driven by the same stdout `onTextAvailable`
  channel that doesn't fire for cucumber-js. The tracker fields are now
  populated through `TzNodeExecutionTrackerListener` (SMTRunner-based) but the
  bar drawing code never re-runs. To restore the visual: extract the
  `MarkupModel.addRangeHighlighterAndChangeAttributes(...) → MyGutterRenderer`
  block into a helper, then call it from the SMTRunner adapter every time
  `update()` advances `lineNumber` / `exampleLine`. Also need to remove
  previous highlighters at iteration boundaries so the bar rewinds correctly
  for outline rows.

## Architecture sketch

```
extensions/javascript/
├── build.gradle.kts                                # bundledPlugins(JavaScript, JavaScriptDebugger) + cucumber-javascript marketplace plugin
├── src/main/kotlin/io/nimbly/tzatziki/
│   ├── JsTzatzikiExtensionPoint.kt                 # TzatzikiExtensionPoint impl
│   │     - findStepsAndBreakpoints (walks JSCallExpression → reverse-search Gherkin steps)
│   │     - findBestPositionToAddBreakpoint (first executable line of callback body)
│   │     - canRunStep (any def is JavaScriptStepDefinition)
│   │     - promoteToCucumberType (creates tzatziki.cucumber.code.javascript)
│   │     - isDeprecated (JSDoc `@deprecated`)
│   ├── JsCucumberIndexRefresher.kt                 # ProjectActivity that re-indexes JS/TS at startup
│   └── breakpoints/
│       └── TzCucumberJsBreakpointType.kt           # XLineBreakpointType<JavaScriptLineBreakpointProperties>
plugin-tzatziki/src/main/resources/META-INF/
└── plugin-withJavaScript.xml                       # <depends optional="true">JavaScript</depends> + extensions
```

The JVM path is unchanged. The two parallel code paths share
`TzatzikiExtensionPoint` and `TzBreakpointListener` which dispatch by file
extension. Two key places to keep in sync if you touch either:

- `TzBreakpointListener.refreshCode` — JVM uses `BreakpointsUtil.promoteToCucumberType`,
  non-JVM dispatches to the extension's own `promoteToCucumberType`.
- `BreakpointsUtil.ensureCucumberCodeBreakpoint` — picks the right breakpoint
  type by id (`tzatziki.cucumber.code` vs `tzatziki.cucumber.code.javascript`)
  based on file extension.
