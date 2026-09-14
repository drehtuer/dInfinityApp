package de.drehtuer.dinfinity.feature.stats

import de.drehtuer.dinfinity.core.stats.TotalBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the observed-against-expected chart puts its rectangles
 * (design option `8b`).
 *
 * The arithmetic is here rather than in the `Canvas` for the reason
 * `FaceHistogram` is not in one either: a draw lambda is the one place a test
 * cannot reach, so everything that can be wrong is kept out of it.
 */
class ChartShapesTest {
  @Test
  fun `a bar and a mark for every total`() {
    val shapes = ChartShapes.of(listOf(bar(2, 0.5, 0.25), bar(3, 0.5, 0.75)), WIDTH, HEIGHT)

    assertEquals(4, shapes.size)
    assertEquals(2, shapes.count(ChartShape::isMark))
  }

  @Test
  fun `both are scaled to the same tallest share`() {
    // The whole point of the picture: a mark above its bar means the total came
    // up less often than it should, and that reading only holds on one scale.
    val shapes = ChartShapes.of(listOf(bar(2, observed = 0.25, expected = 0.5)), WIDTH, HEIGHT)

    val ink = shapes.single { !it.isMark }
    val mark = shapes.single(ChartShape::isMark)
    assertEquals(HEIGHT / 2, ink.height, TOLERANCE)
    assertEquals(HEIGHT - HEIGHT, mark.top, TOLERANCE)
    assertTrue("the mark was not above the bar it beats", mark.top < ink.top)
  }

  @Test
  fun `the tallest thing on the chart reaches the top`() {
    val shapes = ChartShapes.of(listOf(bar(2, observed = 0.4, expected = 0.1)), WIDTH, HEIGHT)

    assertEquals(HEIGHT, shapes.single { !it.isMark }.height, TOLERANCE)
  }

  @Test
  fun `a total that never came up is still a rectangle, of no height`() {
    // So the shapes line up one-to-one with the bars, and a bar of zero sits
    // under its expected mark rather than vanishing.
    val shapes = ChartShapes.of(listOf(bar(2, observed = 0.0, expected = 0.5)), WIDTH, HEIGHT)

    assertEquals(0f, shapes.single { !it.isMark }.height, TOLERANCE)
    assertEquals(2, shapes.size)
  }

  @Test
  fun `no mark is drawn where the distribution says nothing`() {
    // An expected share of zero is the absence of a claim, not a claim that
    // the total is impossible. A line along the floor would look like one.
    val shapes = ChartShapes.of(listOf(bar(2, observed = 1.0, expected = 0.0)), WIDTH, HEIGHT)

    assertEquals(1, shapes.size)
    assertTrue(shapes.none(ChartShape::isMark))
  }

  @Test
  fun `the columns are evenly spaced and do not touch`() {
    val shapes = ChartShapes.of((2..4).map { bar(it, 0.3, 0.3) }, WIDTH, HEIGHT)

    val bars = shapes.filterNot(ChartShape::isMark)
    val step = WIDTH / 3

    assertEquals(3, bars.size)
    assertEquals("the columns are not evenly spaced", step, bars[1].left - bars[0].left, TOLERANCE)
    assertEquals(step, bars[2].left - bars[1].left, TOLERANCE)
    assertTrue("the bars fill their whole column, leaving no gap", bars.all { it.width < step })
    assertTrue("a bar started left of the chart", bars.all { it.left >= 0f })
    assertTrue("a bar ran off the right", bars.all { it.left + it.width <= WIDTH + TOLERANCE })
  }

  @Test
  fun `a mark sits over its own bar`() {
    val shapes = ChartShapes.of((2..4).map { bar(it, 0.3, 0.3) }, WIDTH, HEIGHT)

    shapes.filter(ChartShape::isMark).forEach { mark ->
      assertTrue(
        "a mark was not over a bar",
        shapes.any { !it.isMark && it.left == mark.left && it.width == mark.width },
      )
    }
  }

  @Test
  fun `nothing to draw is nothing drawn, rather than dividing by zero`() {
    assertEquals(emptyList<ChartShape>(), ChartShapes.of(emptyList(), WIDTH, HEIGHT))
    assertEquals(emptyList<ChartShape>(), ChartShapes.of(listOf(bar(2, 0.0, 0.0)), WIDTH, HEIGHT))
  }

  @Test
  fun `a chart with no room is not drawn`() {
    // A composable is measured before it is laid out, and zero is a size it
    // can be measured at.
    assertEquals(emptyList<ChartShape>(), ChartShapes.of(listOf(bar(2, 0.5, 0.5)), width = 0f, height = HEIGHT))
    assertEquals(emptyList<ChartShape>(), ChartShapes.of(listOf(bar(2, 0.5, 0.5)), width = WIDTH, height = 0f))
  }

  private fun bar(
    total: Int,
    observed: Double,
    expected: Double,
  ) = TotalBar(total = total, count = 0, observed = observed, expected = expected)

  private companion object {
    const val WIDTH = 300f
    const val HEIGHT = 100f
    const val TOLERANCE = 1e-4f
  }
}
