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
        intellijIdeaUltimate("2025.3.4")
        instrumentationTools()
        bundledPlugin("com.intellij.java")
        plugins(
            "org.intellij.scala:${versions["scala"]}",
            "gherkin:${versions["gherkin"]}",
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
