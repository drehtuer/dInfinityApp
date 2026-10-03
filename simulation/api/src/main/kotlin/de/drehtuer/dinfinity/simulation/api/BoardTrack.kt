package de.drehtuer.dinfinity.simulation.api

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * A board's drop, recorded: where every die was at every step from the moment
 * it was let go until the last of them stopped
 * (`docs/physics-and-rendering.md`, "The dice waiting to be thrown").
 *
 * The drop is worked out once, in a world that is closed before the recording
 * is handed over ([BoardSettler]), and is then *played back*: the picture asks
 * where the dice are at some moment of the board's clock and is told, which is
 * arithmetic and not a step. So a board drawn at 60 Hz and one drawn at 120 Hz
 * are the same drop, a frame that arrives late finds the dice exactly where a
 * frame on time would have, and nothing goes on running between frames.
 *
 * **Positions and orientations, and nothing else.** There is no face here and
 * no way to ask for one: the board is not a roll, and what a die on it happens
 * to show is never read (`docs/architecture.md`, decision 67).
 *
 * Seven floats per die per step, laid out step by step — about 400 KB for the
 * largest board at the longest drop. Floats because the picture is drawn in
 * floats; a die carried from one board to the next goes through a float on the
 * way, and a float read back and written again is the same float, which is
 * what keeps a die that is standing still standing exactly still.
 *
 * @param indices which die of the formula each recorded die is, in the order
 *   they were recorded.
 * @param steps how many poses each die has: the one it was let go in, and one
 *   per step after that.
 */
