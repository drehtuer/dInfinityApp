plugins {
  id("dinfinity.android-library")
}

android {
  namespace = "de.drehtuer.dinfinity.data"
}

dependencies {
  api(project(":core:model"))
  api(project(":core:stats"))

  // Settings are a Flow, so coroutines are part of this module's surface.
  api(libs.kotlinx.coroutines.core)
  implementation(libs.androidx.datastore.preferences)
}
