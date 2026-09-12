plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.settings"
}

dependencies {
  api(project(":data"))
}
