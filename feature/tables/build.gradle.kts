plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.tables"
}

dependencies {
  api(project(":dicesets:format"))
  api(project(":data"))
}
