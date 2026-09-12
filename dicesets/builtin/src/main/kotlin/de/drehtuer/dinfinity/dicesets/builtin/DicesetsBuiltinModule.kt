package de.drehtuer.dinfinity.dicesets.builtin

/**
 * Marks `:dicesets:builtin` as present and wired into the build.
 *
 * The module holds one TOML file and the few lines that read it; the dice it
 * describes are in [BuiltinDiceSet].
 */
object DicesetsBuiltinModule {
  /** This module's Gradle path. */
  const val PATH: String = ":dicesets:builtin"

  /** The Gradle paths this module declares a dependency on. */
  val DEPENDS_ON: List<String> = listOf(":dicesets:format")
}
