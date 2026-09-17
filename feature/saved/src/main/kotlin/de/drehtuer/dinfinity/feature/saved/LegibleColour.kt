package de.drehtuer.dinfinity.feature.saved

import de.drehtuer.dinfinity.core.model.Contrast

/**
 * A colour somebody chose, moved far enough to be seen on the ground it is
 * printed on (`docs/dice-notation.md`, "Saved rolls").
 *
 * A roll's mark is drawn in its own colour tag, and a tag is only a tag while
 * it can be told from the paper: pale yellow on a light ground and ink on a
 * dark one are each a mark that is simply not there. So every colour goes
 * through here — the twelve and a custom one alike, because a clamp that only
 * the custom ones went through would be a promise that holds until somebody
 * switches to the dark theme with `ink` chosen.
 *
 * It steps the colour towards the ground's own text colour and stops at the
 * first step that reaches [Contrast.COMPONENT], so a colour that is already
 * legible is left exactly as it was chosen and one that is not is moved as
 * little as it takes. `design/dInfinityPhone.dc.html` does the same thing in
 * `legible()`, in the same ten steps.
 *
 * **To be replaced by `core/model`'s clamp** when the accent picker lands
 * (`docs/TODO.md`, Step 4.9, "The accent gains the system colour picker,
 * behind a contrast clamp"). That work puts one clamp in `core/model` for
 * every colour a player can choose, and this is the same arithmetic waiting
 * for it: the whole of the saved-roll side is this one function, called from
 * one place, so the merge is deleting this file and changing an import. It is
 * written here rather than there only because the two were built at the same
 * time. (Said in prose because detekt forbids the marker that word usually
 * carries, and `docs/TODO.md` is where the work is actually listed.)
 */
object LegibleColour {
  /** How many steps towards [towards] are tried before giving up on [argb]. */
  private const val STEPS = 10

  /**
   * [argb] on [background], moved towards [towards] until it can be seen.
   *
   * @param towards the ground's own text colour — what a mark falls back to
   *   when its colour cannot be rescued, which is the last step anyway.
   * @return the colour to draw with. [argb] itself whenever it already
   *   reaches [Contrast.COMPONENT] against [background].
   */
  fun legibleOn(
    argb: Int,
    background: Int,
    towards: Int,
  ): Int {
    for (step in 0..STEPS) {
      val moved = Contrast.over(towards, step.toDouble() / STEPS, argb)
      if (Contrast.meets(moved, background, Contrast.COMPONENT)) return moved
    }
    return towards
  }
}
