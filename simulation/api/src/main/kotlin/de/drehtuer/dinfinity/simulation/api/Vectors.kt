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

  /**
   * The turn this one is, [fraction] of the way towards [to].
   *
   * Spherical, not straight-line: a die halfway between two orientations has
   * turned halfway, and it turns at an even rate rather than rushing the
   * middle. That is what makes 120 Hz physics look smooth on a panel running
   * at some other rate — the renderer is shown the last two simulation states
   * and asks this where the die is *now* (`docs/physics-and-rendering.md`).
   *
   * It takes the short way round. A rotation has two quaternions, `q` and
   * `-q`, and blending towards the wrong one sends a die the long way about
   * for no reason anybody watching could explain.
   */
  fun slerp(
    to: Quaternion,
    fraction: Double,
  ): Quaternion {
    val from = normalised()
    val target = to.normalised()
    val alignment = from dot target
    val nearest = if (alignment < 0) -target else target
    val cosine = abs(alignment)
    if (cosine > STRAIGHT_LINE_ABOVE) {
      // Too close to tell apart, and the arc length underflows. A straight
      // line between two nearly equal rotations is the same answer.
      return (from * (1 - fraction) + nearest * fraction).normalised()
    }
    val angle = Exact.acos(cosine)
    val sine = Exact.sin(angle)
    return (from * (Exact.sin((1 - fraction) * angle) / sine) + nearest * (Exact.sin(fraction * angle) / sine))
      .normalised()
  }

  /** How closely this turn agrees with [other]; `±1` when they are the same. */
  infix fun dot(other: Quaternion): Double = w * other.w + x * other.x + y * other.y + z * other.z

  operator fun plus(other: Quaternion): Quaternion = Quaternion(w + other.w, x + other.x, y + other.y, z + other.z)

  operator fun times(scale: Double): Quaternion = Quaternion(w * scale, x * scale, y * scale, z * scale)

  operator fun unaryMinus(): Quaternion = Quaternion(-w, -x, -y, -z)

  companion object {
    /** Past this the two turns are the same turn, and the arc has no length. */
    private const val STRAIGHT_LINE_ABOVE = 0.9995

    /** No rotation at all: the shape in its reference orientation. */
    val Identity: Quaternion = Quaternion(1.0, 0.0, 0.0, 0.0)

    /** A turn of [radians] about [axis]. */
    fun about(
      axis: Vector3,
      radians: Double,
    ): Quaternion {
      val unit = axis.normalised()
      val half = radians / 2
      val sine = Exact.sin(half)
      return Quaternion(Exact.cos(half), unit.x * sine, unit.y * sine, unit.z * sine)
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

/**
 * The vector perpendicular to both, right-handed.
 *
 * Public because a mesh is built out of it: a face's texture frame and the
 * winding of its triangles are both cross products, and `render/filament`
 * has to agree with this module about which way "outward" is or it will draw
 * dice inside out (`docs/architecture.md`, decision 35).
 */
fun cross(
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
