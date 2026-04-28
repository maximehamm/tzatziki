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
    implementation("io.cucumber:tag-expressions:4.1.0")

    intellijPlatform {
        intellijIdeaUltimate("2025.3.4")
        instrumentationTools()
        plugins("gherkin:${versions["gherkin"]}")
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
