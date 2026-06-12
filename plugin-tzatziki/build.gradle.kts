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
    runtimeOnly(project(":extensions:javascript"))
    runtimeOnly(project(":extensions:python"))

    intellijPlatform {
        intellijIdeaUltimate("2025.3.4")
        instrumentationTools()
        bundledPlugins(
            "com.intellij.java",
            "JUnit",
            "com.intellij.properties",
            "org.jetbrains.kotlin",
            // Bundled with IDEA Ultimate / WebStorm only — runIde sandbox needs it
            // for the JS/TS Cucumber+ integration (see extensions/javascript).
            "JavaScript",
        )
        plugins(
            "gherkin:${versions["gherkin"]}",
            "cucumber-java:${versions["cucumberJava"]}",
            "cucumber-javascript:${versions["cucumberJavascript"]}",
            "org.intellij.scala:${versions["scala"]}",
            "PsiViewer:${versions["psiViewer"]}",
            // Python for the behave sample under sample/rich-example/python/.
            // BOTH are required in this Gradle-provisioned sandbox: PythonCore
            // provides the `com.intellij.modules.python` module that Pythonid (the Pro
            // layer) depends on — removing PythonCore makes Pythonid fail to load
            // entirely. Note: JetBrains' NATIVE behave (intellij.python.gherkin) still
            // won't activate here because the Pro module `intellij.python` needs
            // `intellij.python.pyramid`, which is missing from the Gradle artifact —
            // independent of the dual install. That's why the cucumber-python plugin
            // (co-loaded below) provides the behave resolution/run in this sandbox.
            "PythonCore:${versions["pythonCore"]}",
            "Pythonid:${versions["python"]}",
        )
        pluginVerifier()
        zipSigner()

        // MCP Companion — auto-installed into the runIde sandbox for debug introspection
        // (get_psi_tree, project-aware tools, etc.). Resolves to the latest local build
        // under sibling project ../mcp-intellij-all/. Skipped silently if not built yet.
        val mcpDist = rootProject.file("../../mcp-intellij-all/build/distributions")
        if (mcpDist.isDirectory) {
            mcpDist.listFiles { f -> f.name.startsWith("mcp-intellij-all-") && f.name.endsWith(".zip") }
                ?.maxByOrNull { it.name }
                ?.let { localPlugin(it) }
        }

        // Co-load the independent "Cucumber for Python" plugin into the SAME
        // runIde sandbox, so it can be tested in the already-configured behave
        // project (sample/rich-example/python) without spinning up a second
        // sandbox + re-selecting the Python interpreter. It stays a separate
        // plugin (own id io.nimbly.cucumber.python) — this only co-installs it.
        // It provides the Gherkin<->behave resolution/run in this sandbox (the
        // native intellij.python.gherkin module can't load here — see note above).
        //
        // NOTE: pull the PUBLISHED 1.0.2 from the Marketplace, NOT localPlugin(project(...)).
        // This C+ sandbox runs on 2025.3 (253), but the cucumber-python module is now at 1.2.0
        // (sinceBuild=262, compiled against the 262 API) — building & loading it here would be
        // incompatible on 253. 1.0.2 is the 253-compatible line (1.0.x→253, 1.1.x→261, 1.2.x→262).
        plugins("io.nimbly.cucumber.python:1.0.2")
    }
}

configurations.all {
    // Drop the legacy `xml-apis` (org.w3c.dom) JAR — IntelliJ already ships a copy and
    // having two on the classpath leads to LinkageError on PDF export.
    // DO NOT also exclude xml-apis-ext: it provides `org.w3c.dom.svg.SVGDocument` and the
    // rest of the W3C SVG DOM interfaces that Batik's batik-anim / batik-svg-dom
    // implementations extend. Neither the JDK nor IntelliJ ships these (only the base
    // org.w3c.dom is in java.xml), so excluding xml-apis-ext breaks multi-feature PDF
    // export with `Cannot load class org.apache.batik.anim.dom.SVGOMDocument` (#96).
    exclude("xml-apis", "xml-apis")
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
            // Lower bound — keep the floor we declare in sinceBuild reachable.
            ide("IU", "2025.3.3")
            // Latest EAP — reproduce locally the Marketplace report against 2026.2 EAP
            // (IU-262.4852.50 broke 21.5.0 with CucumberStepHelper.getExtensionCount removal).
                ide("IU", "262.7132.23")
        }
        // Hide the handful of internal-API usages we've deliberately accepted —
        // see verifier-ignored-problems.txt for the per-problem rationale.
        freeArgs = listOf(
            "-ignored-problems",
            file("verifier-ignored-problems.txt").absolutePath,
        )
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

// Bake the plugin version into a generated Kotlin constant so runtime code can
// read it WITHOUT touching `PluginManager.findEnabledPlugin(PluginId)` — which the
// Marketplace verifier flags as an internal API usage.
val generatedVersionDir = layout.buildDirectory.dir("generated/source/pluginVersion/kotlin")
val generatePluginVersion by tasks.registering {
    val v = project.version.toString()
    val outDir = generatedVersionDir
    inputs.property("version", v)
    outputs.dir(outDir)
    doLast {
        val pkgDir = outDir.get().asFile.resolve("io/nimbly/tzatziki")
        pkgDir.mkdirs()
        pkgDir.resolve("PluginVersion.kt").writeText(
            """
            // Generated — do not edit. See build.gradle.kts:generatePluginVersion.
            package io.nimbly.tzatziki
            internal object PluginVersion { const val VALUE: String = "$v" }
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    sourceSets["main"].kotlin.srcDir(generatedVersionDir)
}
tasks.named("compileKotlin") { dependsOn(generatePluginVersion) }

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

// Second sandbox runner — launches the (253-compiled) Cucumber+ plugin on a 2026.2 EAP (262) IDE,
// so we can quickly smoke-test C+ on BOTH lines from the IDE Run configs ("Run C+" = 253 via the
// default `runIde`; "Run C+ 262" = this `runIde262` task). The plugin loads on 262 because its
// untilBuild is 264.* and it has no CucumberJvmExtensionPoint signature issue (it inherits from the
// resident cucumber-java plugin). All co-installed plugins are pinned to their 262 builds; the
// bundled ones come from the IDE itself. cucumber-python is taken from the LOCAL module here
// (it's 1.2.0 / built against 262 → compatible), unlike the 253 sandbox which uses the published 1.0.2.
intellijPlatformTesting {
    runIde {
        register("runIde262") {
            type = org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaUltimate
            version = "262.7132.23"
            plugins {
                bundledPlugins(
                    "com.intellij.java",
                    "JUnit",
                    "com.intellij.properties",
                    "org.jetbrains.kotlin",
                    "JavaScript",
                )
                plugins("gherkin:262.7132.23")
                plugins("cucumber-java:262.7132.23")
                plugins("cucumber-javascript:262.7132.23")
                plugins("org.intellij.scala:2026.2.5")
                plugins("PsiViewer:2026.1")
                plugins("PythonCore:262.7132.23")
                plugins("Pythonid:262.7132.23")
                // cucumber-python 1.2.0 (262) — the local module is compatible on this 262 runner.
                // (register{} exposes localPlugin(ProjectDependency), not the Project overload.)
                localPlugin(dependencyFactory.create(project(":cucumber-python")))
            }
        }
    }
}
