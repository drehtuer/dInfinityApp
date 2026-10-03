package de.drehtuer.dinfinity.simulation.api

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.TableLook

/**
 * Lets the dice waiting to be thrown fall onto the table and tumble to a stop
 * (`docs/physics-and-rendering.md`, "The dice waiting to be thrown").
 *
 * A die the player adds is dropped from above and comes down under real
 * physics, among the dice already on the board — which it may knock, because
 * that is what dropping a die among dice does. The whole drop is worked out at
 * once, in a world of its own that is closed before this returns, and is then
 * played back like a recording ([BoardTrack]).
 *
 * **It reports poses, never faces.** The board is not a roll: nothing here
 * reads which way up a die came to rest, nothing is scored, and the next shake
 * throws every die on the board from a spawn of its own as it always did. That
 * is held by the shape of this interface rather than by a promise: a
 * [BoardTrack] is positions and orientations and nothing else, and the only
 * thing that turns an orientation into a number (`FaceReader`) is never handed
 * one (`docs/architecture.md`, decision 67).
 *
 * Blocking, and slow enough to keep off any thread that draws: it is called on
 * a board thread of its own (`docs/architecture.md`, "Threading").
 */
fun interface BoardSettler {
  /** Every die of [request], step by step, until they have all stopped. */
  fun settle(request: BoardRequest): BoardTrack
}

/**
 * One board to let fall: the dice on it, each where it is now and how it is
 * moving, and the dice that are being dropped onto it.
 *
 * @param number which board of this visit this is. Boards are worked out off
 *   the thread that draws them, so one can arrive after the player has moved
 *   on; the number is how a board that is no longer wanted is recognised and
 *   dropped ([BoardDrops], `TrayRenderer`).
 * @param geometry the tray they fall into.
 * @param table its friction and restitution — the same table the throw after
 *   this one is thrown onto.
 * @param bodies every die of the board, in index order.
 */
data class BoardRequest(
  val number: Int,
  val geometry: TableGeometry,
  val table: TableLook,
  val bodies: List<BoardBody>,
)

/**
 * One die of a board.
 *
 * @param index which die of the formula this is, so the picture knows which
 *   die to draw where.
 * @param die what it is — its shape and its material.
 * @param dieScale how far the capacity rule shrank it (`docs/tables.md`).
 * @param placement where it starts and how it is moving: a die already
 *   standing starts where it stands at rest, a die still in the air keeps the
 *   momentum it had, and a die being added is released from above
 *   ([BoardDrops.release]).
 */
data class BoardBody(
  val index: Int,
  val die: Die,
  val dieScale: Double,
  val placement: Placement,
)
