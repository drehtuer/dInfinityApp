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
    ): Face = Face(index = index, value = value, label = printed(value))

    /**
     * How [value] is written on a face when the set file gave no label.
     *
     * The digits, and a **typographic minus** (U+2212) rather than the hyphen
     * `Int.toString` produces. A hyphen is a word-joiner: it is drawn short,
     * high and thin, and beside the `+` on the next face of the same die it
     * does not read as the other half of a pair — which is the whole of what a
     * Fudge die is. The built-in font carries both characters, so this is a
     * choice about which one to print and not about what can be printed
     * (`docs/assets/README.md`). The bundled set writes its own labels this
     * way already, and so does the notation help; this is the same answer for
     * a set file that left `labels` out (`docs/dice-sets.md`, "Labels").
     *
     * It is only ever a **default**. A set that writes `labels` gets exactly
     * what it wrote, hyphen and all — the app does not correct an author's
     * typography.
     */
    fun printed(value: Int): String = if (value < 0) "$MINUS${-value}" else value.toString()

    /** U+2212, the minus a die is printed with. Not the hyphen on a keyboard. */
    const val MINUS: Char = '\u2212'
  }
}
