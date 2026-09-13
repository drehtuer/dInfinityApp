plugins {
  id("dinfinity.android-library")
}

/** Compiled into both test tiers; see the source-set block below. */
val sharedTestSources = "src/sharedTest/kotlin"

val ndk = providers.gradleProperty("dinfinity.ndk").get()
val cmakeVersion = providers.gradleProperty("dinfinity.cmake").get()

android {
  namespace = "de.drehtuer.dinfinity.simulation.jolt"
  ndkVersion = ndk

  defaultConfig {
    ndk {
      // The two ABIs the app is actually run on: `arm64-v8a` is the phone and
      // every Android device that matters, `x86_64` is the emulator in the
      // devcontainer. Both, because "identical outcomes for identical seeds
      // across JVM, emulator and device" is a release blocker and cannot be
      // checked on an ABI that is not built (`docs/TODO.md`, Step 5.2).
      abiFilters += listOf("arm64-v8a", "x86_64")
    }
  }

  // The golden determinism suite is one suite with two halves: the JVM half
  // asserts everything the engine is handed, the device half asserts what it
  // did with it. They have to agree about what a case *means* before they can
  // disagree about what it came to, so the code that turns a case into a throw
  // is compiled into both rather than copied into each.
  sourceSets {
    getByName("test").kotlin.srcDir(sharedTestSources)
    getByName("androidTest").kotlin.srcDir(sharedTestSources)
  }

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = cmakeVersion
    }
  }
}

dependencies {
  api(project(":simulation:api"))

  // A golden case starts as a formula, so both test tiers parse and plan one.
  // Test-only, in both directions: nothing in this module's own code knows
  // that notation exists.
  testImplementation(project(":core:notation"))
  testImplementation(project(":test-fixtures"))
  androidTestImplementation(project(":core:notation"))
  androidTestImplementation(project(":test-fixtures"))
}
