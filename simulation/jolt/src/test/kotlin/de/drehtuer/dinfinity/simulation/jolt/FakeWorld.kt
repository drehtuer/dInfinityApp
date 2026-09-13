package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.simulation.api.DieMotion
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * A world that does whatever the test tells it to.
 *
 * This is what [RollLoop] is for. The physics is native and cannot run here,
 * but the rules the loop keeps — nothing touches a settled die, a cocked die is
 * thrown again rather than nudged, the cap is a cap — are not physics, and
 * against this they can be tested on the JVM in milliseconds
 * (`docs/architecture.md`, decision 40).
 *
 * @param diceCount how many dice the throw has.
 * @param states what each die is doing, asked afresh every step. It is given
 *   the step, the die and how often that die has been thrown again, which is
 *   enough to write every case in the ladder without a script to keep in step
 *   with the loop.
 */
class FakeWorld(
  private val diceCount: Int,
  private val states: States,
) : PhysicsWorld {
  /** What each die is doing at one moment. */
  fun interface States {
    fun at(
      step: Int,
      index: Int,
      rethrows: Int,
    ): DieState
  }

  /** Every die added, in the order the roll spawned them. */
  val spawned: MutableList<Placement> = mutableListOf()

  /** And the hull each was given, which has exactly one legitimate source. */
  val hulls: MutableList<List<Vector3>> = mutableListOf()

  /** Every bias applied, as (step, die index). */
  val biases: MutableList<Pair<Int, Int>> = mutableListOf()

  /** And what each of them actually was, which is what "seeded" has to mean. */
  val biasVelocities: MutableList<Vector3> = mutableListOf()

  /** Every re-throw, as (step, die index). */
  val respawns: MutableList<Pair<Int, Int>> = mutableListOf()

  /** The gravity the loop set, once per step. */
  val gravities: MutableList<Vector3> = mutableListOf()

  /** How many steps were taken. */
  var steps: Int = 0
    private set

  /** True once the loop closed the world. */
  var closed: Boolean = false
    private set

  private val rethrows = IntArray(diceCount)

  override fun addDie(
    hull: List<Vector3>,
    material: DieMaterial,
    placement: Placement,
  ) {
    spawned += placement
    hulls += hull
  }

  override fun finish() = Unit

  override fun setGravity(gravity: Vector3) {
    gravities += gravity
  }

  override fun step(seconds: Double) {
    steps++
  }

  // The step a state belongs to is the one just taken, which is one less than
  // the number of steps the world has taken — so a test and `RollLoop` are
  // counting the same steps.
  override fun readStates(): List<DieState> =
    List(diceCount) { index -> states.at((steps - 1).coerceAtLeast(0), index, rethrows[index]) }

  override fun applyBias(
    index: Int,
    velocity: Vector3,
  ) {
    biases += steps to index
    biasVelocities += velocity
  }

  override fun respawn(
    index: Int,
    placement: Placement,
  ) {
    respawns += steps to index
    rethrows[index]++
  }

  override fun close() {
    closed = true
  }

  companion object {
    /** A die that has stopped dead. */
    fun settled(
      orientation: Quaternion = Quaternion.Identity,
      supportedByDie: Boolean = false,
    ): DieState =
      DieState(
        position = Vector3(0.0, 0.0, 8.0),
        orientation = orientation,
        motion = DieMotion.Stopped,
        touchingFloor = true,
        touchingWall = false,
        supportedByDie = supportedByDie,
      )

    /** A die still going, fast enough that nothing may touch it yet. */
    fun tumbling(orientation: Quaternion = Quaternion.Identity): DieState =
      DieState(
        position = Vector3(0.0, 0.0, 30.0),
        orientation = orientation,
        motion = DieMotion(speedMmPerSecond = 600.0, spinRadiansPerSecond = 20.0),
        touchingFloor = false,
        touchingWall = false,
        supportedByDie = false,
      )

    /** A die slowing inside the watching window — the only time it may be touched. */
    fun settling(
      orientation: Quaternion = Quaternion.Identity,
      supportedByDie: Boolean = false,
    ): DieState =
      DieState(
        position = Vector3(0.0, 0.0, 12.0),
        orientation = orientation,
        motion = DieMotion(speedMmPerSecond = 60.0, spinRadiansPerSecond = 1.0),
        touchingFloor = true,
        touchingWall = false,
        supportedByDie = supportedByDie,
      )
  }
}
