package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Contrast

/**
 * The ink that reads on a paper: black or white, whichever stands further
 * from it (`docs/face-designer.md`, "The guide").
 *
 * The designer draws on the die's own paper, and nothing on the screen can
 * assume what colour that is. The guide used to be drawn in the screen's own
 * ink at a third of its strength, which on a dark page is a pale grey — and a
 * pale grey on white paper is nothing at all, so turning the guide on or off
 * looked like a button that did nothing.
 */
object PaperInk {
  /** Black, for a light paper. */
  const val BLACK: Int = 0xFF000000.toInt()

  /** White, for a dark one. */
  const val WHITE: Int = 0xFFFFFFFF.toInt()

  /** Black or white, whichever has the greater contrast against [paperArgb]. */
  fun on(paperArgb: Int): Int =
    if (Contrast.ratio(BLACK, paperArgb) >=
      Contrast.ratio(WHITE, paperArgb)
    ) {
      BLACK
    } else {
      WHITE
    }
}
