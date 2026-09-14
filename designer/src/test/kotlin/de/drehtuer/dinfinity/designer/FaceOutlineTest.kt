package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieShape
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shape a cell is masked into (`docs/face-designer.md`; design `8d`).
 *
 * A cell is a face of the solid seen flat on, so the outline follows from the
 * shape. The one worth spelling out is the trapezohedron's, which people
 * misremember.
 */
class FaceOutlineTest {
  @Test
  fun `a d10's faces are kites, not the pentagons they look like from a distance`() {
    assertEquals(FaceOutline.Kite, FaceOutline.of(DieShape.PentagonalTrapezohedron))
    assertEquals(FaceOutline.Kite, FaceOutline.of(DieShape.EnneagonalTrapezohedron))
  }

  @Test
  fun `the pentagon is the d12's`() {
    assertEquals(FaceOutline.Pentagon, FaceOutline.of(DieShape.Dodecahedron))
  }

  @Test
  fun `three shapes are triangles, which is why the mask is shared`() {
    listOf(DieShape.Tetrahedron, DieShape.Octahedron, DieShape.Icosahedron).forEach {
      assertEquals(it.id, FaceOutline.Triangle, FaceOutline.of(it))
    }
  }

  @Test
  fun `the coin is a disc and the cube a square`() {
    assertEquals(FaceOutline.Circle, FaceOutline.of(DieShape.Coin))
    assertEquals(FaceOutline.Square, FaceOutline.of(DieShape.Cube))
  }

  @Test
  fun `every shape in the catalogue has an outline`() {
    // `of` is exhaustive over the enum, so a shape added without one would not
    // compile — this is what says so out loud, and what fails if the `when`
    // ever grows an `else`.
    DieShape.entries.forEach { shape ->
      assertEquals(shape.id, FaceOutline.of(shape), FaceOutline.of(shape))
    }
    assertEquals(DieShape.entries.size, DieShape.entries.map(FaceOutline::of).size)
  }
}
