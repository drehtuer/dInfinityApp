package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape

/**
 * Which die the face designer opens on
 * (`docs/face-designer.md`, "Flow" and "Quick mode").
 *
 * Two ways in and one rule. From the menu the designer opens on a die nobody
 * named, and from **"Doodle this die"** it opens on the die a player just
 * long-pressed in the breakdown — which is the whole of quick mode: the screen
 * is the same screen, and what the shortcut saves is the hunt through the
 * chooser for the die already in front of them.
 *
 * Here rather than in `:app`'s wiring because it is a decision with cases in
 * it — a named die that is no longer installed, a default set with no dice, a
 * set whose first die is a coin — and a decision in a wiring function is a
 * decision no unit test reaches (`docs/TODO.md`, "Coverage").
 */
object OpeningDie {
  /**
   * The die to open on, or null when there is no die at all to draw.
   *
   * In order: the die quick mode asked for; the default set's **d6**; that
   * set's first die; and finally any installed die.
   *
   * **The d6 rather than the first die of the set**, which is the d2: opening
   * a drawing app on a coin is a poor answer to "draw a die".
   *
   * **A named die that is not installed falls through to the same answer as no
   * name at all**, rather than opening a designer on nothing. It can happen: a
   * result stays on the roll screen after the package that threw it has been
   * removed, and the id in a long press is then an id the catalogue no longer
   * has.
   *
   * @param choosable every die a drawing can be started from — every die of
   *   every usable set, which is what the chooser lists.
   * @param fromDefaultSet the dice of the set a bare `d20` resolves against.
   * @param wanted the id quick mode asked for, or empty from the menu.
   */
  fun of(
    choosable: List<Die>,
    fromDefaultSet: List<Die> = emptyList(),
    wanted: String = "",
  ): Die? {
    val asked = choosable.firstOrNull { it.id == wanted }
    if (asked != null) return asked
    val opening = fromDefaultSet.ifEmpty { choosable }
    return opening.firstOrNull { it.shape == DieShape.Cube } ?: opening.firstOrNull()
  }
}
