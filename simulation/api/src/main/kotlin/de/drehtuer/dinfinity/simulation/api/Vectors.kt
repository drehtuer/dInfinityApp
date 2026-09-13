package de.drehtuer.dinfinity.simulation.api

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * A direction or a point in the tray, in millimetres.
 *
 * There is a small vector type here rather than a dependency because this
 * module has to stay plain Kotlin: it is where the table's geometry and the
 * rules for reading a die live, and every one of those has to be testable on
 * the JVM without a physics engine or an emulator (`docs/architecture.md`).
 */
data class Vector3(
  val x: Double,
  val y: Double,
  val z: Double,
) {
  /** How long this vector is. */
  val length: Double get() = sqrt(x * x + y * y + z * z)

  /** The same direction, one unit long. */
  fun normalised(): Vector3 {
    val size = length
    require(size > 0.0) { "a zero vector has no direction" }
    return Vector3(x / size, y / size, z / size)
  }

  /** How closely this points the same way as [other]; 1 for parallel unit vectors. */
  infix fun dot(other: Vector3): Double = x * other.x + y * other.y + z * other.z

  operator fun plus(other: Vector3): Vector3 = Vector3(x + other.x, y + other.y, z + other.z)

  operator fun minus(other: Vector3): Vector3 = Vector3(x - other.x, y - other.y, z - other.z)

  operator fun times(scale: Double): Vector3 = Vector3(x * scale, y * scale, z * scale)

  operator fun unaryMinus(): Vector3 = Vector3(-x, -y, -z)

  /** True when every component is within [tolerance] of [other]'s. */
  fun approximates(
    other: Vector3,
    tolerance: Double = 1e-9,
  ): Boolean = abs(x - other.x) <= tolerance && abs(y - other.y) <= tolerance && abs(z - other.z) <= tolerance

  companion object {
    val Zero: Vector3 = Vector3(0.0, 0.0, 0.0)

    /** Which way is up in the tray. Every face reading is measured against it. */
    val Up: Vector3 = Vector3(0.0, 0.0, 1.0)
  }
}

/**
 * How a die is turned, as a unit quaternion.
 *
 * The simulation reports an orientation and the face reader turns it into a
 * number; nothing else in the app needs to know what a quaternion is.
 */
data class Quaternion(
  val w: Double,
  val x: Double,
  val y: Double,
  val z: Double,
) {
  /** The same rotation, normalised — a solver's output drifts a little. */
  fun normalised(): Quaternion {
    val size = sqrt(w * w + x * x + y * y + z * z)
    require(size > 0.0) { "a zero quaternion is not a rotation" }
    return Quaternion(w / size, x / size, y / size, z / size)
  }

  /** [vector] turned by this rotation. */
  fun rotate(vector: Vector3): Vector3 {
    val unit = normalised()
    val u = Vector3(unit.x, unit.y, unit.z)
    val crossed = cross(u, vector)
    val twice = cross(u, crossed + vector * unit.w)
    return vector + twice * 2.0
  }

  companion object {
    /** No rotation at all: the shape in its reference orientation. */
    val Identity: Quaternion = Quaternion(1.0, 0.0, 0.0, 0.0)

    /** A turn of [radians] about [axis]. */
    fun about(
      axis: Vector3,
      radians: Double,
    ): Quaternion {
      val unit = axis.normalised()
      val half = radians / 2
      val sine = kotlin.math.sin(half)
      return Quaternion(kotlin.math.cos(half), unit.x * sine, unit.y * sine, unit.z * sine)
    }

    /** The shortest rotation taking [from] to [to]. */
    fun taking(
      from: Vector3,
      to: Vector3,
    ): Quaternion {
      val a = from.normalised()
      val b = to.normalised()
      val alignment = a dot b
      if (alignment > 1 - OPPOSITE_TOLERANCE) return Identity
      if (alignment < -1 + OPPOSITE_TOLERANCE) return about(anyPerpendicularTo(a), Math.PI)
      val axis = cross(a, b)
      return Quaternion(1 + alignment, axis.x, axis.y, axis.z).normalised()
    }

    private const val OPPOSITE_TOLERANCE = 1e-12
  }
}

internal fun cross(
  a: Vector3,
  b: Vector3,
): Vector3 =
  Vector3(
    a.y * b.z - a.z * b.y,
    a.z * b.x - a.x * b.z,
    a.x * b.y - a.y * b.x,
  )

private fun anyPerpendicularTo(direction: Vector3): Vector3 {
  val candidate = if (abs(direction.x) < abs(direction.z)) Vector3(1.0, 0.0, 0.0) else Vector3(0.0, 0.0, 1.0)
  return cross(direction, candidate).normalised()
}
