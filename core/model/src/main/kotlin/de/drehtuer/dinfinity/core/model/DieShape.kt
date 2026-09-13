package de.drehtuer.dinfinity.core.model

/**
 * The solids a die can be built from — the whole catalogue, closed in v1
 * (`docs/dice-sets.md`).
 *
 * A dice set varies a die's values, size, material and artwork; it never
 * varies its geometry. That is what lets the physics be tuned once and hold
 * for every set anyone ever installs, and it is why every die in every set is
 * one of eight solids whose fairness has been measured
 * (`docs/probability.md`, "Physics vs. probability").
 *
 * The enum is the catalogue. `:dicesets:format` reads [id] out of a
 * `diceset.toml` and rejects anything not listed here, including `mesh`, which
 * is designed but not implemented (`docs/dice-sets.md`, "Shapes after v1").
 *
 * @param id the name written in a set file. Stable: it is part of the file
 *   format, so renaming one breaks every set that uses it.
 * @param faceCount how many entries `faces` must have. For [Tetrahedron] this
 *   counts vertices rather than faces, because that is what is read — see
 *   [FaceRead].
 * @param naturalRead how this solid is read when the set file does not say.
 */
enum class DieShape(
  val id: String,
  val faceCount: Int,
  val naturalRead: FaceRead,
) {
  /** Two faces, for a d2. Landing on the rim counts as cocked. */
  Coin("coin", faceCount = 2, naturalRead = FaceRead.FaceUp),

  /**
   * The d4. A tetrahedron resting on a face never has a face pointing up, so
   * its values belong to vertices and [FaceRead.VertexUp] is its natural read.
   */
  Tetrahedron("tetrahedron", faceCount = 4, naturalRead = FaceRead.VertexUp),

  /** The d6, and the d2 of a set with no coin (`docs/dice-notation.md`). */
  Cube("cube", faceCount = 6, naturalRead = FaceRead.FaceUp),

  /** The d8. */
  Octahedron("octahedron", faceCount = 8, naturalRead = FaceRead.FaceUp),

  /** The d10, and the tens die of a d100 pair. */
  PentagonalTrapezohedron("pentagonal-trapezohedron", faceCount = 10, naturalRead = FaceRead.FaceUp),

  /** The d12. */
  Dodecahedron("dodecahedron", faceCount = 12, naturalRead = FaceRead.FaceUp),

  /** The d18. */
  EnneagonalTrapezohedron("enneagonal-trapezohedron", faceCount = 18, naturalRead = FaceRead.FaceUp),

  /** The d20. */
  Icosahedron("icosahedron", faceCount = 20, naturalRead = FaceRead.FaceUp),
  ;

  companion object {
    /**
     * The shape a set file's `shape = "…"` names, or `null` when the name is
     * not in the catalogue. Set files come from the internet, so this never
     * throws and never guesses: an unknown name is a validation error with a
     * message, not an exception (`docs/dice-sets.md`, "Validation").
     */
    fun ofId(id: String?): DieShape? = entries.firstOrNull { it.id == id }
  }
}

/**
 * Where a die's value is read from once it has come to rest
 * (`docs/physics-and-rendering.md`, "Settling and reading the result").
 *
 * @param id the name written in a set file as `read = "…"`.
 */
enum class FaceRead(
  val id: String,
) {
  /** The face whose outward normal points most nearly up. */
  FaceUp("face-up"),

  /** The vertex pointing up — the "top number" convention every d4 uses. */
  VertexUp("vertex-up"),
  ;

  companion object {
    /** What a set file means by `read = "…"`, or `null` if it means nothing. */
    fun ofId(id: String?): FaceRead? = entries.firstOrNull { it.id == id }
  }
}
