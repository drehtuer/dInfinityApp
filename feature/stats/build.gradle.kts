plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.stats"
}

dependencies {
  api(project(":core:stats"))
  api(project(":data"))

  // A die's faces come from the installed set, because a die labelled
  // 1,2,3,1,2,3 is a d3 and its fair line has to say so.
  api(project(":core:notation"))

  // The screens are tested against a real database, because what a history
  // shows is what SQL ordered — a fake would assert that the fake sorts.
  testImplementation(project(":dicesets:builtin"))
  testImplementation(libs.androidx.room.runtime)
}
