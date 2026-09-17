// Android library modules that carry no Compose UI.

import de.drehtuer.dinfinity.build.configureDeviceTestVerification
import de.drehtuer.dinfinity.build.generateRobolectricProperties

plugins {
  id("com.android.library")
  id("dinfinity.quality")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

val androidApi = providers.gradleProperty("dinfinity.androidApi").get().toInt()
val minimumSdk = providers.gradleProperty("dinfinity.minSdk").get().toInt()
val buildTools = providers.gradleProperty("dinfinity.buildTools").get()

android {
  compileSdk = androidApi
  buildToolsVersion = buildTools

  defaultConfig {
    minSdk = minimumSdk
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }

  // Lets AGP instrument the debug unit tests and build the JaCoCo report
  // itself, so `dinfinity.coverage` does not have to guess where AGP put the
  // compiled classes. Debug only: release is minified, and coverage of
  // R8-rewritten bytecode means nothing.
  buildTypes {
    getByName("debug") {
      enableUnitTestCoverage = true
    }
  }

  testCoverage {
    jacocoVersion = catalog.findVersion("jacoco").get().requiredVersion
  }

  lint {
    warningsAsErrors = true
    abortOnError = true
    // Named rather than left to `warningsAsErrors`, so that turning that off
    // one day does not quietly turn this off with it. `HardcodedText` only
    // reads layout XML, though, and every screen here is Compose — the check
    // that covers Kotlin is `verifyTextIsAResource` in `dinfinity.quality`
    // (`docs/architecture.md`, "Text a person reads").
    error += setOf("HardcodedText", "MissingTranslation", "ExtraTranslation")
    // Off, on purpose. `NewerVersionAvailable` asks Maven Central on every run
    // whether anything newer exists, so a dependency publishing a release turns
    // the build red on a commit that changed nothing — tomlj 1.3.0 did exactly
    // that. A build should succeed or fail on what is in the tree.
    //
    // It is also invisible locally: the devcontainer runs Gradle `--offline`,
    // so the detector has no network and says nothing, and `./gradlew check`
    // passes on a tree CI will reject. A check that only fires on one of the
    // two machines is worse than no check.
    //
    // Keeping up to date is Dependabot's, which opens a pull request per bump
    // with the verification metadata regenerated beside it — a version arrives
    // as something to review rather than as a broken build
    // (`docs/build-setup.md`).
    // Both of them: `GradleDependency` is the same question asked by a
    // different detector, and turning one off simply hands the failure to the
    // other. Found by putting tomlj back to 1.2.0 and watching the build fail
    // again under the other name.
    disable += setOf("NewerVersionAvailable", "GradleDependency")
  }
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    allWarningsAsErrors.set(true)
  }
}

configureDeviceTestVerification()

// Every module's unit tests run against the same API, without every module
// having to remember to say so (`docs/build-setup.md`).
generateRobolectricProperties(android)

dependencies {
  "testImplementation"(catalog.findLibrary("junit4").get())
  "testImplementation"(catalog.findLibrary("robolectric").get())
  "testImplementation"(catalog.findLibrary("androidx-test-junit").get())
  "testImplementation"(catalog.findLibrary("kotlinx-coroutines-test").get())
  "testImplementation"(catalog.findLibrary("turbine").get())
  // The device tier: the physics bridge and the renderer can only be proven on
  // real hardware, so every Android module can carry instrumented tests.
  "androidTestImplementation"(catalog.findLibrary("junit4").get())
  "androidTestImplementation"(catalog.findLibrary("androidx-test-core").get())
  "androidTestImplementation"(catalog.findLibrary("androidx-test-junit").get())
  "androidTestImplementation"(catalog.findLibrary("androidx-test-runner").get())
  "androidTestImplementation"(catalog.findLibrary("androidx-test-espresso-core").get())
}

// Robolectric reaches into JDK internals (java.io.FileDescriptor among others),
// which the module system closes off from Java 17 on. Without these opens every
// Robolectric test fails at start-up with "perhaps JRE has changed?".
val robolectricJvmArgs =
  listOf(
    // SharedSecrets is not merely closed, it is unexported, so it needs
    // --add-exports rather than --add-opens.
    "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.base/java.security=ALL-UNNAMED",
    "--add-opens=java.base/java.text=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED",
  )

tasks.withType<Test>().configureEach {
  jvmArgs(robolectricJvmArgs)
  testLogging {
    events("failed")
    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
  }
}
