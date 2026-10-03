package de.drehtuer.dinfinity.feature.roll

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.drehtuer.dinfinity.simulation.api.DebugWatch
import de.drehtuer.dinfinity.simulation.api.FrameMeter
import de.drehtuer.dinfinity.simulation.api.FrameRate
import de.drehtuer.dinfinity.simulation.api.RollDiagnostics

/**
 * Carries a roll's diagnostics from the roll thread to the screen, for the
 * debug overlay (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * It exists because the two ends are on different threads and neither may
 * reach the other's: the roll is stepped on the roll thread and Compose reads
 * on the main one, so a snapshot crosses through [toTheScreen] exactly as a
 * finished throw does (`docs/architecture.md`, "Threading").
 *
 * **It is a `DebugWatch` and therefore returns nothing.** That is the whole of
 * why an overlay is allowed to exist at all: it is a view, like the renderer,
 * and a view cannot change what the dice come to
 * (`docs/architecture.md`, decision 38; `simulation/jolt`'s
 * `RollDiagnosticsTest`).
 *
 * One of these is made per visit to the roll screen, and only when the
 * developer toggle is on. With it off there is no relay, the tray is given
 * [DebugWatch.NONE], and no snapshot is ever built.
 */
class DebugRelay(
  private val toTheScreen: (() -> Unit) -> Unit = { MAIN.post(it) },
) : DebugWatch {
  /**
   * The most recent snapshot, as Compose reads it.
   *
   * It stays after a roll has landed rather than being cleared: the dice have
   * stopped and the numbers they stopped with are exactly what somebody
   * debugging wants to read.
   */
  var latest: RollDiagnostics by mutableStateOf(RollDiagnostics.NONE)
    private set

  /**
   * Every step the rolls of this visit have dropped, the current one's
   * included ([DroppedTally]).
   */
  var droppedThisVisit: Long by mutableStateOf(0L)
    private set

  /**
   * How fast the tray is being drawn, or null before two frames in a row have
   * been (`docs/architecture.md`, decision 72).
   */
  var frameRate: FrameRate? by mutableStateOf(null)
    private set

  /** Roll-thread side: the frames and the dropped steps, before they cross. */
  private val meter = FrameMeter()
  private val tally = DroppedTally()

  /** Always, because a relay that exists at all was asked for. */
  override val watching: Boolean get() = true

  override fun saw(diagnostics: RollDiagnostics) {
    val dropped = tally.saw(diagnostics)
    toTheScreen {
      latest = diagnostics
      droppedThisVisit = dropped
    }
  }

  /**
   * One frame time, kept on the roll thread and sent across every
   * [POST_EVERY] frames.
   *
   * Not every frame: the readout is a figure over a hundred and twenty frames
   * and a person reads it a few times a second at most, so posting a new one
   * each vsync would be recomposing the overlay for a change nobody can see.
   */
  override fun framed(intervalNanos: Long) {
    meter.record(intervalNanos)
    if (meter.recorded % POST_EVERY != 0L) return
    val reading = meter.reading()
    toTheScreen { frameRate = reading }
  }

  companion object {
    private val MAIN = Handler(Looper.getMainLooper())

    /** A quarter of a second at 120 Hz, half a second at 60. */
    const val POST_EVERY = 30L
  }
}
