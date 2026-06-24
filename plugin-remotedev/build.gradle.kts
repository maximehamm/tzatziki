plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "io.nimbly.remotedev"
version = "1.0.0"

val notes by extra {"""
       <b>Please kindly report any problem... and Rate &amp; Review this plugin !</b><br/>
       <br/>
       Change notes :
       <ul>
         <li><b>1.0.0</b> Initial version — restores Windows host integration when working over WSL Remote Development: <b>Open in Explorer</b>, <b>Open in Windows native application</b>, and <b>Copy Windows path</b>.</li>
       </ul>
      """
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2025.3.4")
        pluginVerifier()
        zipSigner()
    }
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
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        archiveBaseName.set("remotedev")
    }
}
