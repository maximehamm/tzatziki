plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
    id("org.jetbrains.intellij") version "1.13.1"
}

val versions: Map<String, String> by rootProject.extra

intellij {
    version.set(versions["intellij-version"])
    plugins.set(listOf(
        "Gherkin:${versions["gherkin"]}",
        "Kotlin",
        "org.intellij.intelliLang",
        "java",
        "JUnit",
        "cucumber-java:${versions["cucumberJava"]}",
        //"JavaScriptLanguage",
        //"cucumber-javascript:${versions.cucumberJava
        //"JavaScriptDebugger",
        "org.intellij.scala:${versions["scala"]}",
        //"com.github.danielwegener.cucumber-scala:202
        "com.intellij.properties:${versions["properties"]}",
        "PsiViewer:${versions["psiViewer"]}",
    ))
}

dependencies {
    testImplementation(project(":plugin"))
    testImplementation(project(":common"))

    testImplementation("org.jetbrains.kotlin:kotlin-stdlib")
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation("org.apache.logging.log4j:log4j-api:2.14.1")
    testImplementation("org.apache.logging.log4j:log4j-core:2.14.1")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine");
}

tasks {
    buildSearchableOptions {
        enabled = false
    }
    jar {
        archiveBaseName.set(rootProject.name + "-" + project.name)
    }
}