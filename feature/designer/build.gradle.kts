plugins {
  id("dinfinity.android-feature")
}

android {
  namespace = "de.drehtuer.dinfinity.feature.designer"
}

dependencies {
  api(project(":designer"))

  // The Modernist tokens and the shared ink, which live where every screen
  // can see them rather than once per feature module.
  implementation(project(":ui:common"))

  // The bundled dice are what the designer opens on, so the tests draw on a
  // real d6 and a real d4 rather than on invented ones.
  testImplementation(project(":dicesets:builtin"))
}
