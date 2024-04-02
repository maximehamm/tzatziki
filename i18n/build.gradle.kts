plugins {
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
    id("org.jetbrains.intellij") version "1.13.1"
}

val versions: Map<String, String> by rootProject.extra

intellij {
    version.set(versions["intellij-version"])
}

dependencies {
    implementation("javazoom:jlayer:1.0.1")
    implementation("org.apache.commons:commons-text:1.11.0")
    implementation("org.unbescape:unbescape:1.1.6.RELEASE")
    implementation("org.apache.commons:commons-csv:1.10.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
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
    }
}