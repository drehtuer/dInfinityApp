plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.stats"
}

dependencies {
  // The Modernist tokens and the controls drawn from them, which every screen
  // shares rather than transcribing (`docs/design-handover.md`).
  implementation(project(":ui:common"))

  api(project(":core:stats"))
  api(project(":data"))

  // A die's faces come from the installed set, because a die labelled
  // 1,2,3,1,2,3 is a d3 and its fair line has to say so.
  api(project(":core:notation"))

  // The history exports as JSON, written through the DOM the way everything
  // else here writes JSON (`.claude/CLAUDE.md`). Already a dependency of
  // `core/collection` and `data`, and already pinned in
  // `gradle/verification-metadata.xml` — new to this module, not to the build.
  implementation(libs.kotlinx.serialization.json)

  // The screens are tested against a real database, because what a history
  // shows is what SQL ordered — a fake would assert that the fake sorts.
  testImplementation(project(":dicesets:builtin"))
  testImplementation(libs.androidx.room.runtime)
}
