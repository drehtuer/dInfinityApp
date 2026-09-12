plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.sets"
}

dependencies {
  api(project(":dicesets:format"))
  api(project(":dicesets:install"))
}
