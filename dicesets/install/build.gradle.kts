plugins {
  id("dinfinity.android-library")
}

android {
  namespace = "de.drehtuer.dinfinity.dicesets.install"
}

dependencies {
  api(project(":dicesets:format"))

  implementation(libs.okhttp)

  // The note the app writes beside an installed package, read and written
  // through a parser rather than assembled as text (`PackageMeta`).
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.commons.compress)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(project(":test-fixtures"))
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.okhttp.tls)
  testImplementation(libs.kotlinx.coroutines.test)
}
