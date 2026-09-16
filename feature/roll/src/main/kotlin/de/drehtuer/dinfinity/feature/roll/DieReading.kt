package de.drehtuer.dinfinity.feature.roll

import androidx.annotation.StringRes
import de.drehtuer.dinfinity.core.model.RolledDie

/**
 * What a die on the result sheet says once the colour is taken away
 * (`docs/architecture.md`, "Accessibility").
 *
 * The sheet has exactly three kinds of die and draws each of them with a mark
 * only an eye can read: a dropped die is struck through, and one that showed
 * its highest face is printed in the accent. Neither reaches a screen reader,
 * and "the one number in a breakdown anybody scans for" is not a thing to hide
 * from somebody who cannot see the colour.
 *
 * It is an enum and a `when` rather than a pair of flags read at the draw site
 * so that *which of the three a die is* can be asserted without a screen —
 * and so that the colour and the words are chosen by the same decision rather
 * than by two that could come to disagree.
 */
internal enum class DieReading {
  /** Counted, and nothing more to say: the label is the whole of it. */
  Kept,

  /** Counted, and it showed its highest face. */
  NaturalMax,

  /** Thrown, shown, and not counted — `4d6dl1`'s 1. */
  Dropped,
  ;

  /**
   * What a screen reader says about the die, around its label, or `null` when
   * the label alone already says everything.
   *
   * `null` rather than a string that repeats the label: an announcement of
   * "7, ordinary" on every die of a breakdown is noise, and noise is what
   * makes somebody switch the reader off.
   */
  @get:StringRes
  val said: Int?
    get() =
      when (this) {
        Kept -> null
        NaturalMax -> R.string.roll_die_natural_max
        Dropped -> R.string.roll_die_dropped
      }

  companion object {
    /**
     * Which of the three [die] is.
     *
     * Dropped wins over a natural maximum, because a die that does not count
     * is the more surprising of the two things to be told: a 6 on a dropped
     * die is still a 6 that changed nothing.
     */
    fun of(die: RolledDie): DieReading =
      when {
        !die.kept -> Dropped
        die.naturalMax -> NaturalMax
        else -> Kept
      }
  }
}
