plugins {
  id("dinfinity.android-library")
}

android {
  namespace = "de.drehtuer.dinfinity.dicesets.builtin"
}

dependencies {
  api(project(":dicesets:format"))

  // The numbering in `diceset.toml` is geometry rather than a convention, so
  // the test derives it from the solids rather than trusting the file
  // (`BuiltinDiceSetTest`, "opposite faces add up"). A test-only dependency:
  // nothing the app runs reads the shipped set through the simulation.
  testImplementation(project(":simulation:api"))
  testImplementation(project(":test-fixtures"))
}
