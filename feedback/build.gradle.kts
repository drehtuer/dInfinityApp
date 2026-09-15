plugins {
  id("dinfinity.android-library")
}

android {
  namespace = "de.drehtuer.dinfinity.feedback"
}

dependencies {
  // `Impact`, the `Impacts` seam a tray plays through, and `TableSound` —
  // which arrives with `core:model` underneath it. Nothing else: this module
  // knows what a die hitting a table sounds like and not what a roll is.
  api(project(":simulation:api"))
}
