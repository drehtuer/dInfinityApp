plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.saved"
}

dependencies {
  api(project(":core:notation"))
  api(project(":data"))

  // The file saved rolls travel in, and the rules about what may be in it.
  api(project(":core:collection"))

  // The formula field with its squiggle, shared with the tray and the graph.
  api(project(":ui:common"))

  // The editor says what a formula is worth — the exact mean and range —
  // which is free to compute because nothing has to be thrown to know it
  // (`docs/probability.md`).
  implementation(project(":core:probability"))

  // The screen is tested against a real database rather than a fake
  // repository: the ordering a player notices most is SQL's, and a fake would
  // only assert that the test's own sort works.
  testImplementation(project(":dicesets:builtin"))
  testImplementation(libs.androidx.room.runtime)
}
