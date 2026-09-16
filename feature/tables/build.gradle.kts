plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.tables"
}

dependencies {
  api(project(":dicesets:format"))
  api(project(":data"))

  // The personal package, which is where a photo table is written and what
  // decides how big a photo may be (`docs/tables.md`, "Your own photo"). The
  // same dependency `feature/sets` takes, and for the same reason: "My dice"
  // is an ordinary installed package built by that module, and a screen that
  // adds to it needs its rules rather than a second copy of them.
  api(project(":designer"))

  // The bundled package ships the five looks `docs/tables.md` describes, so
  // the tests pick from the real ones rather than from invented tables that
  // could drift from what a player actually sees.
  testImplementation(project(":dicesets:builtin"))
}
