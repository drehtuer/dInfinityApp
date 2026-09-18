plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.settings"
}

dependencies {
  api(project(":data"))

  // The shared Modernist widgets: the segmented control every settings row is
  // built from, the `.btn` variants and the rules between rows. They live in
  // `:ui:common` because no one screen owns them (`docs/architecture.md`,
  // Modules).
  api(project(":ui:common"))

  // The developer screen replays a `ThrowSpec` and reads the anomaly log, both
  // of which live beside the simulation they describe. Pure Kotlin, so nothing
  // about a physics engine comes with it (`docs/architecture.md`, decision 40).
  api(project(":simulation:api"))

  // The notation screen draws itself from `NotationReference`, which lives
  // beside the parser it describes. It arrives transitively through `:data`
  // already; named here because a module should not depend on something by
  // accident of somebody else's dependency graph.
  api(project(":core:notation"))

  // Standard dice, for building the `ThrowSpec` a replay test replays.
  testImplementation(project(":test-fixtures"))
}
