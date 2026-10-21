package de.drehtuer.dinfinity.dicesets.builtin

/**
 * The sample sets the app carries to install on first launch
 * (`docs/dice-sets.md`, "Sets that come installed"; `sample-sets/README.md`).
 *
 * The archives are the ones committed in `sample-sets/`, copied into this
 * module's resources when it is built (`build.gradle.kts`), so there is one
 * copy of each in the repository and the app ships exactly what a player
 * could download. A resource rather than an asset for [BuiltinDiceSet]'s
 * reason: it reads the same in a unit test and in the app.
 *
 * Only the archives travel; installing them is `dicesets/install`'s
 * (`PreinstalledSets`), which is where the validator is.
 */
object SampleSets {
  /** Where the archives sit in this module's resources. */
  const val RESOURCE_ROOT: String = "dicesets/samples"

  /**
   * Every archive, by file name. A list rather than a scan of the resources,
   * which a class loader cannot do; `SampleSetsTest` holds it to the zips in
   * `sample-sets/`, so a fifth set cannot be committed and left out.
   */
  val ARCHIVES: List<String> = listOf("green-resin.zip", "marble.zip", "red-glass.zip", "steel.zip")

  /** The bytes of the archive called [name], or null when the app does not carry it. */
  fun read(name: String): ByteArray? =
    SampleSets::class.java.classLoader
      ?.getResourceAsStream("$RESOURCE_ROOT/$name")
      ?.use { it.readBytes() }
}
