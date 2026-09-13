plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.saved"
}

dependencies {
  api(project(":core:notation"))
  api(project(":data"))

  // The screen is tested against a real database rather than a fake
  // repository: the ordering a player notices most is SQL's, and a fake would
  // only assert that the test's own sort works.
  testImplementation(project(":dicesets:builtin"))
  testImplementation(libs.androidx.room.runtime)
}
