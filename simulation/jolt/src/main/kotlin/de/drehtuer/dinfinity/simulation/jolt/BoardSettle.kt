package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.simulation.api.BoardPose
import de.drehtuer.dinfinity.simulation.api.BoardRequest
import de.drehtuer.dinfinity.simulation.api.BoardTrack
import de.drehtuer.dinfinity.simulation.api.RestTracker
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry

/**
 * The longest a board's drop is recorded for: three seconds of steps.
 *
 * A dropped die is down and still in well under a second. A board that has
 * not stopped by three — a die balanced on another, rocking — is shown as it
 * was at the cap and left there; it is not a roll, so there is nothing to give
 * up on and nothing to re-throw. The next shake throws it like every other die.
 */
internal const val MOST_BOARD_STEPS: Int = 360

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
 * The world is the caller's to close.
 */
internal fun settleOn(
  world: PhysicsWorld,
  request: BoardRequest,
): BoardTrack {
  request.bodies.forEach { body ->
    world.addDie(
      hull = ShapeGeometry.hullOf(body.die, body.dieScale),
      material = body.die.material,
      placement = body.placement,
    )
  }
  world.finish()
  world.setGravity(ShakeDriver.DEFAULT_GRAVITY)

  val recorder = BoardTrack.Recorder(request.bodies)
  val tracker = RestTracker(request.bodies.size)
  while (tracker.stepsTaken < MOST_BOARD_STEPS) {
    world.step(SettleRule.TIMESTEP_SECONDS)
    val states = world.readStates()
    recorder.record(states.map { BoardPose(it.position, it.orientation) })
    if (tracker.step(states.map { it.motion })) break
  }
  return recorder.finish()
}
