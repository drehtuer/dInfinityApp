plugins {
  id("dinfinity.android-library")
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

android {
  namespace = "de.drehtuer.dinfinity.data"
}

// SchemaTest and MigrationTest read the schemas that are checked in, not a copy
// of them, so they are told where they are rather than given them on a
// classpath. MigrationTest builds a real version-1 database out of version 1's
// own exported schema, which is the artifact that says what shipped.
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
  // Room refuses to bump a version without one, SchemaTest walks every
  // published schema up to the current one, and MigrationTest actually runs
  // the migrations against a database built from the oldest of them
  // (docs/statistics.md).
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

  // What an imported collection arrives as. The reading and the refusing are
  // core/collection's; writing it down is this module's.
  api(project(":core:collection"))

  // A roll's breakdown is stored as JSON, whole rather than normalised: a
  // breakdown means what it meant then, and normalising it would let a set
  // uninstalled last week rewrite last week's rolls (docs/statistics.md).
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  testImplementation(project(":test-fixtures"))
  testImplementation(libs.androidx.room.testing)
}
