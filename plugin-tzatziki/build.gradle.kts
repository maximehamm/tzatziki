plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
    id("org.jetbrains.intellij") version "1.13.1"
}

val versions: Map<String, String> by rootProject.extra
val notes: String by rootProject.extra

dependencies {
    implementation(project(":common"))
    implementation(project(":i18n"))

    implementation("com.openhtmltopdf:openhtmltopdf-core:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-java2d:1.0.10")
    implementation("com.openhtmltopdf:openhtmltopdf-svg-support:1.0.10")



    implementation("org.freemarker:freemarker:2.3.30")
    implementation("com.github.rjeschke:txtmark:0.13")
    implementation("io.cucumber:tag-expressions:4.1.0")

    runtimeOnly(project(":extensions:java-cucumber"))
    runtimeOnly(project(":extensions:kotlin"))
    runtimeOnly(project(":extensions:scala"))
}

configurations.all {
    // This is important for PDF export
    exclude("xml-apis", "xml-apis")
    exclude("xml-apis", "xml-apis-ext")
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
        "org.intellij.scala:${versions["scala"]}",
        "com.intellij.properties:${versions["properties"]}",
        "PsiViewer:${versions["psiViewer"]}",
    ))
}

tasks {

    withType<JavaCompile> {
        sourceCompatibility = "11"
        targetCompatibility = "11"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    patchPluginXml {
        sinceBuild.set("212")    // 2021.2.4
        untilBuild.set("241.*")  // 2024.EAP (241)

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

    publishPlugin {
        val t = System.getProperty("PublishToken")
        token.set(t)
    }
}

configurations.all {

    resolutionStrategy {

        // Fix for CVE-2020-11987, CVE-2019-17566, CVE-2022-41704, CVE-2022-42890
        force("org.apache.xmlgraphics:batik-parser:1.16")
        force("org.apache.xmlgraphics:batik-anim:1.16")
        force("org.apache.xmlgraphics:batik-awt-util:1.16")
        force("org.apache.xmlgraphics:batik-bridge:1.16")
        force("org.apache.xmlgraphics:batik-codec:1.16")
        force("org.apache.xmlgraphics:batik-constants:1.16")
        force("org.apache.xmlgraphics:batik-css:1.16")
        force("org.apache.xmlgraphics:batik-dom:1.16")
        force("org.apache.xmlgraphics:batik-ext:1.16")
        force("org.apache.xmlgraphics:batik-gvt:1.16")
        force("org.apache.xmlgraphics:batik-parser:1.16")
        force("org.apache.xmlgraphics:batik-script:1.16")
        force("org.apache.xmlgraphics:batik-svg-dom:1.16")
        force("org.apache.xmlgraphics:batik-transcoder:1.16")
        force("org.apache.xmlgraphics:batik-util:1.16")
    }
}