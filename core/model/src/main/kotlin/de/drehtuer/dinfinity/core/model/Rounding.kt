package de.drehtuer.dinfinity.core.model

import kotlin.math.floor

/**
 * How division in a formula rounds (`docs/dice-notation.md`, "Division
 * rounding").
 *
 * [Down] is the default because that is what most game rules say. The result
 * sheet offers the other two for the throw in front of the player, which
 * recomputes the total *from the dice that already landed* — the dice
 * themselves are never re-rolled and never moved. The override is not
 * remembered; the next roll uses the setting again.
 *
 * @param id the stable key written to storage. Never rename one: an unknown id
 *   read back falls to [Default].
 */
enum class Rounding(
  val id: String,
) {
  /** `7 / 2 = 3`, `-7 / 2 = -4`. Floor, so the step between values is even. */
  Down("down") {
    override fun apply(value: Double): Long = floor(value).toLong()
  },

  /** `.5` up, anything below `.5` down: `7 / 2 = 4`, `-7 / 2 = -3`. */
  Nearest("nearest") {
    override fun apply(value: Double): Long = floor(value + HALF).toLong()
  },

  /** `7 / 2 = 4`, `-7 / 2 = -3`. Ceiling. */
  Up("up") {
    override fun apply(value: Double): Long = -floor(-value).toLong()
  },
  ;

  /** [value] as a whole number, rounded this way. */
  abstract fun apply(value: Double): Long

  /**
   * [numerator] over [denominator], rounded this way.
   *
   * The division is done in `Double` only after both sides are exact integers,
   * and every result a formula can reach fits well inside the 53 bits a
   * `Double` holds exactly, because the parse limits bound the magnitude
   * (`docs/dice-notation.md`, "Limits").
   */
  fun divide(
    numerator: Long,
    denominator: Long,
  ): Long {
    require(denominator != 0L) { "Division by zero" }
    return apply(numerator.toDouble() / denominator.toDouble())
  }

  companion object {
    private const val HALF = 0.5

    /** What most game rules say, and what an unreadable setting falls back to. */
    val Default: Rounding = Down

    /** Storage is a string, and strings from disk are not to be trusted. */
    fun ofId(id: String?): Rounding = entries.firstOrNull { it.id == id } ?: Default
  }
}
