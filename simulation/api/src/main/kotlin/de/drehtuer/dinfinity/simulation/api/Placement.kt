package de.drehtuer.dinfinity.simulation.api

/**
 * Where a die starts, or restarts.
 *
 * Here rather than beside the physics world that takes it, because two things
 * hand one over: a throw's spawn, and the board of dice waiting to be thrown,
 * whose drops are worked out upstream of any engine ([BoardDrops]).
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
