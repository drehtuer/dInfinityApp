plugins {
  id("dinfinity.android-app")
}

android {
  namespace = "de.drehtuer.dinfinity"
}

// The app wires every screen and every engine implementation together; it is
// the only module allowed to know about all of them.
dependencies {
  implementation(project(":feature:roll"))
  implementation(project(":feature:graph"))
  implementation(project(":feature:saved"))
  implementation(project(":feature:sets"))
  implementation(project(":feature:tables"))
  implementation(project(":feature:designer"))
  implementation(project(":feature:stats"))
  implementation(project(":feature:settings"))

  implementation(project(":core:model"))
  implementation(project(":data"))

  implementation(project(":dicesets:builtin"))
  implementation(project(":input:shake"))
  implementation(project(":render:filament"))
  implementation(project(":render:headless"))
  implementation(project(":simulation:jolt"))

  // FileProvider, for handing an exported collection to another app without
  // making anything world-readable. The provider is declared in this module's
  // manifest because its authority is the application's id.
  implementation(libs.androidx.core.ktx)

  // The one place the whole app is assembled is the one place a wiring mistake
  // between two feature modules can show up, so its test builds a real
  // database and gives every destination a real presenter.
  testImplementation(project(":dicesets:builtin"))
  testImplementation(libs.androidx.room.runtime)
}
