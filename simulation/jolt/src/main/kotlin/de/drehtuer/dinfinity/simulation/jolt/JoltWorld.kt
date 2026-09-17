package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.DieMotion
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * [PhysicsWorld] over Jolt.
 *
 * The whole of it is unit conversion and array packing. Millimetres are what
 * the rest of the app speaks and centimetres are what the solver runs in; the
 * two meet in [Units] and nowhere else.
 *
 * This is the one class in the module that cannot run on the JVM, and it is
 * deliberately the one with nothing in it worth testing there — the device
 * suite proves it moves dice, and every decision it carries out was made and
 * tested upstairs.
 *
 * One method per thing the bridge can be asked to do, and that list is the
 * interface's rather than this class's: each is a single call across JNI with
 * no logic on this side, so splitting it would put half a bridge in one file
 * and half in another. The same reason [JoltNative] gives.
 */
@Suppress("TooManyFunctions")
class JoltWorld private constructor(
  private val handle: Long,
  maxDice: Int,
) : PhysicsWorld {
  private val stateBuffer = FloatArray(maxDice * JoltNative.STATE_STRIDE)
  private val placementBuffer = FloatArray(JoltNative.PLACEMENT_STRIDE)
  private val states = ArrayList<DieState>(maxDice)
  private var diceCount = 0
  private var closed = false

  override fun addDie(
    hull: List<Vector3>,
    material: DieMaterial,
    placement: Placement,
  ) {
    val clamped = material.clampedToLimits()
    val points = FloatArray(hull.size * JoltNative.AXES)
    hull.forEachIndexed { index, corner -> points.putLength(index * JoltNative.AXES, corner) }

    val created =
      JoltNative.nativeAddDie(
        world = handle,
        hull = points,
        convexRadius = Units.mmToUnits(convexRadiusMmFor(clamped)),
        // No conversion: the solver's units are centimetres and grams, so a
        // set file's grams per cubic centimetre is already what it wants.
        density = clamped.density.toFloat(),
        friction = clamped.friction.toFloat(),
        restitution = clamped.restitution.toFloat(),
        placement = pack(placement),
      )
    check(created) { "Jolt refused the hull of die $diceCount" }
    diceCount++
  }

  override fun finish() = JoltNative.nativeFinish(handle)

  override fun setGravity(gravity: Vector3) =
    JoltNative.nativeSetGravity(
      handle,
      Units.mmToUnits(gravity.x),
      Units.mmToUnits(gravity.y),
      Units.mmToUnits(gravity.z),
    )

  override fun step(seconds: Double) = JoltNative.nativeStep(handle, seconds.toFloat())

  override fun readStates(): List<DieState> {
    JoltNative.nativeReadStates(handle, stateBuffer)
    states.clear()
    for (index in 0 until diceCount) {
      val at = index * JoltNative.STATE_STRIDE
      val flags = stateBuffer[at + JoltNative.FLAGS].toInt()
      states +=
        DieState(
          position = stateBuffer.lengthAt(at),
          orientation = stateBuffer.rotationAt(at + JoltNative.ROTATION),
          motion =
            DieMotion(
              speedMmPerSecond = Units.unitsToMm(stateBuffer[at + JoltNative.SPEED]),
              // Angular speed is in radians either way: it has no length in it
              // to convert.
              spinRadiansPerSecond = stateBuffer[at + JoltNative.SPIN].toDouble(),
            ),
          touchingFloor = flags and JoltNative.FLAG_FLOOR != 0,
          touchingWall = flags and JoltNative.FLAG_WALL != 0,
          supportedByDie = flags and JoltNative.FLAG_SUPPORTED != 0,
        )
    }
    return states
  }

  override val deepestDiePenetrationMm: Double
    get() = Units.unitsToMm(JoltNative.nativeDeepestPenetration(handle))

  override fun applyBias(
    index: Int,
    velocity: Vector3,
  ) = JoltNative.nativeApplyBias(
    handle,
    index,
    Units.mmToUnits(velocity.x),
    Units.mmToUnits(velocity.y),
    Units.mmToUnits(velocity.z),
  )

  override fun remove(index: Int) = JoltNative.nativeRemove(handle, index)

  override fun respawn(
    index: Int,
    placement: Placement,
  ) = JoltNative.nativeRespawn(handle, index, pack(placement))

  /**
   * The body the solver actually built for die [index]
   * (`docs/physics-and-rendering.md`, "Are the dice fair").
   *
   * Nothing in a roll reads this, and nothing should: it is not a fact about
   * the throw, it is a fact about the die. A die is only fair if the solid the
   * engine collides is the solid the arithmetic describes, and asking the
   * engine is the only way to know which one it got.
   */
  fun bodyOf(index: Int): DieBody {
    val values = FloatArray(JoltNative.BODY_STRIDE)
    JoltNative.nativeReadBody(handle, index, values)
    val planes = FloatArray(MAX_FACES * JoltNative.PLANE_STRIDE)
    val faceCount = JoltNative.nativeReadFaces(handle, index, planes)
    return DieBody(
      centreOfMassMm = Vector3(Units.unitsToMm(values[0]), Units.unitsToMm(values[1]), Units.unitsToMm(values[2])),
      inertia = (0 until INERTIA_CELLS).map { values[JoltNative.AXES + it].toDouble() },
      faceCount = faceCount,
      // A normal has no length in it to convert; the distance does.
      faces =
        (0 until minOf(faceCount, MAX_FACES)).map { face ->
          val at = face * JoltNative.PLANE_STRIDE
          FacePlane(
            normal = Vector3(planes[at].toDouble(), planes[at + 1].toDouble(), planes[at + 2].toDouble()),
            distanceMm = Units.unitsToMm(planes[at + JoltNative.AXES]),
          )
        },
    )
  }

  override fun close() {
    if (closed) return
    closed = true
    JoltNative.nativeDestroy(handle)
  }

  /** Lays a placement out the way `dinfinity_jolt.cpp` reads it back. */
  private fun pack(placement: Placement): FloatArray {
    val rotation = placement.rotation.normalised()
    placementBuffer.putLength(JoltNative.X, placement.position)
    placementBuffer[JoltNative.ROTATION + JoltNative.QW] = rotation.w.toFloat()
    placementBuffer[JoltNative.ROTATION + JoltNative.QX] = rotation.x.toFloat()
    placementBuffer[JoltNative.ROTATION + JoltNative.QY] = rotation.y.toFloat()
    placementBuffer[JoltNative.ROTATION + JoltNative.QZ] = rotation.z.toFloat()
    placementBuffer.putLength(JoltNative.LINEAR_VELOCITY, placement.linearVelocity)
    // Radians per second on both sides: an angle has no length in it to scale.
    placementBuffer.putAngle(JoltNative.ANGULAR_VELOCITY, placement.angularVelocity)
    return placementBuffer
  }

  companion object {
    /**
     * How many face planes [bodyOf] will read back.
     *
     * The most any catalogue solid has is the coin's rim, at 24 sides plus its
     * two ends. The *count* is reported whatever it is, so a hull with more
     * faces than this is still visible as wrong — it is only the planes that
     * stop at the cap.
     */
    const val MAX_FACES: Int = 64

    /** A 3x3 tensor, flattened row by row. */
    private const val INERTIA_CELLS = 9

    /**
     * The rounded edge a solid gets, as a share of its size.
     *
     * Sharp corners are what catch on a floor instead of tumbling off it, and
     * a d4 is almost all corner (`docs/physics-and-rendering.md`, "Dice
     * bodies"). It is a share rather than a fixed millimetre so that a die
     * shrunk by the capacity rule keeps the same proportions it was tuned at.
     */
    const val CONVEX_RADIUS_SHARE: Double = 0.03

    /**
     * Opens a world for one throw. Null when the native library is not there,
     * which on a phone means the build is broken and on the JVM means the test
     * should have used a fake.
     */
    fun open(
      geometry: TableGeometry,
      table: TableLook,
      maxDice: Int,
    ): JoltWorld? {
      if (!JoltNative.available) return null
      val clamped = table.clampedToLimits()
      val tray =
        floatArrayOf(
          Units.mmToUnits(geometry.longSideMm / 2),
          Units.mmToUnits(geometry.shortSideMm / 2),
          Units.mmToUnits(geometry.wallHeightMm),
          Units.mmToUnits(geometry.ceilingHeightMm),
          Units.mmToUnits(geometry.cornerRadiusMm),
          clamped.friction.toFloat(),
          clamped.restitution.toFloat(),
        )
      // The other side reads exactly this many floats and would read whatever
      // followed them if this list ever grew.
      check(tray.size == JoltNative.TRAY_STRIDE) { "the tray is ${tray.size} floats, not 7" }

      val handle = JoltNative.nativeCreateWorld(tray, maxDice)
      if (!JoltNative.nativeOk(handle)) {
        JoltNative.nativeDestroy(handle)
        return null
      }
      return JoltWorld(handle, maxDice)
    }

    private fun convexRadiusMmFor(material: DieMaterial): Double = material.sizeMm * CONVEX_RADIUS_SHARE

    /** Writes a length in millimetres as three floats in simulation units. */
    private fun FloatArray.putLength(
      at: Int,
      value: Vector3,
    ) {
      this[at + JoltNative.X] = Units.mmToUnits(value.x)
      this[at + JoltNative.Y] = Units.mmToUnits(value.y)
      this[at + JoltNative.Z] = Units.mmToUnits(value.z)
    }

    /** And an angle, which needs no conversion. */
    private fun FloatArray.putAngle(
      at: Int,
      value: Vector3,
    ) {
      this[at + JoltNative.X] = value.x.toFloat()
      this[at + JoltNative.Y] = value.y.toFloat()
      this[at + JoltNative.Z] = value.z.toFloat()
    }

    /** Reads three floats back as millimetres. */
    private fun FloatArray.lengthAt(at: Int): Vector3 =
      Vector3(
        Units.unitsToMm(this[at + JoltNative.X]),
        Units.unitsToMm(this[at + JoltNative.Y]),
        Units.unitsToMm(this[at + JoltNative.Z]),
      )

    /** And four back as a rotation, laid out w-x-y-z. */
    private fun FloatArray.rotationAt(at: Int): Quaternion =
      Quaternion(
        w = this[at + JoltNative.QW].toDouble(),
        x = this[at + JoltNative.QX].toDouble(),
        y = this[at + JoltNative.QY].toDouble(),
        z = this[at + JoltNative.QZ].toDouble(),
      )
  }
}

