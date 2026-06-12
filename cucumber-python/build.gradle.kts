plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

val versions: Map<String, String> by rootProject.extra

// Independent plugin — its own version (NOT the inherited Cucumber+ 22.0.0).
version = "1.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 1.2.x build — compiled against 2026.2 (262), where CucumberJvmExtensionPoint
        // also moved loadStepsFor to the 1-arg loadStepsFor(Module) (module-wide). isStepLikeFile
        // is 1-arg (as in 261). Covers 262.* only; 261 → 1.1.x, 253 → 1.0.x.
        // Compile target stays on 262.4852.50: it is Kotlin-2.2 compatible (cached) — newer 262
        // EAPs (262.7132+) bundle Kotlin metadata 2.4.0 that this project's Kotlin 2.2.0 compiler
        // cannot read. The plugin range is 262.* so the 4852-built artifact runs fine on any 262
        // IDE (incl. the non-expired 262.7132.23 launched by plugin-tzatziki:runIde262).
        intellijIdeaUltimate("262.4852.50")
        instrumentationTools()
        pluginVerifier()
        zipSigner()

        // gherkin → the base cucumber framework (AbstractCucumberExtension,
        // AbstractStepDefinition, BDDFrameworkType, the cucumberJvmExtensionPoint EP).
        plugins("gherkin:262.4852.34")
        // PythonCore → the Python PSI (com.jetbrains.python.psi.*) we read to find
        // behave @given/@when/@then step defs. Pythonid (Pro) pulled too so the
        // runIde sandbox matches a real IDEA Ultimate + Python setup.
        plugins("PythonCore:262.4852.50")
        plugins("Pythonid:262.4852.50")

        // Dev convenience (sandbox only): provision cucumber-java so the co-loaded Cucumber+
        // can light up its Java/Kotlin integration here too (its plugin-withJava.xml is gated
        // on the cucumber-java plugin since C+ 23.0.1). Lets us validate Java + Kotlin + Python
        // all at once in this single 262 sandbox. NOT a published dependency of cucumber-python.
        plugins("cucumber-java:262.4852.50")

        // Dev convenience (runIde sandbox only — NOT a published dependency): co-load the
        // latest built Cucumber+ (plugin-tzatziki) so the FULL Python experience (gutter
        // "N scenarios" + Gherkin<->PY breakpoint sync, both Cucumber+ features) is testable
        // here on 262. The Cucumber+ zip bundles all extensions (java/kotlin/scala/js/python),
        // so this gives the complete Cucumber+ experience on a 262 IDE alongside cucumber-python
        // 1.2.0 — without re-pinning the 253-bound plugin-tzatziki sandbox to 262.
        fileTree("$rootDir/plugin-tzatziki/build/distributions") { include("plugin-tzatziki-*.zip") }
            .files.maxByOrNull { it.lastModified() }
            ?.let { localPlugin(it) }
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = "262.*"
        }
        changeNotes = """
            <ul>
              <li><b>1.2.0</b> — Compatibility with IntelliJ IDEA 2026.2 (build 262). (2025.3 → 1.0.2, 2026.1 → 1.1.0.)</li>
              <li><b>1.1.0</b> — Compatibility with IntelliJ IDEA 2026.1 (build 261).</li>
              <li><b>1.0.2</b> — Refreshed plugin icon (official Python logo combined with the Cucumber+ mark).</li>
              <li><b>1.0.1</b> — Resolve the Python interpreter via the module SDK (no internal API); run a single scenario / Scenario-Outline example; cleaner test tree.</li>
              <li><b>1.0.0</b> — Initial release: Gherkin ↔ behave step resolution &amp; navigation, Run / Debug feature files, and the "Create step definition" quick-fix.</li>
            </ul>
        """.trimIndent()
    }
    // Publishing — mirrors Cucumber+ : the token is passed at invocation time via
    //   ./gradlew :cucumber-python:publishPlugin -DPublishToken=<token>
    // and the signing certificate / private key are read from the environment
    // (CERTIFICATE_CHAIN, PRIVATE_KEY, PRIVATE_KEY_PASSWORD) by the zipSigner.
    publishing {
        token = System.getProperty("PublishToken")
    }
    pluginVerification {
        ides {
            ide(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaUltimate, "262.4852.50")
        }
    }
    buildSearchableOptions = false
}

kotlin {
    jvmToolchain(21)
}
