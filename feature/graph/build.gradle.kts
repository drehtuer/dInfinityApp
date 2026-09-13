plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.graph"
}

dependencies {
  api(project(":core:notation"))
  api(project(":core:probability"))

  // The graph is about the formula and nothing else: no simulator, no table.
  // `500d6` graphs perfectly well and is refused only when it reaches the tray
  // (`docs/probability.md`).
  testImplementation(project(":dicesets:builtin"))
}