/**
 * The one place millimetres become the unit the solver works in.
 *
 * The app is in millimetres because that is the unit a die is quoted in and the
 * unit the table's capacity rule is worked out in (`docs/tables.md`). The
 * solver runs in centimetres and grams, which makes a 16 mm die 1.6 units
 * across and 4.9 units heavy — squarely inside the range Jolt's tolerances
 * were written for (`docs/architecture.md`, decision 41).
 *
 * Metres would have been the obvious choice and it is the wrong one. At that
 * scale a die's inertia tensor is about 2·10⁻⁷, and Jolt tests a tensor for
 * being "near zero" against a hard-coded 10⁻¹² on its *squared* length. A die
 * falls under it, so Jolt quietly substitutes the inertia of a sphere a metre
 * across — nine thousand times too much to turn. The dice then land, slide and
 * spin for ever, because friction is real but far too weak to stop something
 * that heavy. It looks exactly like friction being broken and it is not.
 *
 * Density needs no conversion at all, which is the sign the unit is the right
 * one: a set file quotes grams per cubic centimetre because that is what a
 * dice maker quotes, and that is what the solver is handed.
 */
internal object Units {
  /** A simulation unit is this many millimetres: one centimetre. */
  const val MM_PER_UNIT: Double = 10.0

  fun mmToUnits(millimetres: Double): Float = (millimetres / MM_PER_UNIT).toFloat()

