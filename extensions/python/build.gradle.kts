plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform.module") version "2.5.0"
}

val versions: Map<String, String> by rootProject.extra

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":plugin-tzatziki"))

    intellijPlatform {
        // The Python plugin (Pythonid == the "Python" plugin in IDEA Ultimate) is
        // NOT bundled with IntelliJ IDEA Community. The corresponding
        // `<depends optional="true">com.intellij.modules.python` entry in plugin.xml
        // keeps Cucumber+ usable without it.
        intellijIdeaUltimate("2025.3.4")
        instrumentationTools()
        plugins(
            "gherkin:${versions["gherkin"]}",
            // com.jetbrains.python.psi.* (PyFunction / PyDecorator / …) lives in the
            // Python *community* base (PythonCore). Pythonid layers on top of it.
            // This is a COMPILE-ONLY dependency for this module — it does NOT affect
            // the runIde sandbox plugin set (configured in :plugin-tzatziki, which
            // intentionally installs Pythonid alone).
            "PythonCore:${versions["pythonCore"]}",
            "Pythonid:${versions["python"]}",
        )
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        archiveBaseName.set(rootProject.name + "-" + project.name)
    }
}
