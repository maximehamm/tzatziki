plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "tzatziki"

include("common")
include("i18n")

include("plugin-tzatziki")
include("plugin-i18n")

include("extensions")
include("extensions:java-cucumber")
include("extensions:kotlin")
include("extensions:scala")
include("extensions:javascript")
include("extensions:python")

// Independent plugin (own id io.nimbly.cucumber.python) — NOT part of Cucumber+.
include("cucumber-python")

include("tests")

