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
        // The JavaScript / TypeScript language plugin is only bundled with
        // IntelliJ IDEA Ultimate, WebStorm, PhpStorm, RubyMine, etc. — NOT with
        // IntelliJ IDEA Community. The corresponding `<depends optional="true">`
        // entry in plugin.xml keeps Cucumber+ usable on Community.
        intellijIdeaUltimate("2025.3.4")
        instrumentationTools()
        bundledPlugins(
            "JavaScript",
            // Provides JavaScriptBreakpointType + JavaScriptLineBreakpointProperties
            // — needed to build our TzCucumberJsBreakpointType.
            "JavaScriptDebugger",
        )
        plugins(
            "gherkin:${versions["gherkin"]}",
            "cucumber-javascript:${versions["cucumberJavascript"]}",
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
