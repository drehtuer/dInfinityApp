package de.drehtuer.dinfinity.designer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * A stroke of the pen as the shapes its ink covers, which is how the Solid tab
 * draws it (`docs/face-designer.md`, "What the Solid view shows").
 */
class StrokeOutlineTest {
  private val nib = 0.1f

  @Test
  fun `a two-dot stroke is a disc at each end and a band between them`() {
    val rings = StrokeOutline.ringsOf(stroke(Dot(0.2f, 0.5f), Dot(0.8f, 0.5f)))

    assertEquals(3, rings.size)
    val (start, end, band) = rings
    assertEquals(StrokeOutline.DISC_CORNERS, start.size)
    start.forEach { assertEquals("a disc's corner is off its radius", nib / 2, hypot(it.x - 0.2f, it.y - 0.5f), 1e-5f) }
    end.forEach { assertEquals(nib / 2, hypot(it.x - 0.8f, it.y - 0.5f), 1e-5f) }
    assertEquals(4, band.size)
    // A horizontal band half a nib either side of the line.
    assertEquals(setOf(0.45f, 0.55f), band.map { Math.round(it.y * 100) / 100f }.toSet())
    assertEquals(setOf(0.2f, 0.8f), band.map { Math.round(it.x * 10) / 10f }.toSet())
  }

  @Test
  fun `every ring is wound the same way, whichever way the line was drawn`() {
    val rightwards = StrokeOutline.ringsOf(stroke(Dot(0.2f, 0.5f), Dot(0.8f, 0.5f), Dot(0.8f, 0.1f)))
    val leftwards = StrokeOutline.ringsOf(stroke(Dot(0.8f, 0.1f), Dot(0.8f, 0.5f), Dot(0.2f, 0.5f)))

    (rightwards + leftwards).forEach { ring ->
      assertTrue("a ring is wound against the rest", StrokeOutline.areaOf(ring) < 0)
    }
  }

  @Test
  fun `dots a finger left closer than a quarter of the nib are one dot`() {
    val dots = listOf(Dot(0f, 0f), Dot(0.01f, 0f), Dot(0.02f, 0f), Dot(0.5f, 0f), Dot(0.51f, 0f))

    assertEquals(listOf(Dot(0f, 0f), Dot(0.5f, 0f), Dot(0.51f, 0f)), StrokeOutline.thinned(dots, nib / 4))
    assertEquals("two dots are always two", dots.take(2), StrokeOutline.thinned(dots.take(2), nib / 4))
  }

  @Test
  fun `a segment of no length adds no band`() {
    val rings = StrokeOutline.ringsOf(stroke(Dot(0.5f, 0.5f), Dot(0.5f, 0.5f)))

    assertEquals("two discs and nothing between them", 2, rings.size)
  }

  @Test
  fun `a stroke with no width covers nothing`() {
    assertTrue(StrokeOutline.ringsOf(Stroke(listOf(Dot(0f, 0f), Dot(1f, 1f)), INK, width = 0f)).isEmpty())
    assertTrue(StrokeOutline.ringsOf(Stroke(emptyList(), INK, width = nib)).isEmpty())
  }

  private fun stroke(vararg dots: Dot): Stroke = Stroke(dots = dots.toList(), colorArgb = INK, width = nib)

  private companion object {
    const val INK = 0xFF000000.toInt()
  }
}
