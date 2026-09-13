package de.drehtuer.dinfinity.core.model

/**
 * One die of a dice set: a catalogue solid with a value on every readable
 * position (`docs/dice-sets.md`).
 *
 * A die knows nothing about how many sides its *name* suggests. `d6` is the id
 * plain notation resolves to, but what it rolls is whatever [faces] says — so
 * a d6 with faces `1,2,1,2,1,2` is a d2 to the physics, to the breakdown and
 * to the outcome graph alike, with no special case anywhere
 * (`docs/dice-notation.md`, "d2").
 *
 * @param id the slug notation resolves, e.g. `d20` or `skull-d6`, unique
 *   within its set.
 * @param shape the solid. Fixes how many entries [faces] has.
 * @param faces one entry per readable position, in the catalogue's face order.
 * @param read where the value is taken from once the die stops. Defaults to
 *   the shape's [DieShape.naturalRead].
 * @param texturePath the die's atlas, relative to the set folder, or `null` to
 *   print [Face.label] instead.
 * @param material appearance and physics, [DieMaterial.clampedToLimits] by
 *   whoever built this die.
 */
data class Die(
  val id: String,
  val shape: DieShape,
  val faces: List<Face>,
  val read: FaceRead = shape.naturalRead,
  val texturePath: String? = null,
  val material: DieMaterial = DieMaterial(),
) {
  init {
    require(faces.size == shape.faceCount) {
      "Die '$id' is a ${shape.id} and needs ${shape.faceCount} faces, not ${faces.size}"
    }
  }

  /** The largest value this die can show — what `!` explodes on. */
  val maxValue: Int get() = faces.maxOf(Face::value)

  /** The smallest value this die can show. */
  val minValue: Int get() = faces.minOf(Face::value)

  /** What the die scores with [index] up. */
  fun valueAt(index: Int): Int = faces[index].value

  /**
   * The die's outcomes as a flat list of values, one entry per face, which is
   * exactly the uniform distribution the physics produces for a fair solid.
   * `:core:probability` turns this into a PMF without knowing anything else
   * about the die.
   */
  fun values(): List<Int> = faces.map(Face::value)

  companion object {
    /** Set files come from the internet; die ids are slugs (`docs/dice-sets.md`). */
    val IdPattern: Regex = Regex("[a-z0-9][a-z0-9-]{0,39}")

    /**
     * A plain die of [shape] whose faces run `1..faceCount` — what the
     * standard dice are and what most tests want.
     */
    fun standard(
      id: String,
      shape: DieShape,
      material: DieMaterial = DieMaterial(),
    ): Die =
      Die(
        id = id,
        shape = shape,
        faces = List(shape.faceCount) { Face.labelled(index = it, value = it + 1) },
        material = material,
      )
  }
}
