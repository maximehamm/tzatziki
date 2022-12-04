# Tzatziki y Cucumber

Developped by Maxime HAMM
_maxime.hamm.pro@gmail.com_

This plugin is compatible with all Jetbrain products

- `plugin` : main plugin code
- `extensions` : extensions (future use)
    - ...
- `common` : code used by plugin and extensions

## Market place location
https://plugins.jetbrains.com/plugin/16289-cucumber-to-tzatziki

## Installation
Import using gradle (Gradle should use Java 11)
- Launch task `build` from root module
- Run all tests from module `./test`

## Run
Launch task `runIde` from `plugin` module

## Get ready to publish to market place
Setup a system variable :
- Edit `~/.bash_profile` (if you're using bash)
  Then `source ~/.bash_profile`
- Add `export ORG_GRADLE_PROJECT_intellijPublishToken='xxxx'`

_Token is known by Maxime HAMM :)_

## Prepare to publish a release
- Clean and build all
  - Clean all `Taks > build > clean`
  - Build all `Taks > build > build`
  - Build plugin `plugin > Tasks > intellij > buildplugin`
- Run tests in `./test` module
- Verify compatibility using task `runPluginVerifier` from `plugin` module

## Publishing a new release
- Upgrade version in `./build.gradle`
- Update change note in `./plugin/build.gradle`
- Publish to marketplace 
  - Using task `plugin > Tasks > intellij > publishPlugin`

*Many thanks to Pierre-Michel BRET for his contribution, the great PDF generator, allowing to realize a complete layout with page numbers, summary, paragraphs management, etc.*

Enjoy !


