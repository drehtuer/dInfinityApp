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

// A `ValidationMessage` is written where the validation happens, and that is a
// module with no resources — by design, because nothing that reads a
// stranger's file may depend on Android (`docs/dice-sets.md`). The sentences
// end up on screen all the same, so they are a real gap; closing it means
// giving every message a typed reason the screen phrases, which is a design
// change rather than a string move (`docs/TODO.md`, "Open questions").
tasks.named<de.drehtuer.dinfinity.build.VerifyTextIsAResourceTask>("verifyTextIsAResource") {
  exempt.addAll("AtlasDecoder.kt", "InstalledSets.kt")
}
