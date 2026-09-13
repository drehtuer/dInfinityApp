plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.stats"
}

dependencies {
  api(project(":core:stats"))
  api(project(":data"))

  // The screens are tested against a real database, because what a history
  // shows is what SQL ordered — a fake would assert that the fake sorts.
  testImplementation(libs.androidx.room.runtime)
}
