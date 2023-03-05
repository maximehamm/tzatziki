plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
    id("org.jetbrains.intellij") version "1.13.1"
}

val versions: Map<String, String> by rootProject.extra
val notes: String by rootProject.extra

dependencies {
    implementation(project(":common"))

    implementation("com.openhtmltopdf:openhtmltopdf-core:1.0.8")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.8")
    implementation("com.openhtmltopdf:openhtmltopdf-java2d:1.0.8")
    implementation("com.openhtmltopdf:openhtmltopdf-svg-support:1.0.8")

    implementation("org.freemarker:freemarker:2.3.23")
    implementation("com.github.rjeschke:txtmark:0.13")
    implementation("io.cucumber:tag-expressions:4.1.0")

    runtimeOnly(project(":extensions:java-cucumber"))
    runtimeOnly(project(":extensions:kotlin"))
    runtimeOnly(project(":extensions:scala"))
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set(versions["intellij-version"])

    plugins.set(listOf(
        "Gherkin:${versions["gherkin"]}",
        "Kotlin",
        "org.intellij.intelliLang",
        "java",
        "JUnit",
        "cucumber-java:${versions["cucumberJava"]}",
//        "JavaScriptLanguage",
        //"cucumber-javascript:${versions.cucumberJava
        //"JavaScriptDebugger",
        "org.intellij.scala:${versions["scala"]}",
        //"com.github.danielwegener.cucumber-scala:202
        "com.intellij.properties:${versions["properties"]}",
        "PsiViewer:${versions["psiViewer"]}",
    ))
}

tasks {

    patchPluginXml {
        sinceBuild.set("203")
        untilBuild.set("232.*")

        changeNotes.set(notes)
    }

    buildSearchableOptions {
        enabled = false
    }

    jar {
        archiveBaseName.set(rootProject.name)
    }

    instrumentedJar {
        exclude("META-INF/*") // Workaround for runPluginVerifier duplicate plugins...
    }

    runPluginVerifier {
        ideVersions.set(
            listOf("IU-2022.3.1"))
    }

    //signPlugin {
    //    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    //    privateKey.set(System.getenv("PRIVATE_KEY"))
    //    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    //}

    publishPlugin {
        token.set(System.getenv("ORG_GRADLE_PROJECT_intellijPublishToken"))
        // token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
