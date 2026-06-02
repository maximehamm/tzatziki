plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

val versions: Map<String, String> by rootProject.extra

// Independent plugin — its own version (NOT the inherited Cucumber+ 22.0.0).
version = "1.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 1.1.x build — compiled against 2026.1 (261). isStepLikeFile/isWritableStepLikeFile
        // are 1-arg here; loadStepsFor is still 2-arg (it becomes 1-arg only at 262 → 1.2.x).
        intellijIdeaUltimate("261.22158.182")
        instrumentationTools()
        pluginVerifier()
        zipSigner()

        // gherkin → the base cucumber framework (AbstractCucumberExtension,
        // AbstractStepDefinition, BDDFrameworkType, the cucumberJvmExtensionPoint EP).
        plugins("gherkin:261.22158.182")
        // PythonCore → the Python PSI (com.jetbrains.python.psi.*) we read to find
        // behave @given/@when/@then step defs. Pythonid (Pro) pulled too so the
        // runIde sandbox matches a real IDEA Ultimate + Python setup.
        plugins("PythonCore:261.22158.277")
        plugins("Pythonid:261.22158.277")

        // Dev convenience (runIde sandbox only — NOT a published dependency): co-load the
        // latest built Cucumber+ (plugin-tzatziki) so the FULL Python experience (gutter
        // "N scenarios" + Gherkin<->PY breakpoint sync, both Cucumber+ features) is testable
        // here on 261. The Cucumber+ zip bundles all extensions (java/kotlin/scala/js/python),
        // so this gives the complete Cucumber+ experience on a 261 IDE alongside cucumber-python
        // 1.1.0 — without re-pinning the 253-bound plugin-tzatziki sandbox to 261.
        fileTree("$rootDir/plugin-tzatziki/build/distributions") { include("plugin-tzatziki-*.zip") }
            .files.maxByOrNull { it.lastModified() }
            ?.let { localPlugin(it) }
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "261.*"
        }
        changeNotes = """
            <ul>
              <li><b>1.1.0</b> — Compatibility with IntelliJ IDEA 2026.1 (build 261). (2025.3 users: stay on 1.0.2.)</li>
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
            ide(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaUltimate, "261.22158.182")
        }
    }
    buildSearchableOptions = false
}

kotlin {
    jvmToolchain(21)
}
