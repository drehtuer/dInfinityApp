package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * Where a die is, as the sixteen numbers a renderer moves things with.
 *
 * A die's mesh is built one unit from the middle and the simulation reports a
 * position in millimetres and a turn as a quaternion; between them sits a
 * matrix, and building it is the sort of arithmetic that is wrong in a way
 * nobody can see until a die is drawn at the wrong size, in the wrong place,
 * turned the wrong way — three mistakes that look identical on a device.
 *
 * So it is here, in Kotlin, with tests, rather than left to the one file that
 * talks to Filament (`docs/architecture.md`, decision 40).
 */
object Transform {
  /** How many numbers a 4×4 matrix is. */
  const val SIZE: Int = 16

  /**
   * A die at [position], turned by [orientation], scaled by [scale].
   *
   * Column-major, because that is what Filament and OpenGL both read: the
   * three axes first, each as three numbers and a nought, then the position.
   * Row-major is the same sixteen numbers in an order that draws everything
   * mirrored through the diagonal, which is the mistake this comment exists
   * to stop.
   */
  fun of(
    position: Vector3,
    orientation: Quaternion = Quaternion.Identity,
    scale: Double = 1.0,
  ): FloatArray {
    val turn = orientation.normalised()
    val x = turn.rotate(Vector3(scale, 0.0, 0.0))
    val y = turn.rotate(Vector3(0.0, scale, 0.0))
    val z = turn.rotate(Vector3(0.0, 0.0, scale))
    return floatArrayOf(
      x.x.toFloat(),
      x.y.toFloat(),
      x.z.toFloat(),
      0.0f,
      y.x.toFloat(),
      y.y.toFloat(),
      y.z.toFloat(),
      0.0f,
      z.x.toFloat(),
      z.y.toFloat(),
      z.z.toFloat(),
      0.0f,
      position.x.toFloat(),
      position.y.toFloat(),
      position.z.toFloat(),
      1.0f,
    )
  }

  /** Nothing moved, nothing turned, nothing scaled. */
  fun identity(): FloatArray = of(Vector3.Zero)
}
