// Android library modules that carry no Compose UI.

import de.drehtuer.dinfinity.build.configureDeviceTestVerification

plugins {
  id("com.android.library")
  id("dinfinity.quality")
}

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

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  "testImplementation"(libs.findLibrary("junit4").get())
  "testImplementation"(libs.findLibrary("robolectric").get())
  "testImplementation"(libs.findLibrary("androidx-test-junit").get())
  "testImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
  "testImplementation"(libs.findLibrary("turbine").get())
  // The device tier: the physics bridge and the renderer can only be proven on
  // real hardware, so every Android module can carry instrumented tests.
  "androidTestImplementation"(libs.findLibrary("junit4").get())
  "androidTestImplementation"(libs.findLibrary("androidx-test-core").get())
  "androidTestImplementation"(libs.findLibrary("androidx-test-junit").get())
  "androidTestImplementation"(libs.findLibrary("androidx-test-runner").get())
  "androidTestImplementation"(libs.findLibrary("androidx-test-espresso-core").get())
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