  fun unitsToMm(units: Float): Double = units.toDouble() * MM_PER_UNIT
}

/**
 * The [WorldFactory] that opens a real one, sized for this throw.
 *
 * It lives beside [JoltWorld] rather than beside [JoltDiceSimulator] because
 * it is the same thing: a line that can only run where the engine is. Every
 * other line of the simulator is decided on the JVM and tested there, and
 * keeping this one out of that file is what lets the coverage exclusion stay
 * as narrow as it is (`sonar-project.properties`).
 */
object JoltWorldFactory : WorldFactory {
  override fun open(spec: ThrowSpec): PhysicsWorld? =
    JoltWorld.open(
      geometry = spec.geometry,
      table = spec.table,
      maxDice = spec.dice.size,
    )
}

/**
 * What the solver made of a die (`docs/physics-and-rendering.md`, "Are the
 * dice fair").
 *
 * Read from the engine rather than computed, which is the whole point: the
 * catalogue's arithmetic already says what the solid should be, and what is
 * worth knowing is whether the engine agrees.
 *
 * @param centreOfMassMm where the die's mass sits, in its own space. A die
 *   whose mass is not at its centre is a loaded die.
 * @param inertia the 3x3 inertia tensor, row by row, in the solver's own
 *   units — grams and centimetres. Its *shape* is what matters rather than its
 *   scale: a solid with a symmetry has a tensor with the same symmetry.
 * @param faceCount how many faces the hull has, which is the first thing to
 *   check: a d18 that came out with seventeen is not a d18.
 * @param faces those faces' planes, up to [JoltWorld.MAX_FACES].
 */
data class DieBody(
  val centreOfMassMm: Vector3,
  val inertia: List<Double>,
  val faceCount: Int,
  val faces: List<FacePlane>,
)

/** One face of a hull: which way it points and how far out it sits. */
data class FacePlane(
  val normal: Vector3,
  val distanceMm: Double,
)
