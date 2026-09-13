package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * The matrix a die is drawn by.
 *
 * Drawn at the wrong size, in the wrong place, and turned the wrong way look
 * identical on a device — a die that is simply not where it should be — so the
 * three are told apart here, where each is a number.
 */
class TransformTest {
  @Test
  fun `nothing moved is the identity, in the order a renderer reads it`() {
    val identity = Transform.identity()

    assertEquals(Transform.SIZE, identity.size)
    listOf(0, 5, 10, 15).forEach { assertEquals("the diagonal", 1.0f, identity[it], TOLERANCE) }
    identity.indices.filterNot { it % 5 == 0 }.forEach {
      assertEquals(
        "off the diagonal",
        0.0f,
        identity[it],
        TOLERANCE,
      )
    }
  }

  @Test
  fun `a position is the last column, which is what column-major means`() {
    // Row-major is the same sixteen numbers in an order that draws everything
    // mirrored through the diagonal. The position is where the two differ
    // most visibly and most cheaply.
    val moved = Transform.of(Vector3(12.0, -34.0, 56.0))

    assertEquals(12.0f, moved[12], TOLERANCE)
    assertEquals(-34.0f, moved[13], TOLERANCE)
    assertEquals(56.0f, moved[14], TOLERANCE)
    assertEquals(1.0f, moved[15], TOLERANCE)
  }

  @Test
  fun `a scale stretches the axes and leaves the position alone`() {
    val scaled = Transform.of(Vector3(1.0, 2.0, 3.0), scale = 4.0)

    assertEquals("the x axis", 4.0f, scaled[0], TOLERANCE)
    assertEquals("the y axis", 4.0f, scaled[5], TOLERANCE)
    assertEquals("the z axis", 4.0f, scaled[10], TOLERANCE)
    assertEquals("a scaled die is not moved as well", 1.0f, scaled[12], TOLERANCE)
  }

  @Test
  fun `a turn puts the die's own axes in the first three columns`() {
    val quarter = Quaternion.about(Vector3(0.0, 0.0, 1.0), PI / 2)

    val turned = Transform.of(Vector3.Zero, quarter)

    assertAxis(quarter.rotate(Vector3(1.0, 0.0, 0.0)), turned, column = 0)
    assertAxis(quarter.rotate(Vector3(0.0, 1.0, 0.0)), turned, column = 1)
    assertAxis(quarter.rotate(Vector3(0.0, 0.0, 1.0)), turned, column = 2)
  }

  @Test
  fun `a turn and a scale are both there, and do not cancel`() {
    val turn = Quaternion.about(Vector3(1.0, 1.0, 0.0), PI / 3)

    val both = Transform.of(Vector3.Zero, turn, scale = 2.5)

    assertAxis(turn.rotate(Vector3(2.5, 0.0, 0.0)), both, column = 0)
    assertEquals("a turned die is still the size it was thrown at", 2.5, lengthOf(both, column = 1), LOOSE)
  }

  @Test
  fun `a turn that is not quite a unit quaternion is still a turn`() {
    // The solver's output drifts, and a matrix built from a drifted
    // quaternion would quietly resize the die it draws.
    val drifted = Quaternion(w = 0.9, x = 0.9, y = 0.0, z = 0.0)

    val matrix = Transform.of(Vector3.Zero, drifted)

    repeat(AXES) { assertEquals("axis $it", 1.0, lengthOf(matrix, it), LOOSE) }
  }

  @Test
  fun `the bottom row is what makes it a position rather than a direction`() {
    val matrix = Transform.of(Vector3(5.0, 5.0, 5.0), scale = 3.0)

    listOf(3, 7, 11).forEach { assertEquals(0.0f, matrix[it], TOLERANCE) }
    assertEquals(1.0f, matrix[15], TOLERANCE)
  }

  private fun assertAxis(
    expected: Vector3,
    matrix: FloatArray,
    column: Int,
  ) {
    val actual = axisAt(matrix, column)
    assertTrue("column $column is $actual, not $expected", expected.approximates(actual, LOOSE))
  }

  /** The three numbers of one column, as the axis it stands for. */
  private fun axisAt(
    matrix: FloatArray,
    column: Int,
  ): Vector3 =
    Vector3(
      matrix[column * COLUMN].toDouble(),
      matrix[column * COLUMN + 1].toDouble(),
      matrix[column * COLUMN + 2].toDouble(),
    )

  private fun lengthOf(
    matrix: FloatArray,
    column: Int,
  ): Double = axisAt(matrix, column).length

  private companion object {
    const val TOLERANCE = 1e-7f

    /** Built as floats out of doubles, so compared as floats. */
    const val LOOSE = 1e-6

    /** Four numbers to a column of a 4x4. */
    const val COLUMN = 4

    const val AXES = 3
  }
}
