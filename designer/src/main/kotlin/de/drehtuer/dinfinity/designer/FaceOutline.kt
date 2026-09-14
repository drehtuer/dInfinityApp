package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieShape

/**
 * The shape of one cell, which the canvas masks the drawing into
 * (`docs/face-designer.md`, "Flow"; design option `8d`).
 *
 * A cell is a face of the solid seen flat on, so the outline follows from the
 * shape and nothing else. Drawing outside it would be drawing on a part of the
 * atlas no face shows.
 */
enum class FaceOutline {
  /** The d2's, which is a disc rather than a polygon. */
  Circle,

  /** The d4, d8 and d20: equilateral triangles. */
  Triangle,

  /** The d6. */
  Square,

  /** The d12. */
  Pentagon,

  /**
   * The d10 and the d18.
   *
   * A trapezohedron's faces are kites, not pentagons — the shape people
   * misremember because a d10 *looks* like it has pentagonal faces from a
   * distance.
   */
  Kite,
  ;

  companion object {
    /** The outline a [shape]'s cells have. */
    fun of(shape: DieShape): FaceOutline =
      when (shape) {
        DieShape.Coin -> Circle
        DieShape.Tetrahedron, DieShape.Octahedron, DieShape.Icosahedron -> Triangle
        DieShape.Cube -> Square
        DieShape.Dodecahedron -> Pentagon
        DieShape.PentagonalTrapezohedron, DieShape.EnneagonalTrapezohedron -> Kite
      }
  }
}
