plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.stats"
}

dependencies {
  api(project(":core:stats"))
  api(project(":data"))
}
