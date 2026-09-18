package de.drehtuer.dinfinity.core.notation

import de.drehtuer.dinfinity.core.model.RolledDie
import kotlin.math.absoluteValue

/**
 * How a roll of Fudge dice writes what it came to.
 *
 * **A Fudge total carries its sign, and the sign is the answer.** A Fudge die
 * is not a die that happens to roll small numbers: its faces are a minus, a
 * blank and a plus, and what a player reads off four of them is `+2` or `−1`
 * rather than a count of anything. That is how Fate writes a result, and it
 * is what the faces themselves say — the bundled `df` is labelled
 * `− − 0 + 0 +` (`docs/dice-sets.md`, "Numbering").
 *
 * It exists because the app was writing one of the two signs and not the
 * other. A total of one dF showing its minus printed `-1`; the same die
 * showing its plus printed `1`, because that is what a `Long` prints. So a
 * minus was a sign and a plus was nothing, which is not a rule anybody could
 * have stated.
 *
 * **The other answer was to print the die's own label** — `−`, `+`, `0` — for
 * a roll of exactly one die. It was refused because it only works for one
 * die: `4dF` has no single face to quote, and a d10 would then read `0` for
 * the face that is worth ten (`docs/dice-notation.md`, "d100 and d%").
 * Signing the total says the same thing about one die and goes on saying it
 * about four.
 *
 * The minus is U+2212 MINUS SIGN, the one the faces are labelled with, rather
 * than a hyphen. A total set beside the dice that made it should use the same
 * glyph they do.
 *
 * **Only for display.** Exports write the number
 * (`feature/stats/HistoryExport`): a file is read by a machine, and a machine
 * parsing `−2` would be a machine this app broke on purpose.
 */
object FudgeTotal {
  /**
   * True when every die in [dice] is a Fudge die, so the total is a sign.
   *
   * Empty is false: a formula with no dice in it — `4`, or a group whose dice
   * were all dropped — is arithmetic, and arithmetic is a number.
   *
   * A mixed roll is false too. `1dF + 1d6` has no sign to print, because the
   * d6 is a count and a count plus a sign is a count.
   */
  fun isFudge(dieIds: List<String>): Boolean =
    dieIds.isNotEmpty() && dieIds.all { id -> id == DieResolver.FUDGE_DIE_ID }

  /** The same question of dice that have just landed. */
  fun isFudgeRoll(dice: List<RolledDie>): Boolean = isFudge(dice.map(RolledDie::dieId))

  /**
   * [total] as the player should read it, given the [dice] that made it.
   *
   * Anything that is not a roll of Fudge dice is written the way it always
   * was, so this is safe to call wherever a total is drawn.
   */
  fun write(
    total: Long,
    dieIds: List<String>,
  ): String = if (isFudge(dieIds)) signed(total) else total.toString()

  /**
   * The same, of dice that have just landed.
   *
   * It takes the ids rather than the dice so that the history, which stores
   * its own kind of die, asks exactly the same question of exactly the same
   * rule (`data/Breakdown`'s `StoredDie`).
   */
  fun writeRoll(
    total: Long,
    dice: List<RolledDie>,
  ): String = write(total, dice.map(RolledDie::dieId))

  /** `+2`, `−1`, `0`. Zero takes no sign, because nothing went either way. */
  private fun signed(total: Long): String =
    when {
      total > 0L -> "$PLUS$total"
      total < 0L -> "$MINUS${total.absoluteValue}"
      else -> "0"
    }

  /** U+2212 MINUS SIGN, as the faces are labelled. */
  const val MINUS: String = "−"

  /** And the plus that was missing. */
  const val PLUS: String = "+"
}
