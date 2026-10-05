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

  // `Tray.shaders` is a StateFlow: written on the roll thread while a material
  // compiles, collected by the roll screen on the main one, and safe to read
  // from either without a lock of this module's own (`ShaderWork`). Already
  // in the build for `dicesets/install`, and on the app's classpath through
  // Compose.
  api(libs.kotlinx.coroutines.core)

  implementation(libs.filament.android)
  implementation(libs.filamat.android)

  // Not filament-utils-android: the studio panorama is decoded and folded into
  // a cube here (`Radiance`, `StudioLight.faces`) and prefiltered by the engine
  // above, rather than pull in a native library that links gltfio's and costs
  // 17.5 MB over four ABIs (`docs/architecture.md`, decision 89).

  testImplementation(project(":test-fixtures"))
  // Reads the shipped panorama on the JVM, so `StudioLightTest` can check the
  // irradiance written into `StudioLight` against the file it came from, and
  // `RadianceTest` the app's own decoder against an independent one.
  testImplementation(libs.twelvemonkeys.imageio.hdr)
  androidTestImplementation(project(":test-fixtures"))

  // The rendered harness: the real physics thrown through this module's tray
  // onto a real surface, scored by the plain harness's arithmetic. Test-only,
  // and only in this direction — nothing this module ships knows Jolt or the
  // harness exists (`docs/architecture.md`, decision 80).
  androidTestImplementation(project(":simulation:jolt"))
  androidTestImplementation(project(":simulation:harness"))

  // The render gallery throws the built-in set's own dice onto its own
  // tables, so a picture is of what a player sees (`RenderGalleryTest`).
  androidTestImplementation(project(":dicesets:builtin"))
}
