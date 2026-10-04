package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieShape

/**
 * The shape of one cell, which the canvas masks the drawing into
 * (`docs/face-designer.md`, "Flow"; design option `8d`).
 *
 * A cell is a face of the solid seen flat on, so the outline follows from the
 * shape and nothing else — and it is that face's own polygon, so a drawing that
 * fills the outline fills the face, no more and no less. Drawing outside it would be drawing on a part of the
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
   * The d10's: a kite.
   *
   * A trapezohedron's faces are kites, not pentagons — the shape people
   * misremember because a d10 *looks* like it has pentagonal faces from a
   * distance.
   */
  PentagonalKite,

  /**
   * The d18's: a kite too, but a longer and narrower one.
   *
   * A kite of its own rather than the d10's, because the two are not the same
   * shape and a mask has to be the face's own polygon to fit it. One kite drawn
   * for both left the exporter growing the drawing until it covered the face —
   * 1.20 times on a d10 and 1.37 on a d18, with the long tip clipped off
   * (`docs/face-designer.md`, "Export details").
   */
  EnneagonalKite,
  ;

  companion object {
    /** The outline a [shape]'s cells have. */
    fun of(shape: DieShape): FaceOutline =
      when (shape) {
        DieShape.Coin -> Circle
        DieShape.Tetrahedron, DieShape.Octahedron, DieShape.Icosahedron -> Triangle
        DieShape.Cube -> Square
        DieShape.Dodecahedron -> Pentagon
        DieShape.PentagonalTrapezohedron -> PentagonalKite
        DieShape.EnneagonalTrapezohedron -> EnneagonalKite
      }
  }
}