class BoardTrack private constructor(
  val indices: List<Int>,
  val steps: Int,
  private val poses: FloatArray,
) {
  /** How many dice were recorded. */
  val dice: Int get() = indices.size

  /** When the last die stopped, on the board's clock. */
  val endsAt: Double get() = (steps - 1).coerceAtLeast(0) * SettleRule.TIMESTEP_SECONDS

  /** Whether the drop is over by [seconds] on the board's clock. */
  fun ended(seconds: Double): Boolean = seconds >= endsAt

  /** Every die as it came to rest — or as it was at the cap, if one never did. */
  val finalPoses: List<BoardPose> get() = List(dice) { poseAt(it, steps - 1) }

  /**
   * The step [seconds] falls in: the one just taken, never past the last.
   *
   * Together with [fractionAt] this is how the picture is drawn between two
   * recorded steps, exactly as a roll is ([fractionAt]).
   */
  fun stepAt(seconds: Double): Int {
    if (steps == 0) return 0
    val step = floor(seconds.coerceAtLeast(0.0) / SettleRule.TIMESTEP_SECONDS)
    return step.coerceAtMost((steps - 1).toDouble()).toInt()
  }

  /** How far [seconds] is from [stepAt] towards the step after it, 0 to 1. */
  fun fractionAt(seconds: Double): Double {
    if (ended(seconds)) return 0.0
    val into = seconds.coerceAtLeast(0.0) / SettleRule.TIMESTEP_SECONDS - stepAt(seconds)
    return into.coerceIn(0.0, 1.0)
  }

  /** Where recorded die [die] was at [step]. */
  fun poseAt(
    die: Int,
    step: Int,
  ): BoardPose {
    require(die in 0 until dice) { "the board recorded $dice dice, not a die $die" }
    require(step in 0 until steps) { "the board recorded $steps steps, not a step $step" }
    val at = (step * dice + die) * STRIDE
    return BoardPose(
      position = Vector3(poses[at + X].toDouble(), poses[at + Y].toDouble(), poses[at + Z].toDouble()),
      orientation =
        Quaternion(
          w = poses[at + QW].toDouble(),
          x = poses[at + QX].toDouble(),
          y = poses[at + QY].toDouble(),
          z = poses[at + QZ].toDouble(),
        ),
    )
  }

  /**
   * Where recorded die [die] is at [seconds] on the board's clock, between
   * the two steps either side of it.
   *
   * What a die carried over to the next board starts from: what is on screen
   * now, not the step before it.
   */
  fun poseAt(
    die: Int,
    seconds: Double,
  ): BoardPose {
    val step = stepAt(seconds)
    val before = poseAt(die, step)
    if (ended(seconds)) return before
    val after = poseAt(die, step + 1)
    val fraction = fractionAt(seconds)
    return BoardPose(
      position = before.position + (after.position - before.position) * fraction,
      orientation = before.orientation.slerp(after.orientation, fraction),
    )
  }

  /**
   * How fast recorded die [die] is going at [seconds], in mm/s and rad/s.
   *
   * Read off the recording rather than out of the world that made it, which
   * is long closed: the move from one step to the next, over the step. The
   * turn between the two orientations is taken the short way round — a
   * rotation has two quaternions, and the long way would be a die spinning
   * the wrong way at nearly a turn a step. Nothing once the drop is over.
   */
  fun velocitiesAt(
    die: Int,
    seconds: Double,
  ): Velocities {
    if (ended(seconds)) return Velocities.None
    val step = stepAt(seconds)
    val before = poseAt(die, step)
    val after = poseAt(die, step + 1)
    val linear = (after.position - before.position) * (1.0 / SettleRule.TIMESTEP_SECONDS)
    var turn = after.orientation.normalised() * before.orientation.normalised().conjugate()
    if (turn.w < 0.0) turn = -turn
    val axis = Vector3(turn.x, turn.y, turn.z)
    val sine = axis.length
    if (sine == 0.0) return Velocities(linear, Vector3.Zero)
    val angle = 2 * Exact.atan2(sine, turn.w)
    return Velocities(linear, axis * (angle / sine / SettleRule.TIMESTEP_SECONDS))
  }

  /**
   * Recorded die [die] at [seconds], as the start of the next board's drop.
   *
   * A die the settle rule would call still is handed over still: a die
   * standing on the board is at rest, and starting it with the hair's breadth
   * of drift a solver leaves would only cost it the promise that a die nothing
   * touches stays exactly where it is ([Recorder]).
   */
  fun placementAt(
    die: Int,
    seconds: Double,
  ): Placement {
    val pose = poseAt(die, seconds)
    val moving = velocitiesAt(die, seconds)
    val still =
      SettleRule.isStill(
        DieMotion(speedMmPerSecond = moving.linear.length, spinRadiansPerSecond = moving.angular.length),
      )
    val velocities = if (still) Velocities.None else moving
    return Placement(pose.position, pose.orientation, velocities.linear, velocities.angular)
  }

  override fun equals(other: Any?): Boolean =
    other is BoardTrack && indices == other.indices && steps == other.steps && poses.contentEquals(other.poses)

  override fun hashCode(): Int = 31 * (31 * indices.hashCode() + steps) + poses.contentHashCode()

  /**
   * Writes a drop down as it happens, starting from where every die was let
   * go.
   *
   * **A die nothing touched is recorded exactly where it stood.** A die that
   * started dead still and never moved further than [STILL_DRIFT_MM] or turned
   * further than [STILL_TURN_RADIANS] is written down at its starting pose on
   * every step, verbatim. That is a decision about the *picture*, not a force:
   * the solver is free to leave a die resting on the felt a few microns from
   * where it put it, and a standing die drawn shivering by that much each time
   * another is dropped would be a die visibly touched by nothing. A die that
   * really was knocked moves far further than this and is recorded as it moved.
   */
  class Recorder(
    private val bodies: List<BoardBody>,
  ) {
    private var poses = FloatArray(bodies.size * STRIDE * INITIAL_STEPS)
    private var steps = 0

    init {
      record(bodies.map { BoardPose(it.placement.position, it.placement.rotation) })
    }

    /** One step's poses, one per die, in the order the dice were given. */
    fun record(step: List<BoardPose>) {
      require(step.size == bodies.size) { "the board has ${bodies.size} dice, not ${step.size}" }
      val start = steps * bodies.size * STRIDE
      if (start + bodies.size * STRIDE > poses.size) poses = poses.copyOf(poses.size * 2)
      step.forEachIndexed { die, pose -> write(start + die * STRIDE, pose) }
      steps++
    }

    /** The recording, with every die nothing touched held exactly still. */
    fun finish(): BoardTrack {
      val recorded = poses.copyOf(steps * bodies.size * STRIDE)
      val track = BoardTrack(bodies.map { it.index }, steps, recorded)
      bodies.forEachIndexed { die, body ->
        if (untouched(track, die, body.placement)) {
          val start = track.poseAt(die, 0)
          for (step in 1 until steps) write(recorded, (step * bodies.size + die) * STRIDE, start)
        }
      }
      return track
    }

    private fun write(
      at: Int,
      pose: BoardPose,
    ) = write(poses, at, pose)

    private fun untouched(
      track: BoardTrack,
      die: Int,
      start: Placement,
    ): Boolean {
      if (start.linearVelocity.length != 0.0 || start.angularVelocity.length != 0.0) return false
      val from = track.poseAt(die, 0)
      return (1 until track.steps).all { step ->
        val pose = track.poseAt(die, step)
        (pose.position - from.position).length <= STILL_DRIFT_MM &&
          angleBetween(from.orientation, pose.orientation) <= STILL_TURN_RADIANS
      }
    }
  }

  companion object {
    /** Seven floats a die a step: a position, then a w-x-y-z orientation. */
    const val STRIDE: Int = 7

    /**
     * How far a die that started still may drift and still be drawn as never
     * having moved — a twentieth of a millimetre, well under a pixel at any
     * zoom the tray allows ([Recorder]).
     */
    const val STILL_DRIFT_MM: Double = 0.05

    /** And how far it may turn: about a tenth of a degree. */
    const val STILL_TURN_RADIANS: Double = 0.002

    /** A board with nothing on it. */
    val EMPTY: BoardTrack = BoardTrack(emptyList(), 0, FloatArray(0))

    // Where each number sits within a pose's seven floats.
    private const val X = 0
    private const val Y = 1
    private const val Z = 2
    private const val QW = 3
    private const val QX = 4
    private const val QY = 5
    private const val QZ = 6

    /** Room for about half a second before the recording first grows. */
    private const val INITIAL_STEPS = 64

    private fun write(
      into: FloatArray,
      at: Int,
      pose: BoardPose,
    ) {
      into[at + X] = pose.position.x.toFloat()
      into[at + Y] = pose.position.y.toFloat()
      into[at + Z] = pose.position.z.toFloat()
      into[at + QW] = pose.orientation.w.toFloat()
      into[at + QX] = pose.orientation.x.toFloat()
      into[at + QY] = pose.orientation.y.toFloat()
      into[at + QZ] = pose.orientation.z.toFloat()
    }

    /**
     * How far apart two orientations are, in radians.
     *
     * From the turn between them, by its sine and cosine together rather than
     * by an arc-cosine alone: near nought — the only place the answer matters
     * here — the arc-cosine of a float's worth of noise is a thousandth of a
     * radian on its own.
     */
    internal fun angleBetween(
      from: Quaternion,
      to: Quaternion,
    ): Double {
      val turn = to.normalised() * from.normalised().conjugate()
      val sine = sqrt(turn.x * turn.x + turn.y * turn.y + turn.z * turn.z)
      return 2 * Exact.atan2(sine, abs(turn.w))
    }
  }
}

/** One die's place and turn at one moment of a board's drop. */
data class BoardPose(
  val position: Vector3,
  val orientation: Quaternion,
)

/** How fast a die is going: mm/s along, rad/s about. */
data class Velocities(
  val linear: Vector3,
  val angular: Vector3,
) {
  companion object {
    /** Not moving at all. */
    val None: Velocities = Velocities(Vector3.Zero, Vector3.Zero)
  }
}
