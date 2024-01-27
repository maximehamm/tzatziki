plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
    id("org.jetbrains.intellij") version "1.13.1"
}

intellij {
    version.set("IU-2021.3.1")
    plugins.set(listOf(
        "Gherkin:213.5744.223",
        "Kotlin",
        "org.intellij.intelliLang",
        "java",
        "JUnit",
        "cucumber-java:213.5744.125",
        "com.intellij.properties:213.6461.46"
    ))
}

dependencies {
    testImplementation(project(":plugin-tzatziki"))
    testImplementation(project(":common"))

    testImplementation("org.jetbrains.kotlin:kotlin-stdlib")
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.0")
    testImplementation("org.apache.logging.log4j:log4j-api:2.14.1")
    testImplementation("org.apache.logging.log4j:log4j-core:2.14.1")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine");
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }
    buildSearchableOptions {
        enabled = false
    }
    jar {
        archiveBaseName.set(rootProject.name + "-" + project.name)
    }
}