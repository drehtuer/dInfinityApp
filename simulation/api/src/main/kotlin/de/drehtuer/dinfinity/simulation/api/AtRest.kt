package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.Die

/**
 * Where a die stopped, and how it is turned.
 *
 * Reported beside the faces because a roll is not always over when its dice
 * stop: an explosion or a reroll adds a die to the same tray, and the next
 * throw has to know where the last one left everything — to find it clear floor
 * to land on, and to draw the dice it is landing among
 * (`docs/physics-and-rendering.md`, "The dice an explosion or a reroll adds").
 *
 * It comes out of the simulation, so it is the same in both modes: the tray a
 * player is watching and the tray nobody is looking at agree about where the
 * dice are, which is what keeps an added die's throw the same throw either way.
 */
data class RestingPlace(
  val position: Vector3,
  val orientation: Quaternion,
)

/**
 * A die that has already come to rest in the tray an added die is thrown into.
 *
 * It is **not** part of that throw's physics. Its face has been read, it is
 * finished, and there is no body for it in the world the added die is thrown
 * in — which is how "nothing touches a die that has come to rest" is kept here:
 * not by tuning, but because there is nothing there to touch. What it is for is
 * the two things outside the solver that still need it: where *not* to drop the
 * new die ([ClearSpace]), and what to draw around it.
 */
data class DieAtRest(
  val die: Die,
  val at: RestingPlace,
)
