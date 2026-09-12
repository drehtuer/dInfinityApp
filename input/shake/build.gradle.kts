plugins {
  id("dinfinity.android-library")
}

android {
  namespace = "de.drehtuer.dinfinity.input.shake"
}

dependencies {
  api(project(":simulation:api"))
}
