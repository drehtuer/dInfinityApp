plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.roll"
}

dependencies {
  api(project(":core:notation"))
  api(project(":simulation:api"))
  api(project(":data"))
}
