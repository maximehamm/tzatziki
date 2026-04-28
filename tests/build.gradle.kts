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
    testImplementation(project(":plugin-tzatziki"))
    testImplementation(project(":common"))
    testImplementation(project(":extensions:java-cucumber"))
    testImplementation(project(":extensions:kotlin"))

    testImplementation("org.jetbrains.kotlin:kotlin-stdlib")
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation("org.apache.logging.log4j:log4j-api:2.14.1")
    testImplementation("org.apache.logging.log4j:log4j-core:2.14.1")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    intellijPlatform {
        intellijIdeaUltimate("2025.3.4")
        instrumentationTools()
        bundledPlugins(
            "com.intellij.java",
            "JUnit",
            "com.intellij.properties",
            "org.jetbrains.kotlin",
            "com.intellij.modules.json",
        )
        bundledModule("intellij.platform.langInjection")
        bundledModule("intellij.spellchecker")
        plugins(
            "gherkin:${versions["gherkin"]}",
            "cucumber-java:${versions["cucumberJava"]}",
        )
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Plugin.Java)
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
