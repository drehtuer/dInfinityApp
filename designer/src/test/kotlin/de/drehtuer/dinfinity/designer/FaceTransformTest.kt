package de.drehtuer.dinfinity.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning and mirroring a copied face (`docs/face-designer.md`, "Copy and
 * paste").
 *
 * Arithmetic over the stored dots, so there is nothing to draw to find out
 * whether it is right — which is why it is tested here rather than through a
 * canvas.
 */
class FaceTransformTest {
  @Test
  fun `a transform that does nothing hands the marks straight back`() {
    val marks = listOf(stroke())

    assertTrue(FaceTransform().identity)
    assertEquals(marks, FaceTransform().applyTo(marks, FaceOutline.Square))
  }

  @Test
  fun `a transform says what it is, because the screen labels a button with it`() {
    val transform = FaceTransform(turns = 2, mirrored = true)

    assertEquals(2, transform.turns)
    assertTrue(transform.mirrored)
    assertFalse(transform.identity)
  }

  @Test
  fun `a whole turn of the cell comes back to where it started`() {
    val marks = listOf(stroke())

    val round = FaceTransform(turns = 4).applyTo(marks, FaceOutline.Square)

    assertClose(marks, round)
  }

  @Test
  fun `a quarter turn on a square puts the top of the drawing on its right`() {
    val top = Stroke(dots = listOf(Dot(0.5f, 0.1f)), colorArgb = BLACK, width = W)

    val turned = FaceTransform(turns = 1).applyTo(listOf(top), FaceOutline.Square)

    // Clockwise as the face is looked at: the top of it goes to the right.
    assertClose(listOf(Stroke(dots = listOf(Dot(0.9f, 0.5f)), colorArgb = BLACK, width = W)), turned)
  }

  @Test
  fun `half a turn on a square is the drawing upside down`() {
    val corner = Stroke(dots = listOf(Dot(0.2f, 0.3f)), colorArgb = BLACK, width = W)

    val turned = FaceTransform(turns = 2).applyTo(listOf(corner), FaceOutline.Square)

    assertClose(listOf(Stroke(dots = listOf(Dot(0.8f, 0.7f)), colorArgb = BLACK, width = W)), turned)
  }

  @Test
  fun `the mirror is the vertical one, left for right`() {
    val left = Stroke(dots = listOf(Dot(0.2f, 0.4f)), colorArgb = BLACK, width = W)

    val flipped = FaceTransform(mirrored = true).applyTo(listOf(left), FaceOutline.Square)

    assertClose(listOf(Stroke(dots = listOf(Dot(0.8f, 0.4f)), colorArgb = BLACK, width = W)), flipped)
  }

  @Test
  fun `mirroring twice is mirroring not at all`() {
    val marks = listOf(stroke())

    val there = FaceTransform(mirrored = true).applyTo(marks, FaceOutline.Square)
    val back = FaceTransform(mirrored = true).applyTo(there, FaceOutline.Square)

    assertClose(marks, back)
    assertNotEquals(marks, there)
  }

  @Test
  fun `the mirror goes on first and the turn after it`() {
    // Which is the order that makes "mirror, then turn twice" mean what it
    // says: mirror the drawing, then turn what you are looking at.
    val dot = Stroke(dots = listOf(Dot(0.2f, 0.3f)), colorArgb = BLACK, width = W)

    val both = FaceTransform(turns = 2, mirrored = true).applyTo(listOf(dot), FaceOutline.Square)

    // Mirrored to (0.8, 0.3), then half a turn to (0.2, 0.7).
    assertClose(listOf(Stroke(dots = listOf(Dot(0.2f, 0.7f)), colorArgb = BLACK, width = W)), both)
  }

  @Test
  fun `a fill is turned like anything else, because both are lists of dots`() {
    val fill = Fill(dots = listOf(Dot(0.2f, 0.3f)), colorArgb = RED)

    val turned = FaceTransform(turns = 2).applyTo(listOf(fill), FaceOutline.Square).single()

    assertTrue(turned is Fill)
    assertEquals(RED, turned.colorArgb)
    assertEquals(0.8f, turned.dots.single().x, 1e-5f)
  }

  @Test
  fun `a turn counts round rather than off the end`() {
    val marks = listOf(stroke())

    assertClose(
      FaceTransform(turns = 1).applyTo(marks, FaceOutline.Triangle),
      FaceTransform(turns = 4).applyTo(marks, FaceOutline.Triangle),
    )
    assertClose(
      FaceTransform(turns = 2).applyTo(marks, FaceOutline.Triangle),
      FaceTransform(turns = -1).applyTo(marks, FaceOutline.Triangle),
    )
  }

  @Test
  fun `a third of a turn carries a triangle onto itself`() {
    // A quarter would carry the drawing off the face and under the mask, which
    // is why the step is the cell's own.
    val marks: List<Mark> = listOf(stroke())

    val thrice =
      (1..3).fold(marks) { turned, _ -> FaceTransform(turns = 1).applyTo(turned, FaceOutline.Triangle) }

    assertClose(marks, thrice)
  }

  @Test
  fun `every cell gets the turn its shape has, and a kite gets none`() {
    assertEquals(3, FaceTransform.stepsOf(FaceOutline.Triangle))
    assertEquals(4, FaceTransform.stepsOf(FaceOutline.Square))
    assertEquals(5, FaceTransform.stepsOf(FaceOutline.Pentagon))
    assertEquals(4, FaceTransform.stepsOf(FaceOutline.Circle))
    assertEquals(1, FaceTransform.stepsOf(FaceOutline.Kite))
  }

  @Test
  fun `a kite still mirrors, which is the one symmetry it has`() {
    val left = Stroke(dots = listOf(Dot(0.3f, 0.5f)), colorArgb = BLACK, width = W)

    assertFalse(FaceTransform(mirrored = true).identity)
    assertClose(
      listOf(Stroke(dots = listOf(Dot(0.7f, 0.5f)), colorArgb = BLACK, width = W)),
      FaceTransform(turns = 1, mirrored = true).applyTo(listOf(left), FaceOutline.Kite),
    )
  }

  @Test
  fun `a dot carried off the canvas is left where the arithmetic put it`() {
    // It is clipped by the mask like any other ink. Squashing it back inside
    // would change the shape that was copied.
    val corner = Stroke(dots = listOf(Dot(0f, 0f)), colorArgb = BLACK, width = W)

    val turned = FaceTransform(turns = 1).applyTo(listOf(corner), FaceOutline.Triangle)

    assertTrue(
      "the drawing was squashed back onto the canvas",
      turned
        .single()
        .dots
        .single()
        .x > 1f,
    )
  }

  private fun stroke() = Stroke(dots = listOf(Dot(0.1f, 0.2f), Dot(0.7f, 0.9f)), colorArgb = BLACK, width = W)

  private fun assertClose(
    expected: List<Mark>,
    actual: List<Mark>,
  ) {
    assertEquals("a different number of marks", expected.size, actual.size)
    expected.zip(actual).forEach { (one, other) ->
      assertEquals(one.dots.size, other.dots.size)
      one.dots.zip(other.dots).forEach { (a, b) ->
        assertEquals(a.x, b.x, 1e-5f)
        assertEquals(a.y, b.y, 1e-5f)
      }
    }
  }

  private companion object {
    const val BLACK = 0xFF000000.toInt()
    const val RED = 0xFFEC3013.toInt()
    const val W = 0.02f
  }
}
