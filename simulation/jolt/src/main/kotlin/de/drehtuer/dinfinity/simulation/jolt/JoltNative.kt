package de.drehtuer.dinfinity.simulation.jolt

import android.util.Log

/**
 * The JNI surface, and nothing else.
 *
 * Every method here is one call into `libdinfinity_jolt.so` with primitives and
 * float arrays. There is no logic to test on this side of the boundary either,
 * which is the point: what can go wrong with a roll goes wrong in the Kotlin
 * above [JoltWorld], where the JVM can reach it (`docs/architecture.md`,
 * decision 40).
 *
 * Arrays rather than objects because a roll is up to 1,440 steps of up to
 * eighty dice, and an object per die per step would cost more than the physics
 * it is reporting on. The offsets below are the wire format, and
 * `dinfinity_jolt.cpp` unpacks them in the same order — the two have to be read
 * together.
 *
 * There are as many functions here as the bridge has native entry points and
 * not one more, which is why the count is suppressed rather than split: a
 * second object would be the same surface behind two names.
 */
@Suppress("TooManyFunctions")
internal object JoltNative {
  private const val LIBRARY = "dinfinity_jolt"
  private const val TAG = "dinfinity.jolt"

  /** How many floats [nativeReadStates] writes per die. */
  const val STATE_STRIDE: Int = 10

  /** How many floats [nativeReadBody] writes: a centre of mass and a 3x3 inertia tensor. */
  const val BODY_STRIDE: Int = 3 + 9

  /** How many floats one face plane takes: a normal and a distance. */
  const val PLANE_STRIDE: Int = 4

  /** How many floats a [Placement] is packed into. */
  const val PLACEMENT_STRIDE: Int = 13

  /** How many floats the tray is described by. */
  const val TRAY_STRIDE: Int = 7

  /** Where a vector's components sit, wherever one is packed. */
  const val X: Int = 0

  const val Y: Int = 1

  const val Z: Int = 2

  /** Where a rotation starts in a placement or a state. */
  const val ROTATION: Int = 3

  /** And where its components sit within it: w first, then x, y, z. */
  const val QW: Int = 0

  const val QX: Int = 1

  const val QY: Int = 2

  const val QZ: Int = 3

  /** Where a placement's velocities sit. */
  const val LINEAR_VELOCITY: Int = 7

  const val ANGULAR_VELOCITY: Int = 10

  /** Where a state's scalars sit, after its position and rotation. */
  const val SPEED: Int = 7

  const val SPIN: Int = 8

  const val FLAGS: Int = 9

  /** How many components a vector has, which is also a hull point's stride. */
  const val AXES: Int = 3

  /** Touching the tray floor or its ceiling. */
  const val FLAG_FLOOR: Int = 1

  /** Touching a wall. */
  const val FLAG_WALL: Int = 2

  /** Standing on another die. */
  const val FLAG_SUPPORTED: Int = 4

  /**
   * True when `libdinfinity_jolt.so` is loaded and the bridge can be used.
   *
   * False on a JVM with no native library — which is every unit test, and is
   * why [PhysicsWorld] exists as an interface rather than as this object.
   */
  val available: Boolean = tryLoad()

  private fun tryLoad(): Boolean =
    try {
      System.loadLibrary(LIBRARY)
      true
    } catch (failure: UnsatisfiedLinkError) {
      // A physics bridge that cannot load is the app's whole reason for
      // existing failing to start, and it fails once, at class-load time,
      // where nobody is looking. Saying why in the log is the difference
      // between "the roll screen is broken" and a missing ABI.
      Log.e(TAG, "the physics bridge did not load", failure)
      false
    }

  external fun nativeCreateWorld(
    tray: FloatArray,
    maxDice: Int,
  ): Long

  @Suppress("LongParameterList")
  external fun nativeAddDie(
    world: Long,
    hull: FloatArray,
    convexRadius: Float,
    density: Float,
    friction: Float,
    restitution: Float,
    placement: FloatArray,
  ): Boolean

  external fun nativeFinish(world: Long)

  external fun nativeSetGravity(
    world: Long,
    x: Float,
    y: Float,
    z: Float,
  )

  external fun nativeStep(
    world: Long,
    seconds: Float,
  )

  external fun nativeReadStates(
    world: Long,
    out: FloatArray,
  )

  external fun nativeApplyBias(
    world: Long,
    index: Int,
    x: Float,
    y: Float,
    z: Float,
  )

  external fun nativeRespawn(
    world: Long,
    index: Int,
    placement: FloatArray,
  )

  /**
   * Writes [BODY_STRIDE] floats about the body Jolt built for a die: its
   * centre of mass, then its inertia tensor row by row.
   *
   * Nothing in a roll calls this. A die is only fair if the solid the engine
   * collides is the solid the arithmetic describes, and this is the only way
   * to ask which one it got (`docs/TODO.md`, Step 5.2).
   */
  external fun nativeReadBody(
    world: Long,
    index: Int,
    out: FloatArray,
  )

  /**
   * Writes the hull's face planes as normal-xyz then distance, as many as
   * [out] has room for, and returns how many faces the hull actually has.
   *
   * The count is the interesting half: a d18 whose hull came out with
   * seventeen faces is not a d18, however close it looks.
   */
  external fun nativeReadFaces(
    world: Long,
    index: Int,
    out: FloatArray,
  ): Int

  external fun nativeOk(world: Long): Boolean

  external fun nativeDestroy(world: Long)
}
