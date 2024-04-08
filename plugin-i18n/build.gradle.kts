plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
    id("org.jetbrains.intellij") version "1.13.1"
}

group = "io.nimbly.translation"
version = "8.0.0"

val notes by extra {"""
       <b>Please kindly report any problem... and Rate &amp; Review this plugin !</b><br/>
       <br/>
       Change notes :
       <ul> 
         <li><b>8.0.0</b> Adding support of Microsoft Translator API</li>
         <li><b>7.0.0</b> Adding support of DeepL translation API</li>
         <li><b>6.0.0</b> Apply translation by clicking on hint</li>
         <li><b>5.0.0</b> Smart replacement everywhere using refactoring !</li>
         <li><b>4.0.0</b> Escaping text depending on format : HTML, JSON, CSV, XML, PROPERTIES</li>
         <li><b>3.0.0</b> Commit dialog translation </li>
         <li><b>2.0.0</b> Dictionary definitions</li>
         <li><b>1.2.0</b> Support of camel case</li>
         <li><b>1.1.0</b> Display translation inlined in text</li>
         <li><b>1.0.0</b> Initial version</li>
       </ul>
      """
}

val versions by extra {
    mapOf(
        "intellij-version" to "IU-2022.3.1",
    )
}

intellij {
    version.set(versions["intellij-version"])
}

dependencies {
    implementation(project(":i18n"))
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

        // Check build number here : https://www.jetbrains.com/idea/download/other.html
        sinceBuild.set("222.4554.10")    // 2021.2.4
        untilBuild.set("241.*")

        changeNotes.set(notes)
    }

    buildSearchableOptions {
        enabled = false
    }

    jar {
        archiveBaseName.set("translation")
    }
    instrumentedJar {
        // exclude("META-INF/*") // Workaround for runPluginVerifier duplicate plugins...
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

