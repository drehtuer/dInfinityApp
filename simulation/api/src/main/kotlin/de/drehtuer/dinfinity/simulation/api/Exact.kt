package de.drehtuer.dinfinity.simulation.api

/**
 * The trigonometry anything upstream of the physics is allowed to use.
 *
 * `kotlin.math.sin` is `java.lang.Math.sin`, which the specification only
 * requires to be within 1 ulp of the true result and which every runtime is
 * free to implement as a hardware intrinsic. HotSpot on x86 and ART on an arm64
 * phone can therefore return two different doubles for the same angle, both of
 * them correct. [StrictMath] has no such freedom: it is fdlibm, reproducible
 * bit for bit on every platform, which is why it exists.
 *
 * One ulp does not stay one ulp. It is a die's starting quaternion, or a corner
 * of the hull the engine collides; a hundred steps of contacts later it is a
 * different face. Determinism is not a nicety here — it is what makes
 * power-saving mode provably the same roll, what the golden suite asserts, and
 * what makes a bug report reproducible (`docs/physics-and-rendering.md`,
 * "Timestep and determinism").
 *
 * Only the functions that are *allowed* to differ are here. `sqrt` is not: IEEE
 * 754 requires it to be correctly rounded, so it is already exact everywhere,
 * and so are the four arithmetic operators. Nothing downstream of the bridge
 * uses this — Jolt has its own answer to the same problem, which is being built
 * with `CROSS_PLATFORM_DETERMINISTIC=ON` (`docs/build-setup.md`).
 */
object Exact {
  /** [StrictMath.sin], for the reason this object exists. */
  fun sin(radians: Double): Double = StrictMath.sin(radians)

  /** [StrictMath.cos], for the reason this object exists. */
  fun cos(radians: Double): Double = StrictMath.cos(radians)

  /** [StrictMath.acos], for the reason this object exists. */
  fun acos(value: Double): Double = StrictMath.acos(value)

  /** [StrictMath.atan2], for the reason this object exists. */
  fun atan2(
    y: Double,
    x: Double,
  ): Double = StrictMath.atan2(y, x)
}
