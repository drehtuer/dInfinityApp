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

include(":feature:roll")
include(":feature:graph")
include(":feature:saved")
include(":feature:sets")
include(":feature:tables")
include(":feature:designer")
include(":feature:stats")
include(":feature:settings")

include(":test-fixtures")
