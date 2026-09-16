package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.simulation.api.DieMotion
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * A tray with dice in it, and nothing that decides anything.
 *
 * The physics is native and cannot be run on the JVM, but almost nothing about
 * a roll is physics: the spawn, the shake, when a die has stopped, whether it
 * may be touched, what it read and whether it has to be thrown again are all
 * decisions, and decisions belong where a test can reach them
 * (`docs/architecture.md`, decision 40). This interface is the line between
 * the two. [JoltWorld] is the one implementation that ships; the tests drive
 * the same loop against a world they can make do anything.
 *
 * Everything here is in millimetres and seconds, like the rest of
 * `:simulation:api`. Jolt's metres are on the far side of the JNI boundary and
 * do not leak through this interface.
 */
interface PhysicsWorld : AutoCloseable {
  /**
   * Adds one die. Dice are added in throw order and keep that index for the
   * life of the world, because that is what the outcome is reported against.
   *
   * @param hull the die's corners in its own space, already scaled — the same
   *   hull the renderer draws and the face reader reads
   *   (`docs/architecture.md`, decision 35).
   * @param material friction, restitution and density, already clamped.
   * @param placement where it starts and how hard it was thrown.
   */
  fun addDie(
    hull: List<Vector3>,
    material: DieMaterial,
    placement: Placement,
  )

  /** Called once after the last die, before the first step. */
  fun finish()

  /**
   * Which way down is, in mm/s².
   *
   * It is the only thing a shake changes. The tray is the screen and does not
   * move; the hand's acceleration reaches the dice as its inverse, added here
   * ([ShakeDriver]).
   */
  fun setGravity(gravity: Vector3)

  /** Advances by exactly [seconds]. Never a frame time. */
  fun step(seconds: Double)

  /** The dice as they are now, in throw order. The list is reused between steps. */
  fun readStates(): List<DieState>

  /**
   * How far one die has ever been inside another since the world was opened,
   * in millimetres.
   *
   * A property rather than something carried on [DieState], because it is a
   * fact about the *throw* rather than about a die at a moment — and because
   * the deepest overlap of a roll is usually reported on a step nobody kept.
   * A solver resolves overlaps rather than forbidding them, so this is never
   * exactly zero; Step 5.4 asks that it stays under two tenths of a
   * millimetre (`docs/TODO.md`).
   */
  val deepestDiePenetrationMm: Double

  /**
   * Adds [velocity] to a die's motion — rung 2 of the correction ladder.
   *
   * Whether this die may be touched at all was decided by
   * [de.drehtuer.dinfinity.simulation.api.CorrectionLadder.mayTouch] before
   * this was called. A world does not second-guess it; the gate is one place
   * or it is no gate.
   */
  fun applyBias(
    index: Int,
    velocity: Vector3,
  )

  /**
   * Picks one die up and throws it again, leaving every other die where it is.
   * The visible last resort, not a nudge (`docs/physics-and-rendering.md`).
   */
  fun respawn(
    index: Int,
    placement: Placement,
  )

  /**
   * Takes a die off the table, for good.
   *
   * Its face has been read, so it is out of play and the floor it stood on is
   * free for the dice still to be thrown. This is how a roll clears a heap
   * without touching anything: the dice that can be counted are counted and
   * lifted off, and the rest are thrown again onto a table with more room on
   * it than it had (`docs/physics-and-rendering.md`).
   *
   * Taking a counted die out of play is not *moving* it. Where it came to rest
   * stays readable afterwards, because that reading is part of the result, and
   * nothing puts it back.
   */
  fun remove(index: Int)
}

/**
 * Where a die starts, or restarts.
 *
 * @param position in the tray, millimetres, with the floor at `z = 0`.
 * @param rotation how it is turned as it is let go.
 * @param linearVelocity mm/s.
 * @param angularVelocity rad/s. Large on purpose: a die dropped without spin
 *   would land predictably from its starting orientation, which is not a roll
 *   (`docs/physics-and-rendering.md`, "Starting a roll").
 */
data class Placement(
  val position: Vector3,
  val rotation: Quaternion,
  val linearVelocity: Vector3,
  val angularVelocity: Vector3,
)

/**
 * One die, as the solver has it this step.
 *
 * @param position millimetres.
 * @param orientation what the face reader turns into a number.
 * @param motion how fast it is going, which is all the settle rule needs.
 * @param touchingFloor resting on or bouncing off the tray floor.
 * @param touchingWall against a wall. Together with a cocked reading this is
 *   the "leaning on a wall" case of the ladder's rung 2; on its own it is
 *   nothing to act on, and it is carried because the debug overlay
 *   (`docs/physics-and-rendering.md`, "Debug tooling") has no other source for
 *   it.
 * @param supportedByDie standing on another die. One of the two questions rung
 *   2 actually acts on.
 */
data class DieState(
  val position: Vector3,
  val orientation: Quaternion,
  val motion: DieMotion,
  val touchingFloor: Boolean,
  val touchingWall: Boolean,
  val supportedByDie: Boolean,
)
