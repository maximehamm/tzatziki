# Running TypeScript cucumber-js step definitions

Cucumber+ supports step definitions written in **TypeScript** (`.ts`) the same
way it does JavaScript — breakpoints, Gherkin ⇄ step-def sync, the gutter
"usages" marker and the test-tree decorations all work.

There is **one setup step** that is *not* specific to Cucumber+: Node.js can't
execute `.ts` files directly, so cucumber-js needs a TypeScript loader. This is
standard cucumber-js + Node behaviour, independent of the IDE.

## Make Node load TypeScript

Install the loader:

```bash
npm install --save-dev ts-node typescript
```

Then make cucumber-js run under it, in **either** of these ways:

### A. From a run configuration in the IDE (recommended)

In the **Cucumber.js** run configuration, set **Node options** to:

```
-r ts-node/register
```

> The IDE's cucumber-js runner passes its own `--require` flags and does **not**
> read `requireModule` from `cucumber.cjs`, so the loader has to be registered
> via Node options here rather than in the cucumber config file.

A ready-made example ships with the sample project:
`sample/rich-example/javascript/.idea/runConfigurations/Calculator_feature.xml`.

### B. From the command line

A `cucumber.cjs` at the project root works when you run `cucumber-js` yourself:

```js
module.exports = {
  default: {
    requireModule: ['ts-node/register'],
    require: [
      'features/step_definitions/**/*.js',
      'features/step_definitions/**/*.ts',
    ],
    paths: ['features/**/*.feature'],
  },
};
```

## Verify

`./node_modules/.bin/cucumber-js` should report your `.ts` scenarios as passing
(not "Undefined"). Once the steps resolve, all Cucumber+ features light up on
the `.ts` files.
