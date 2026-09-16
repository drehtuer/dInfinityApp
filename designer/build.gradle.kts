plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.designer"
}

dependencies {
  api(project(":dicesets:format"))

  // The stamp and "fill all faces with numbers" place the outlines the tray
  // prints with, at the size and in the place the tray would put them — one
  // font and one solve, so a drawn die and a printed one agree
  // (`docs/face-designer.md`, "The stamp").
  api(project(":core:glyphs"))

  // A draft on disk is JSON, read through its DOM with every field taken by
  // hand — the same way `core/collection` reads a collection. A draft is the
  // app's own file rather than a stranger's, so the reason here is the other
  // one: a deserializer's idea of the file is the class shape of the day, and
  // a drawing has to survive the class changing under it (`DraftFile`).
  implementation(libs.kotlinx.serialization.json)
}
