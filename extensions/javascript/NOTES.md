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
- ~~**"Stop only on the example row I clicked" filtering for Scenario Outlines.**~~
  DONE (user-confirmed). `TzRunNodeListener` hooks the Node debug session
  (`XDebugSessionListener.sessionPaused`, Alarm-debounced 250ms to let the
  SMTRunner tracker catch up), and on each pause resolves the running step (via
  PSI + the JS step-def `JSCallExpression` covering the pause offset), then
  `session.resume()`s when the step / example-row has no enabled Gherkin BP.
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
    * `TzNodeExecutionTrackerListener.update` still runs `proxy.getLocation` +
      `scenarioHeaderLine0` read-actions per SMTRunner event. The "is data row?"
      verdict is now cached (`dataRowCache`, keyed by `(vfileUrl, line, modCount)`),
      but the location resolution + header lookup aren't — batch/cache those too
      on large features.
    * ~~`TzRunNodeListener.handlePause` 250ms `Thread.sleep`~~ DONE — replaced
      with a project-scoped `Alarm` (cancel-and-rearm) so concurrent pauses
      don't pile up sleeping pooled threads.
    * ~~`JsCucumberIndexRefresher` reindexes every JS/TS file at startup~~ DONE —
      now filtered to step-def files (under `step_definitions/` / `steps/` dirs
      or `*.steps.{js,ts}`), skipping `node_modules` / `build` / `dist` / dot-dirs.
- ~~**Test tree: @tag on JS / TS scenario / outline / feature suites.**~~ DONE
  (user-confirmed). `resolveFeatureVFile` uses `proxy.getLocation` first; a
  PSI/name-prefix `CUCUMBER_IS_STEP_KEY` distinguishes scenarios from steps;
  feature-node tags read off the `GherkinFeature` when the location URL has no
  `:NN`; outline-iteration tags rendered on the example node.
- ~~**Test tree: `#N` ordinal on Scenario Outline iterations (JS / TS).**~~ DONE
  (user-confirmed). Simpler than synthesizing intermediate `Example #N` tree
  nodes (the proxy hierarchy is owned by the cucumber-js runner): the styled
  renderer prefixes each outline-iteration node with a bold `#N` ordinal
  (`CUCUMBER_EXAMPLE_INDEX_KEY`, the 1-based data-row position computed in
  `readExampleRowData`); never leaks onto step child nodes.
- ~~**Test tree: strip noisy `Scenario: ` / `Step: ` prefixes (JS only).**~~
  DONE (user-confirmed). `TzCucumberSuiteNameDecorator.stripRunnerPrefix` strips
  the runner's fixed English prefix via `setPresentableName`; its return value
  also drives the reliable step/scenario classification.
- ~~**"N usages" gutter icon on JS / TS step-defs.**~~ DONE (user-confirmed).
  `JsTzatzikiUsagesMarker` (`codeInsight.lineMarkerProvider` for `JavaScript`
  ONLY — TypeScript is a JS dialect, covered via base-language lookup;
  registering both fired it twice on `.ts` → duplicate markers) extends the
  shared `TzStepsUsagesMarker`. Anchors on the `Given`/`When`/`Then` callee
  identifier, resolves steps via `Tzatziki.findSteps` (reusing
  `JsTzatzikiExtensionPoint`'s reverse-search), `buildMarker(token, steps)` →
  the same Cucumber+ popup as Java.
- ~~**Gutter progression bar during JS / TS debug runs.**~~ DONE (user-confirmed).
  Public `paintCucumberProgression(project, vfile, lineStart, lineEnd, isExample)`
  helper in `TzRunCucumberListener.kt` (lineEnd is INCLUSIVE → +1 internally so
  the bar covers the paused line). `TzNodeExecutionTrackerListener` drives it
  from SMTRunner events for RUN; `TzRunNodeListener.handlePause` repaints from
  the REAL resolved step on a kept DEBUG pause (the tracker lags the actual
  pause line). Cleanup rides on `TzExecutionCucumberListener.processTerminated`.

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
