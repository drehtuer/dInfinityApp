plugins {
  id("dinfinity.android-library")
}

android {
  namespace = "de.drehtuer.dinfinity.dicesets.install"
}

dependencies {
  api(project(":dicesets:format"))
}
