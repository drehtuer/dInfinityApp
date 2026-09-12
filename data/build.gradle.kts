plugins {
  id("dinfinity.android-library")
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

android {
  namespace = "de.drehtuer.dinfinity.data"
}

// SchemaTest reads the schemas that are checked in, not a copy of them, so it
// is told where they are rather than given them on a classpath.
tasks.withType<Test>().configureEach {
  systemProperty(
    "dinfinity.schemas",
    layout.projectDirectory
      .dir("schemas")
      .asFile.absolutePath,
  )
}

room {
  // The schema of every version is written out and checked in, which is what
  // makes "migrations from day one" enforceable rather than aspirational:
  // Room refuses to bump a version without one, and MigrationTest walks every
  // published schema up to the current one (docs/statistics.md).
  //
  // Through Room's own plugin rather than as a bare KSP argument, because a
  // KSP argument is global: every variant would write the same file, and the
  // debug and release processors would race each other for it.
  schemaDirectory("$projectDir/schemas")
}

ksp {
  arg("room.generateKotlin", "true")
}

dependencies {
  api(project(":core:model"))
  api(project(":core:stats"))

  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  testImplementation(project(":test-fixtures"))
  testImplementation(libs.androidx.room.testing)
}
