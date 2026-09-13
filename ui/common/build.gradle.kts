plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.ui.common"
}

dependencies {
  // What the shared pieces are *about*: formulas and the dice a formula names.
  // No data, no simulator, no navigation — a screen's furniture should not be
  // able to reach a database (`docs/architecture.md`, Modules).
  api(project(":core:notation"))
}
