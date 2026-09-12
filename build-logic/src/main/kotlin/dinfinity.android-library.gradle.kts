// Android library modules that carry no Compose UI.

import de.drehtuer.dinfinity.build.configureDeviceTestVerification

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
  }
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    allWarningsAsErrors.set(true)
  }
}

configureDeviceTestVerification()

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
