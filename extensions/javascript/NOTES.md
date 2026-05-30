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
- **[REVISIT LATER] cucumber-js step-def stub index goes stale after sandbox
  restart.** `CucumberJavaScriptExtension.loadStepsFor` returns an empty list
  until the user runs `File → Invalidate Caches`. Current workaround:
  `JsCucumberIndexRefresher` (project activity) requests a re-index of step-def
  files at startup. This is a HACK the user explicitly wants revisited — root
  cause is the cucumber-js stub cache surviving a plugin classloader swap; find
  a cleaner trigger (or confirm end users never hit it since they don't rebuild
  the plugin jar mid-session) and consider removing `JsCucumberIndexRefresher`.
- **[REVISIT LATER] TypeScript step-defs need `-r ts-node/register` passed to
  Node.** `cucumber.cjs` config alone is ignored by the IntelliJ cucumber-js
  run config — it passes its own `--require` flags that don't pick up our
  `requireModule`. Current workaround: a hand-crafted
  `.idea/runConfigurations/*.xml` template for the sample with
  `node-options = -r ts-node/register`. Users would have to replicate this by
  hand — investigate auto-injecting the flag (run-config extension?) or
  documenting it, so TS step-defs run out of the box.
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
- ~~**Test tree: @tag on JS / TS scenario / outline / feature suites.**~~ DONE
  (user-confirmed). `resolveFeatureVFile` uses `proxy.getLocation` first; a
  PSI/name-prefix `CUCUMBER_IS_STEP_KEY` distinguishes scenarios from steps;
  feature-node tags read off the `GherkinFeature` when the location URL has no
  `:NN`; outline-iteration tags rendered on the example node.
- **[AWAITING USER TEST] Test tree: `#N` ordinal on Scenario Outline iterations
  (JS / TS).** Simpler than synthesizing intermediate `Example #N` tree nodes
  (the proxy hierarchy is owned by the cucumber-js runner): the styled renderer
  prefixes each outline-iteration node with its `#N` ordinal
  (`CUCUMBER_EXAMPLE_INDEX_KEY`, the 1-based data-row position computed in
  `readExampleRowData`). NOT yet confirmed by the user.
- ~~**Test tree: strip noisy `Scenario: ` / `Step: ` prefixes (JS only).**~~
  DONE (user-confirmed). `TzCucumberSuiteNameDecorator.stripRunnerPrefix` strips
  the runner's fixed English prefix via `setPresentableName`; its return value
  also drives the reliable step/scenario classification.
- **[AWAITING USER TEST] "N usages" gutter icon on JS / TS step-defs.**
  `JsTzatzikiUsagesMarker` (`codeInsight.lineMarkerProvider` for `JavaScript` /
  `TypeScript`, registered in `plugin-withJavaScript.xml`) extends the shared
  `TzStepsUsagesMarker`. Anchors the marker on the `Given`/`When`/`Then` callee
  identifier of a step-def call, resolves the Gherkin steps via
  `Tzatziki.findSteps(vfile, offset)` (reusing `JsTzatzikiExtensionPoint`'s
  reverse-search), and calls `buildMarker(token, steps)`. NOT yet confirmed by
  the user.
- **[AWAITING USER TEST] No gutter progression bar during JS / TS debug runs.**
  Extracted the bar-drawing into the public
  `paintCucumberProgression(project, vfile, lineStart, lineEnd, isExample)`
  helper in `TzRunCucumberListener.kt`; `TzNodeExecutionTrackerListener.update`
  now calls it on every step / example-row SMTRunner event, anchoring the bar
  at the enclosing scenario/outline header (`scenarioHeaderLine0`) down to the
  current line. Previous guides are dropped on each paint so the bar rewinds.
  Cleanup still rides on `TzExecutionCucumberListener.processTerminated`
  (generic ExecutionListener, fires for JS runs too).

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
