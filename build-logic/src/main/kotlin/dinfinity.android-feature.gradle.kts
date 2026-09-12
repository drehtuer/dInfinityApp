// Android library modules that do carry Compose UI: the feature/* screens and
// the face designer's canvas.

plugins {
  id("dinfinity.android-library")
  id("org.jetbrains.kotlin.plugin.compose")
}

extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
  buildFeatures {
    compose = true
  }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
  val bom = platform(libs.findLibrary("compose-bom").get())
  "implementation"(bom)
  "implementation"(libs.findLibrary("compose-ui").get())
  "implementation"(libs.findLibrary("compose-foundation").get())
  "implementation"(libs.findLibrary("compose-material3").get())
  "implementation"(libs.findLibrary("compose-ui-tooling-preview").get())
  "implementation"(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
  "debugImplementation"(libs.findLibrary("compose-ui-tooling").get())
  "debugImplementation"(libs.findLibrary("compose-ui-test-manifest").get())
  "testImplementation"(bom)
  "testImplementation"(libs.findLibrary("compose-ui-test-junit4").get())
}
