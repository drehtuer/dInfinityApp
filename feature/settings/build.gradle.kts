plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.settings"
}

dependencies {
  api(project(":data"))

  // The notation screen draws itself from `NotationReference`, which lives
  // beside the parser it describes. It arrives transitively through `:data`
  // already; named here because a module should not depend on something by
  // accident of somebody else's dependency graph.
  api(project(":core:notation"))
}
