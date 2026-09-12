plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.designer"
}

dependencies {
  api(project(":dicesets:format"))
}
