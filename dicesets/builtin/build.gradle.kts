plugins {
  id("dinfinity.android-library")
}

android {
  namespace = "de.drehtuer.dinfinity.dicesets.builtin"
}

dependencies {
  api(project(":dicesets:format"))

  testImplementation(project(":test-fixtures"))
}
