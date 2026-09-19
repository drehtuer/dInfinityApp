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

  // The Solid tab turns the real polyhedron over, and `simulation/api` already
  // owns every catalogue solid: its corners, the direction of each readable
  // position, the face order the whole app agrees on and — since the Solid tab
  // — which corners make up which face (`SolidFaces`). A second description of
  // a die's geometry is the thing `docs/dice-sets.md` warns comes apart the
  // first time either is touched, so there is one and the renderer's mesh is
  // built from it too (`docs/face-designer.md`, "The solid, not just the
  // face").
  api(project(":simulation:api"))

  // A draft on disk is JSON, read through its DOM with every field taken by
  // hand — the same way `core/collection` reads a collection. A draft is the
  // app's own file rather than a stranger's, so the reason here is the other
  // one: a deserializer's idea of the file is the class shape of the day, and
  // a drawing has to survive the class changing under it (`DraftFile`).
  implementation(libs.kotlinx.serialization.json)

  // The bundled dice, for the export tests. A drawing is started from a real
  // die and what leaves the exporter has to be that die again, face for face
  // — which is a claim about the numbering of an actual d20 rather than about
  // an invented one (`DrawnDiceScoreTheSameTest`).
  testImplementation(project(":dicesets:builtin"))
}
