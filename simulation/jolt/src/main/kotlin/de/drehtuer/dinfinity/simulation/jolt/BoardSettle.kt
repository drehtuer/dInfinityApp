package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.BoardDrops
import de.drehtuer.dinfinity.simulation.api.BoardPose
import de.drehtuer.dinfinity.simulation.api.BoardRequest
import de.drehtuer.dinfinity.simulation.api.BoardTrack
import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.DieMotion
import de.drehtuer.dinfinity.simulation.api.Placement
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.RestTracker
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3

/**
 * The longest a board's drop is recorded for after its last die is let go:
 * three seconds of steps.
 *
 * A dropped die is down and still in well under a second. A board that has
 * not stopped by three — a die balanced on another, rocking — is shown as it
 * was at the cap and left there; it is not a roll, so there is nothing to give
 * up on and nothing to re-throw. The next shake throws it like every other die.
 *
 * Counted from the last die let go rather than from the start, because the
 * dice of a handful are let go one after another
 * ([BoardDrops.DROP_INTERVAL_STEPS]): forty of them take four seconds to leave
 * the spot, and a cap counted from the start would stop the last of them in
 * the air.
 */
internal const val MOST_BOARD_STEPS: Int = 360

/**
 * How far under the tray a die waiting its turn to be let go is kept, in
 * millimetres.
 *
 * A die is a body in the world from the start — the bridge only adds bodies
 * before the first step — so one that is not yet let go has to be somewhere
 * it touches nothing: well below the floor, where it falls on its own until
 * its turn comes and it is picked up and let go over the spot
 * ([PhysicsWorld.respawn]). It is never drawn there ([BoardTrack.inPlay]).
 */
internal const val PARKED_BELOW_MM: Double = 1000.0

/**
 * Lets [request]'s dice fall in [world] until they stop, and records every
 * step (`docs/physics-and-rendering.md`, "The dice waiting to be thrown").
 *
 * Plain Kotlin over [PhysicsWorld], so it is tested on the JVM against a fake
 * world like the roll loop is (`docs/architecture.md`, decision 40). And what
 * it does *not* do is as deliberate as what it does: it never nudges a die,
 * never re-throws one, never takes one off, and never asks a die which face
 * is up. Gravity is set once, the world is stepped, the poses are written
 * down — a board is the dice falling and nothing else.
 *
 * **The dice being added are let go one at a time**, each at its own step
 * ([de.drehtuer.dinfinity.simulation.api.BoardBody.dropStep]). Until then a die
 * is parked under the tray in a row of its own, out of everything's way
 * ([PARKED_BELOW_MM]); at its step it is put over the spot once
 * ([PhysicsWorld.respawn]) — lifted over any die in play that it would
 * otherwise start inside ([BoardDrops.letGo]) — and from then on it is a die
 * like the others. That one placing is the only time anything moves a body
 * here, and it is a die that was not on the table yet.
 *
 * The world is the caller's to close.
 */
internal fun settleOn(
  world: PhysicsWorld,
  request: BoardRequest,
): BoardTrack {
  val bodies = request.bodies
  val radii = bodies.map { ClearSpace.radiusOf(it.die, it.dieScale) }
  val largest = radii.maxOrNull() ?: 0.0
  val poses = bodies.mapTo(mutableListOf()) { BoardPose(it.placement.position, it.placement.rotation) }
  val inPlay = BooleanArray(bodies.size) { bodies[it].dropStep == null }

  // Lets go every die whose turn is [step], in index order, each clear of the
  // dice in play by then — the ones let go just before it included.
  fun letGoAt(step: Int): List<Pair<Int, Placement>> =
    bodies.indices
      .filter { bodies[it].dropStep == step }
      .map { die ->
        val others = bodies.indices.filter { inPlay[it] }.map { poses[it].position }
        val placement = BoardDrops.letGo(request.geometry, bodies[die].placement, radii[die], largest, others)
        poses[die] = BoardPose(placement.position, placement.rotation)
        inPlay[die] = true
        die to placement
      }

  val atOnce = letGoAt(0).toMap()
  val spacing = 2 * largest + 2 * ClearSpace.CLEARANCE_MM
  bodies.forEachIndexed { die, body ->
    world.addDie(
      hull = ShapeGeometry.hullOf(body.die, body.dieScale),
      material = body.die.material,
      placement = atOnce[die] ?: if (inPlay[die]) body.placement else parked(die, spacing),
    )
  }
  world.finish()
  world.setGravity(ShakeDriver.DEFAULT_GRAVITY)

  val lastDrop = bodies.maxOfOrNull { it.firstStep } ?: 0
  val recorder = BoardTrack.Recorder(bodies, poses.toList())
  val tracker = RestTracker(bodies.size)
  while (tracker.stepsTaken < lastDrop + MOST_BOARD_STEPS) {
    world.step(SettleRule.TIMESTEP_SECONDS)
    val step = tracker.stepsTaken + 1
    val states = world.readStates()
    states.forEachIndexed { die, state ->
      if (inPlay[die]) poses[die] = BoardPose(state.position, state.orientation)
    }
    val motions =
      states.mapIndexedTo(mutableListOf()) { die, state -> if (inPlay[die]) state.motion else DieMotion.Stopped }
    letGoAt(step).forEach { (die, placement) ->
      world.respawn(die, placement)
      motions[die] = DieMotion(placement.linearVelocity.length, placement.angularVelocity.length)
    }
    recorder.record(poses.toList())
    if (tracker.step(motions) && step >= lastDrop) break
  }
  return recorder.finish()
}

/**
 * Where die [die] waits for its turn: under the tray, [spacing] along from the
 * die before it so that no two parked dice ever touch. They all fall the same
 * way from the same height, so they stay that far apart for as long as they
 * wait.
 */
internal fun parked(
  die: Int,
  spacing: Double,
): Placement =
  Placement(
    position = Vector3(die * spacing, 0.0, -PARKED_BELOW_MM),
    rotation = Quaternion.Identity,
    linearVelocity = Vector3.Zero,
    angularVelocity = Vector3.Zero,
  )
