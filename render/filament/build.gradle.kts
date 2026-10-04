plugins {
  id("dinfinity.android-library")
}

android {
  namespace = "de.drehtuer.dinfinity.render.filament"
}

dependencies {
  api(project(":simulation:api"))

  // A die with no artwork prints its labels, and they are drawn from the
  // built-in font (docs/physics-and-rendering.md, "Rendering").
  api(project(":core:glyphs"))

  // For the Renderer contract, which lives with the renderer that does
  // nothing: a headless mode built out of this one, with the drawing switched
  // off, would still hold a GPU context (docs/physics-and-rendering.md).
  api(project(":render:headless"))

  implementation(libs.filament.android)
  implementation(libs.filamat.android)

  testImplementation(project(":test-fixtures"))
  androidTestImplementation(project(":test-fixtures"))

  // The rendered harness: the real physics thrown through this module's tray
  // onto a real surface, scored by the plain harness's arithmetic. Test-only,
  // and only in this direction — nothing this module ships knows Jolt or the
  // harness exists (`docs/architecture.md`, decision 80).
  androidTestImplementation(project(":simulation:jolt"))
  androidTestImplementation(project(":simulation:harness"))
}
