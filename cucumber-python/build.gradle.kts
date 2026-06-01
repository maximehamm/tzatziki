plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

val versions: Map<String, String> by rootProject.extra

// Independent plugin — its own version (NOT the inherited Cucumber+ 22.0.0).
version = "1.0.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2025.3.4")
        instrumentationTools()
        pluginVerifier()
        zipSigner()

        // gherkin → the base cucumber framework (AbstractCucumberExtension,
        // AbstractStepDefinition, BDDFrameworkType, the cucumberJvmExtensionPoint EP).
        plugins("gherkin:${versions["gherkin"]}")
        // PythonCore → the Python PSI (com.jetbrains.python.psi.*) we read to find
        // behave @given/@when/@then step defs. Pythonid (Pro) pulled too so the
        // runIde sandbox matches a real IDEA Ultimate + Python setup.
        plugins("PythonCore:${versions["pythonCore"]}")
        plugins("Pythonid:${versions["python"]}")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "253.*"
        }
        changeNotes = """
            <ul>
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
    buildSearchableOptions = false
}

kotlin {
    jvmToolchain(21)
}
