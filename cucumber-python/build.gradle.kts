plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

val versions: Map<String, String> by rootProject.extra

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
    }
    buildSearchableOptions = false
}

kotlin {
    jvmToolchain(21)
}
