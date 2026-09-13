plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.roll"
}

dependencies {
  api(project(":core:notation"))

  // The formula field and the die silhouettes, which more than this screen
  // wants (`docs/architecture.md`, Modules).
  api(project(":ui:common"))
  api(project(":simulation:api"))
  api(project(":data"))

  // The tray. `render/filament` brings the Renderer contract and the roll a
  // tray can drive with it; which engine is underneath is the app's business,
  // not this screen's (`docs/architecture.md`, decision 48).
  api(project(":render:filament"))

  // Shake to roll. The sensors are Android's; everything the shake decides is
  // in this module's dependency and testable without one.
  implementation(project(":input:shake"))

  // Sensors are registered while the screen is resumed and let go when it is
  // not: an accelerometer left running in the background is a battery bill.
  implementation(libs.androidx.lifecycle.runtime.compose)

  testImplementation(project(":test-fixtures"))
  testImplementation(project(":dicesets:builtin"))
}
