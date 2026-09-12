plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.designer"
}

dependencies {
  api(project(":designer"))
}
