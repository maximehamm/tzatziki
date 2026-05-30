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
