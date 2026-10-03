package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.simulation.api.BoardPose
import de.drehtuer.dinfinity.simulation.api.BoardRequest
import de.drehtuer.dinfinity.simulation.api.BoardSettler
import de.drehtuer.dinfinity.simulation.api.BoardTrack
import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.Quaternion

/**
 * A board settler with no physics in it: every die slides straight down to the
 * felt and turns flat over [steps] steps from the step it is let go at, and a
 * die already down stays put.
 *
 * Enough for what the tray decides — which board is wanted, when it is drawn,
 * how far into it a frame is — and nothing of the real drop, which is the
 * device suite's (`BoardSettlerTest` in `simulation/jolt`).
 */
class FakeBoards(
  private val steps: Int = STEPS,
) : BoardSettler {
  /** Every board asked for, in order. */
  val asked: MutableList<BoardRequest> = mutableListOf()

  override fun settle(request: BoardRequest): BoardTrack {
    asked += request
    val recorder = BoardTrack.Recorder(request.bodies)
    val lastDrop = request.bodies.maxOfOrNull { it.firstStep } ?: 0
    for (step in 1..lastDrop + steps) {
      recorder.record(
        request.bodies.map { body ->
          // Each die falls for [steps] steps from the moment it is let go.
          val share = ((step - body.firstStep).toDouble() / steps).coerceIn(0.0, 1.0)
          val from = body.placement.position
          val floor = ClearSpace.radiusOf(body.die, body.dieScale)
          BoardPose(
            position = from.copy(z = from.z + (floor - from.z) * share),
            orientation = body.placement.rotation.slerp(Quaternion.Identity, share),
          )
        },
      )
    }
    return recorder.finish()
  }

  companion object {
    /** A fifth of a second of steps. */
    const val STEPS: Int = 24
  }
}
