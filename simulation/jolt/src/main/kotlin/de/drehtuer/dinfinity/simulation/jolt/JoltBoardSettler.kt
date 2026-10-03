package de.drehtuer.dinfinity.simulation.jolt

import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.simulation.api.BoardRequest
import de.drehtuer.dinfinity.simulation.api.BoardSettler
import de.drehtuer.dinfinity.simulation.api.BoardTrack
import de.drehtuer.dinfinity.simulation.api.TableGeometry

/**
 * [BoardSettler] over Jolt: a world of its own for each board, opened, stepped
 * and closed before the recording is handed back.
 *
 * No world outlives the call — not between taps, and not while the board is
 * played back — so there is nothing to keep, nothing to leak and nothing for
 * the throw that follows to share (`docs/architecture.md`, decision 67).
 *
 * @param open how a world is opened. Jolt's by default; the tests hand in a
 *   world they control.
 */
class JoltBoardSettler(
  private val open: (TableGeometry, TableLook, Int) -> PhysicsWorld? = { geometry, table, dice ->
    JoltWorld.open(geometry, table, dice)
  },
) : BoardSettler {
  override fun settle(request: BoardRequest): BoardTrack {
    if (request.bodies.isEmpty()) return BoardTrack.EMPTY
    // No fallback, as for a throw (`JoltDiceSimulator.start`): a bridge that
    // will not open is a broken build, and a board drawn some other way would
    // hide it until the shake.
    val world =
      open(request.geometry, request.table, request.bodies.size)
        ?: error("the physics bridge would not open (libdinfinity_jolt)")
    return world.use { settleOn(it, request) }
  }
}
