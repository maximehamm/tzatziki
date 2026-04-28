plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "io.nimbly.translation"
version = "11.2.0"

val notes by extra {"""
       <b>Please kindly report any problem... and Rate &amp; Review this plugin !</b><br/>
       <br/>
       Change notes :
       <ul>
         <li><b>11.2.0</b> IntelliJ IDEA 2026.1 compatibility</li>
         <li><b>11.1.0</b> IntelliJ IDEA 2025.2 compatibility</li>
         <li><b>11.0.0</b> IntelliJ IDEA 2025.1.1 compatibility</li>
         <li><b>10.0.0</b> Adding support of Baidu API</li>
         <li><b>9.0.0</b> Adding support of ChatGPT API</li>
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

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":i18n"))

    intellijPlatform {
        intellijIdeaUltimate("2025.3.4")
        instrumentationTools()
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
        archiveBaseName.set("translation")
    }
}
