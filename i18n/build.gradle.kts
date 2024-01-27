plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
    id("org.jetbrains.intellij") version "1.13.1"
}

group = "io.nimbly.i18n"
version = "1.0.0"

val notes by extra {"""
       <b>Please kindly report any problem... and Rate &amp; Review this plugin !</b><br/>
       <br/>
       Change notes :
       <ul> 
         <li><b>1.0.0</b> Initial version</li>
       </ul>
      """

    /**
     * Supports of markdown
     * - https://github.com/commonmark/commonmark-java
     * - https://github.com/vsch/flexmark-java
     */
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

}

tasks {
    tasks {
        withType<JavaCompile> {
            sourceCompatibility = "11"
            targetCompatibility = "11"
        }
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.jvmTarget = "11"
        }
    }
    buildSearchableOptions {
        enabled = false
    }
    jar {
        archiveBaseName.set(rootProject.name + "-" + project.name)
        exclude("META-INF/*") // Workaround for runPluginVerifier duplicate plugins...
    }

    patchPluginXml {
        sinceBuild.set("203")
        untilBuild.set("241.*")

        changeNotes.set(notes)
    }

    buildSearchableOptions {
        enabled = false
    }

    instrumentedJar {
        exclude("META-INF/*") // Workaround for runPluginVerifier duplicate plugins...
    }

    runPluginVerifier {
        ideVersions.set(
            listOf("IU-2023.3.5"))
    }

    publishPlugin {
        val t = "perm:aG1heGltZQ==.OTItOTI5Nw==.zJ37fiKDe5cwNCAN4tib1IvEtIuOis"
        token.set(t)
    }
}