pluginManagement {
  includeBuild("build-logic")
  repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "dInfinity"

// Keep this list in step with the module map in docs/architecture.md — the
// `verifyModuleGraph` task fails the build if a module on disk is missing here.
include(":app")

include(":core:model")
include(":core:notation")
include(":core:probability")
include(":core:stats")

// The saved-roll collection format: read, written and validated. A collection
// is a file from a stranger, so it lives beside the notation it carries rather
// than in the screen that opens it (docs/architecture.md, Modules).
include(":core:collection")

// The built-in font, and the numbers it prints on a die that has no artwork.
// It is `core/` rather than part of the renderer because the face designer
// stamps from the same font the tray draws with, and two fonts that were
// meant to be one would disagree about what a `6` looks like
// (docs/architecture.md, Modules).
include(":core:glyphs")

include(":dicesets:format")
include(":dicesets:builtin")
include(":dicesets:install")

include(":simulation:api")
include(":simulation:jolt")

include(":render:headless")
include(":render:filament")

include(":input:shake")
include(":designer")
include(":data")

// Screen furniture more than one screen needs. Not a feature: nothing in it
// knows what screen it is on (docs/architecture.md, Modules).
include(":ui:common")

include(":feature:roll")
include(":feature:graph")
include(":feature:saved")
include(":feature:sets")
include(":feature:tables")
include(":feature:designer")
include(":feature:stats")
include(":feature:settings")

include(":test-fixtures")
