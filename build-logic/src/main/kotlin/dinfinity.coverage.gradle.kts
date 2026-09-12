// JaCoCo, applied by `dinfinity.quality` so every module is measured and none
// can opt out by forgetting a line.
//
// Two report tasks exist, because the two module kinds produce coverage
// differently:
//
//   * pure Kotlin modules use Gradle's own `jacocoTestReport`;
//   * Android modules use AGP's `createDebugUnitTestCoverageReport`, switched
//     on by `enableUnitTestCoverage` in the android-library plugin. Letting AGP
//     build the report avoids hard-coding the paths of its intermediate class
//     directories, which are not API and have moved between AGP versions.
//
// `coverageReport` is the single name that works in either kind, so CI and a
// developer run the same command.
//
// Reports are left per module rather than merged: SonarQube takes a list of XML
// files and merges them itself, and a hand-rolled merge would be one more thing
// to keep correct for no gain.
//
// What counts is **function and branch** coverage, not lines (.claude/CLAUDE.md).

plugins {
  id("jacoco")
}

// The catalog is reached the long way round, as in the other convention
// plugins: the generated `libs` accessor is not available to them.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

jacoco {
  toolVersion = catalog.findVersion("jacoco").get().requiredVersion
}

tasks.withType<Test>().configureEach {
  extensions.configure<JacocoTaskExtension> {
    // Robolectric loads Android classes that carry no source location; without
    // this they are dropped and Android modules look far less covered than
    // they are.
    isIncludeNoLocationClasses = true
    // JDK internals genuinely have no source here, and instrumenting them
    // makes the agent fail on class load.
    excludes = listOf("jdk.internal.*")
  }
}

val coverageReport =
  tasks.register("coverageReport") {
    group = "verification"
    description = "Writes this module's JaCoCo XML report for SonarQube."
  }

// Pure Kotlin: Gradle's own report task, which only needs its XML switching on.
plugins.withId("org.jetbrains.kotlin.jvm") {
  tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
      xml.required.set(true)
      html.required.set(true)
      csv.required.set(false)
    }
  }
  coverageReport.configure { dependsOn(tasks.named("jacocoTestReport")) }
}

// Android: AGP registers its report task long after this plugin is applied, so
// the dependency is declared by name — Gradle resolves that when it builds the
// task graph, by which time the task exists.
listOf("com.android.library", "com.android.application").forEach { androidPlugin ->
  pluginManager.withPlugin(androidPlugin) {
    coverageReport.configure { dependsOn("createDebugUnitTestCoverageReport") }
  }
}
