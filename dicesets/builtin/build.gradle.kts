plugins {
  id("dinfinity.android-library")
}

android {
  namespace = "de.drehtuer.dinfinity.dicesets.builtin"
}

dependencies {
  api(project(":dicesets:format"))

  // The numbering in `diceset.toml` is geometry rather than a convention, so
  // the test derives it from the solids rather than trusting the file
  // (`BuiltinDiceSetTest`, "opposite faces add up"). A test-only dependency:
  // nothing the app runs reads the shipped set through the simulation.
  testImplementation(project(":simulation:api"))
  testImplementation(project(":test-fixtures"))
}

/**
 * Copies the committed archives of `sample-sets/` into this module's
 * resources under `dicesets/samples/`, which is where [SampleSets] reads them
 * from. Generated rather than committed twice: the archive a player downloads
 * and the one the app installs are the same file.
 */
abstract class CopySampleArchives : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  abstract val archives: ConfigurableFileCollection

  @get:OutputDirectory
  abstract val output: DirectoryProperty

  @TaskAction
  fun copy() {
    val into = output.get().asFile.resolve("dicesets/samples")
    into.deleteRecursively()
    into.mkdirs()
    archives.files.forEach { it.copyTo(into.resolve(it.name)) }
  }
}

val copySampleArchives =
  tasks.register<CopySampleArchives>("copySampleArchives") {
    archives.from(fileTree(rootDir.resolve("sample-sets")) { include("*.zip") })
    output.set(layout.buildDirectory.dir("generated/sample-sets"))
  }

androidComponents {
  onVariants { variant ->
    variant.sources.resources?.addGeneratedSourceDirectory(copySampleArchives, CopySampleArchives::output)
  }
}

// `SampleSetsTest` compares what is carried with `sample-sets/` itself.
tasks.withType<Test>().configureEach {
  inputs
    .dir(rootDir.resolve("sample-sets"))
    .withPropertyName("sampleSets")
    .withPathSensitivity(PathSensitivity.RELATIVE)
}
