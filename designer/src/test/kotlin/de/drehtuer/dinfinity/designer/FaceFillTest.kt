package de.drehtuer.dinfinity.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the bucket decides (`docs/face-designer.md`, "The fill bucket").
 *
 * All of it is arithmetic over the dots a finger left, which is the point of
 * it being here: a bucket tested through a canvas would be a bucket nobody
 * could tell was wrong.
 */
class FaceFillTest {
  @Test
  fun `a tap on bare paper fills the whole face`() {
    val fill = FaceFill.at(point = Dot(0.5f, 0.5f), marks = emptyList(), colorArgb = RED)

    assertEquals(FaceFill.FACE, fill.dots)
    assertEquals(RED, fill.colorArgb)
  }

  @Test
  fun `the face is the canvas square, which every renderer already masks`() {
    // A fill carrying its own copy of the cell outline could come to disagree
    // with the mask, and then the paper would be a different shape from the
    // face.
    assertEquals(listOf(Dot(0f, 0f), Dot(1f, 0f), Dot(1f, 1f), Dot(0f, 1f)), FaceFill.FACE)
  }

  @Test
  fun `a tap inside a closed shape fills that shape`() {
    val box = closed(0.2f, 0.8f)

    val fill = FaceFill.at(point = Dot(0.5f, 0.5f), marks = listOf(box), colorArgb = RED)

    assertEquals(box.dots, fill.dots)
  }

  @Test
  fun `a tap outside every shape still fills the face`() {
    val box = closed(0.2f, 0.4f)

    val fill = FaceFill.at(point = Dot(0.9f, 0.9f), marks = listOf(box), colorArgb = RED)

    assertEquals(FaceFill.FACE, fill.dots)
  }

  @Test
  fun `shapes nest, and the smallest one the tap is inside wins`() {
    // A tap in the eye of a skull is inside the eye and inside the skull, and
    // what the finger meant is the eye.
    val skull = closed(0.1f, 0.9f)
    val eye = closed(0.4f, 0.6f)

    val fill = FaceFill.at(point = Dot(0.5f, 0.5f), marks = listOf(skull, eye), colorArgb = RED)

    assertEquals(eye.dots, fill.dots)
  }

  @Test
  fun `an eraser loop is not a boundary`() {
    val rubbed = closed(0.2f, 0.8f).copy(erases = true)

    val fill = FaceFill.at(point = Dot(0.5f, 0.5f), marks = listOf(rubbed), colorArgb = RED)

    assertEquals(FaceFill.FACE, fill.dots)
  }

  @Test
  fun `a fill is not a boundary either`() {
    // Filling inside the paper is what filling the face already does.
    val paper = Fill(dots = closed(0.2f, 0.8f).dots, colorArgb = WHITE)

    val fill = FaceFill.at(point = Dot(0.5f, 0.5f), marks = listOf(paper), colorArgb = RED)

    assertEquals(FaceFill.FACE, fill.dots)
  }

  @Test
  fun `a line whose ends are apart encloses nothing`() {
    val open = Stroke(dots = listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.2f), Dot(0.8f, 0.8f)), colorArgb = BLACK, width = W)

    assertFalse(FaceFill.closed(open))
    assertEquals(FaceFill.FACE, FaceFill.at(Dot(0.5f, 0.3f), listOf(open), RED).dots)
  }

  @Test
  fun `a finger that lands near enough where it started has closed the shape`() {
    // It never lands on the same pixel, and demanding that it did would make
    // the bucket useless.
    val nearly =
      Stroke(
        dots = listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.2f), Dot(0.8f, 0.8f), Dot(0.2f, 0.8f), Dot(0.22f, 0.24f)),
        colorArgb = BLACK,
        width = W,
      )

    assertTrue(FaceFill.closed(nearly))
  }

  @Test
  fun `two dots are a line however near their ends are`() {
    val doubledBack = Stroke(dots = listOf(Dot(0.5f, 0.5f), Dot(0.5f, 0.52f)), colorArgb = BLACK, width = W)

    assertFalse(FaceFill.closed(doubledBack))
  }

  @Test
  fun `a point inside a square is inside it, and one outside is not`() {
    val square = closed(0.2f, 0.8f).dots

    assertTrue(Polygon.contains(square, Dot(0.5f, 0.5f)))
    assertFalse(Polygon.contains(square, Dot(0.1f, 0.5f)))
    assertFalse(Polygon.contains(square, Dot(0.5f, 0.95f)))
  }

  @Test
  fun `a shape with a bite out of it is not filled where the bite is`() {
    // The even-odd rule needs no convexity, which matters because the boundary
    // is whatever a finger drew.
    val bitten =
      listOf(
        Dot(0.1f, 0.1f),
        Dot(0.9f, 0.1f),
        Dot(0.9f, 0.9f),
        Dot(0.5f, 0.2f),
        Dot(0.1f, 0.9f),
      )

    assertTrue(Polygon.contains(bitten, Dot(0.2f, 0.3f)))
    assertFalse(Polygon.contains(bitten, Dot(0.5f, 0.8f)))
  }

  @Test
  fun `nothing is inside a boundary of fewer than three dots, and it covers nothing`() {
    assertFalse(Polygon.contains(listOf(Dot(0f, 0f), Dot(1f, 1f)), Dot(0.5f, 0.5f)))
    assertEquals(0f, Polygon.area(listOf(Dot(0f, 0f), Dot(1f, 1f))), 1e-6f)
  }

  @Test
  fun `area is the shoelace, whichever way round the dots go`() {
    val square = closed(0.2f, 0.8f).dots

    assertEquals(0.36f, Polygon.area(square), 1e-5f)
    assertEquals(0.36f, Polygon.area(square.reversed()), 1e-5f)
    assertEquals(1f, Polygon.area(FaceFill.FACE), 1e-6f)
  }

  /** A square drawn clockwise from [from] to [to], its last dot back at its first. */
  private fun closed(
    from: Float,
    to: Float,
  ) = Stroke(
    dots = listOf(Dot(from, from), Dot(to, from), Dot(to, to), Dot(from, to), Dot(from, from)),
    colorArgb = BLACK,
    width = W,
  )

  private companion object {
    const val RED = 0xFFEC3013.toInt()
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val W = 0.02f
  }
}
