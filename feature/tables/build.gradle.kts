plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.tables"
}

dependencies {
  api(project(":dicesets:format"))
  api(project(":data"))

  // The bundled package ships the five looks `docs/tables.md` describes, so
  // the tests pick from the real ones rather than from invented tables that
  // could drift from what a player actually sees.
  testImplementation(project(":dicesets:builtin"))
}
