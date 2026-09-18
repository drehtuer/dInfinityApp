package de.drehtuer.dinfinity.core.glyphs

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.Face

/**
 * What a face has written on it, and whether it needs a mark to tell it from
 * what it reads as upside down.
 *
 * Here rather than in the renderer because two things ask it now: the tray
 * prints a die that has no artwork (`render/filament`'s `DieNumbers`), and the
 * face designer fills a die's faces with the same numbers
 * (`docs/face-designer.md`, "The stamp"). A drawn `6` and a printed `6` that
 * disagreed about the dot after them would be the same die reading two ways.
 */
object FaceLabel {
  /**
   * What a face is printed with: its label, or its value when the built-in
   * font cannot draw the label.
   *
   * A set may label a face `💀`, and the font has no skull. Printing a row of
   * blanks would make the die unreadable and printing a box would be a lie
   * about what the author wrote, so the *value* is printed — the one thing
   * about that face the app can always write down, and the thing the player is
   * about to read off it anyway (`docs/dice-sets.md`). It is written the way
   * an unlabelled face is written, [Face.printed], so a negative value comes
   * out with the same minus the rest of the app prints.
   *
   * An **empty** label is different and is left empty: a face with nothing on
   * it is a face an author asked for, and a blank side is what half a Fudge
   * die is.
   */
  fun textOf(at: Face): String =
    when {
      // A face an author deliberately left blank stays blank. A Fudge die's
      // nought is a real face of a real die and printing a `0` on it would be
      // the app arguing with the set file.
      at.label.isEmpty() -> ""
      BuiltinFont.canDraw(at.label) -> at.label
      // The value written the way a set file that gave no label would have had
      // it written ([Face.printed]) — a typographic minus and not the hyphen
      // `toString` produces. A die that fell back to its values should read
      // like a die that was never labelled, not like a different die.
      else -> Face.printed(at.value)
    }

  /**
   * Whether [text] has to be marked to be told from what it becomes when the
   * die is the other way up.
   *
   * The rule a real die follows, written down rather than hard-coded to `6`
   * and `9`: turn the label about, and if what comes out is a *different*
   * label that this same die also carries, a player cannot tell the two apart
   * and both are marked. A d6 has no `9`, so its `6` is left alone — which is
   * exactly what a moulded d6 does, and why the rule is worth stating this way
   * rather than as two characters by name.
   *
   * An `8` turns into itself and a `2` turns into nothing readable, so neither
   * is ever marked.
   *
   * **What the mark is** is not decided here: it is a trailing full stop,
   * `6.` and `9.`, and it is [Typesetter.MARK] because that is where the
   * writing happens (`docs/dice-sets.md`, "Labels").
   */
  fun isAmbiguous(
    text: String,
    die: Die,
  ): Boolean {
    val turned = turnedAbout(text) ?: return false
    if (turned == text) return false
    return die.faces.any { textOf(it) == turned }
  }

  /** [text] read upside down, or null when it does not read as anything. */
  private fun turnedAbout(text: String): String? {
    val turned = StringBuilder()
    text.reversed().forEach { turned.append(TURNS_INTO[it] ?: return null) }
    return turned.toString()
  }

  /**
   * What each character becomes when the die is turned about.
   *
   * Only the characters that still read as something: a `2` upside down is a
   * squiggle, and a label containing one can never be mistaken for another.
   */
  private val TURNS_INTO: Map<Char, Char> = mapOf('0' to '0', '1' to '1', '6' to '9', '8' to '8', '9' to '6')
}
