plugins {
  id("dinfinity.kotlin-jvm")
}

dependencies {
  api(project(":core:model"))

  // Every formula in a collection goes through the same parser the field does.
  // A collection that could carry a formula the app cannot read would be a
  // file that imports and then fails when it is pressed.
  api(project(":core:notation"))

  // JSON through its DOM, never through a deserializer — see the note in the
  // version catalogue. A collection is a file from a stranger.
  implementation(libs.kotlinx.serialization.json)

  testImplementation(project(":test-fixtures"))
}
