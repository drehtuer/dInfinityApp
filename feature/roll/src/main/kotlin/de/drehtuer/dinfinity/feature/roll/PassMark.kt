package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.render.filament.PicturePoint

/**
 * One die a later pass of the roll put on the table, as the picture shows it
 * ([RollPresenter.passMarks]).
 *
 * @param outline the die's edge on the picture, corner by corner, as
 *   fractions of its width and height.
 * @param pass which pass of the roll put it there; never below
 *   [FIRST_MARKED].
 */
data class PassMark(
  val outline: List<PicturePoint>,
  val pass: Int,
) {
  companion object {
    /** The first pass that is marked. The first throw is what a roll is, and says nothing. */
    const val FIRST_MARKED: Int = 2
  }
}
