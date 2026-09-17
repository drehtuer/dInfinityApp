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

  // The accent's own colour picker is the one the face designer already draws:
  // hue, depth and brightness over `designer/Ink`, whose arithmetic has a JVM
  // test. Two pickers in one app that disagreed about what a hue is would be
  // one too many, and the alternative — three red-green-blue sliders — is the
  // picker `Ink`'s own KDoc argues against, because nobody thinks in those.
  implementation(project(":designer"))

  // Standard dice, for building the `ThrowSpec` a replay test replays.
  testImplementation(project(":test-fixtures"))
}
