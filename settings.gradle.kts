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

include("tests")

