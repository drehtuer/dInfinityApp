package de.drehtuer.dinfinity.core.model

/**
 * One readable position of a die — a face, or a vertex for a
 * [FaceRead.VertexUp] die — with the value it scores and the text printed on
 * it.
 *
 * The value carries the whole meaning: a cube with faces `1,2,1,2,1,2` *is* a
 * d2, and both the physics and the outcome graph treat it as one without being
 * told (`docs/probability.md`). Nothing anywhere maps a die id to a range of
 * numbers.
 *
 * @param index position in the shape's catalogue face order, `0` upwards. It
 *   is also the cell this face occupies in a texture atlas
 *   (`docs/dice-sets.md`, "Shape catalogue").
 * @param value what the die scores when this face lands up.
 * @param label what is printed when the die has no texture for this face, e.g.
 *   `"00"` on a tens d10 or `"💀"` on a symbol die.
 */
data class Face(
  val index: Int,
  val value: Int,
  val label: String,
) {
  companion object {
    /** The values a set file may give a face (`docs/dice-sets.md`). */
    val ValueRange: IntRange = -9999..9999

    /** How much text fits on a face before it stops being legible on a phone. */
    const val MAX_LABEL_LENGTH: Int = 4

    /** The face a set file gets when it gives a value and leaves `labels` out. */
    fun labelled(
      index: Int,
      value: Int,
    ): Face = Face(index = index, value = value, label = value.toString())
  }
}
