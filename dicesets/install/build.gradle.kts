plugins {
  id("dinfinity.android-library")
}

android {
  namespace = "de.drehtuer.dinfinity.dicesets.install"
}

dependencies {
  api(project(":dicesets:format"))

  implementation(libs.okhttp)
  implementation(libs.commons.compress)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(project(":test-fixtures"))
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.okhttp.tls)
  testImplementation(libs.kotlinx.coroutines.test)
}
