plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

val versions: Map<String, String> by rootProject.extra
val notes: String by rootProject.extra

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

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

    intellijPlatform {
        intellijIdeaUltimate("2025.3.4")
        instrumentationTools()
        bundledPlugins(
            "com.intellij.java",
            "JUnit",
            "com.intellij.properties",
            "org.jetbrains.kotlin",
        )
        plugins(
            "gherkin:${versions["gherkin"]}",
            "cucumber-java:${versions["cucumberJava"]}",
            "org.intellij.scala:${versions["scala"]}",
            "PsiViewer:${versions["psiViewer"]}",
        )
        pluginVerifier()
        zipSigner()
    }
}

configurations.all {
    // This is important for PDF export
    exclude("xml-apis", "xml-apis")
    exclude("xml-apis", "xml-apis-ext")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253.28294"
            untilBuild = "264.*"
        }
        changeNotes = notes
    }
    pluginVerification {
        ides {
            ide("IU", "2025.3.3")
        }
    }
    publishing {
        token = System.getProperty("PublishToken")
    }
    buildSearchableOptions = false
    instrumentCode = true
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        archiveBaseName.set(rootProject.name)
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
