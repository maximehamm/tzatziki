# TODO

## Divers 

  - corrige les actions insert/delete... qd il y a des commentaires intercallés, ne pas les perdre



## Make the plugin gracefully handle missing `cucumber-java` plugin

When the user installs Cucumber+ in an IDE that has Java enabled but the
`cucumber-java` marketplace plugin not installed, the `plugin-withJava.xml`
optional descriptor still loads (its only `<depends>` are
`com.intellij.modules.java` + `cucumber-java`, but optional descriptors don't
auto-install missing dependencies). At first project open the platform tries
to instantiate `TzCucumberJavaRunConfigurationProducer`, which extends
`org.jetbrains.plugins.cucumber.java.run.CucumberJavaFeatureRunConfigurationProducer`,
and crashes with:

```
SEVERE c.i.o.e.i.ExtensionPointImpl - Cannot create extension
  (class=io.nimbly.tzatziki.TzCucumberJavaRunConfigurationProducer)
Caused by: NoClassDefFoundError:
  org/jetbrains/plugins/cucumber/java/run/CucumberJavaFeatureRunConfigurationProducer
```

User has to manually install `cucumber-java` to make Cucumber+ work — but the
plugin should fail soft.

Options to investigate:
- Split `plugin-withJava.xml` so the `<runConfigurationProducer>`
  registration is conditional on `cucumber-java` being present. Maybe via a
  separate optional descriptor that only loads when both
  `com.intellij.modules.java` AND `cucumber-java` are loaded.
- Lazily resolve the cucumber-java parent class via reflection in the
  producer, so missing classes are tolerated.
- Document that `cucumber-java` is a hard prerequisite, and surface the
  requirement in the plugin description.
