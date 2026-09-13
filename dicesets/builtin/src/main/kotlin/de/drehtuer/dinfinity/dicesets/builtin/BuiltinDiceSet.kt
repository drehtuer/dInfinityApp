package de.drehtuer.dinfinity.dicesets.builtin

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationResult

/**
 * The dice the app ships with, read from the package it ships as.
 *
 * The point of this module is what it *does not* do. There is no Kotlin here
 * that builds a d20 out of a loop; there is a `diceset.toml` in the resources,
 * and it is parsed and validated by exactly the code a download goes through
 * (`docs/dice-sets.md`). The app therefore eats its own dog food on every
 * launch: if the validator breaks, the built-in dice break, loudly, rather
 * than the built-in dice carrying on while every downloaded set quietly stops
 * installing.
 *
 * It is a resource on the classpath rather than an Android asset so that it
 * loads the same way in a unit test, in an instrumented test and in the app,
 * and needs no `Context` to reach.
 */
object BuiltinDiceSet {
  /** Where the package sits in this module's resources. */
  const val RESOURCE_ROOT: String = "dicesets/builtin"

  /**
   * The bundled set.
   *
   * Built once and kept: it is read on the first roll of every session and
   * never changes afterwards.
   */
  val set: DiceSet by lazy { load() }

  /** The package's files, for anything that wants to validate it itself. */
  fun files(): PackageFiles = ResourcePackage(RESOURCE_ROOT, BuiltinDiceSet::class.java.classLoader)

  /**
   * Reads and validates the package.
   *
   * A failure here is not a user's problem and cannot be recovered from — the
   * app has no dice — so it throws rather than degrading. `BuiltinDiceSetTest`
   * is what makes sure it never happens outside a broken working copy.
   */
  fun load(): DiceSet =
    when (val result = DiceSetValidator.validate(files())) {
      is ValidationResult.Valid -> result.set
      is ValidationResult.Rejected ->
        error(
          "The bundled dice set does not pass the validator:\n" +
            result.messages.joinToString("\n") { "  $it" },
        )
    }
}

/** A package whose files are resources on the classpath. */
private class ResourcePackage(
  private val root: String,
  private val loader: ClassLoader?,
) : PackageFiles {
  private val cache = mutableMapOf<String, ByteArray?>()

  override fun read(path: String): ByteArray? = cached(path)?.copyOf()

  override fun size(path: String): Long? = cached(path)?.size?.toLong()

  private fun cached(path: String): ByteArray? =
    cache.getOrPut(path) {
      loader?.getResourceAsStream("$root/$path")?.use { it.readBytes() }
    }
}
