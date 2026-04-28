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
    implementation(project(":extensions:java-cucumber"))
    implementation(project(":plugin-tzatziki"))

    intellijPlatform {
        intellijIdeaUltimate("2025.3.4")
        instrumentationTools()
        bundledPlugins(
            "com.intellij.java",
            "org.jetbrains.kotlin",
        )
        plugins(
            "gherkin:${versions["gherkin"]}",
            "cucumber-java:${versions["cucumberJava"]}",
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
