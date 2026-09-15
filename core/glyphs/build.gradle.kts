plugins {
  id("dinfinity.kotlin-jvm")
}

dependencies {
  // A glyph is drawn at a size, and what decides that size is a die's faces
  // and the atlas grid they are laid out in (`docs/dice-sets.md`).
  api(project(":core:model"))

  testImplementation(project(":test-fixtures"))
}
