package de.drehtuer.dinfinity.feature.roll

import de.drehtuer.dinfinity.simulation.api.RollDiagnostics

/**
 * The steps every roll of this visit has dropped, added up — the second
 * number on the debug overlay's dropped-steps line
 * (`docs/physics-and-rendering.md`, "Debug tooling").
 *
 * A roll counts its own dropped steps and forgets them when it closes; the
 * question this is for — is the first throw of a session different? — is
 * about the rolls before this one as much as this one (`docs/TODO.md`,
 * Step 5.6).
 *
 * It tells one roll from the next by the snapshots alone: within a roll both
 * the steps taken and the steps dropped only ever grow, so a snapshot with
 * fewer of either belongs to a new roll, and the last figure of the old one is
 * banked. That keeps the overlay a watcher that is handed snapshots and
 * nothing else, rather than one the tray has to tell when a roll begins.
 *
 * Not thread-safe; fed on the roll thread, like the relay that owns it.
 */
class DroppedTally {
  private var banked = 0L
  private var lastSteps = 0
  private var lastDropped = 0

  /** Every step dropped this visit, the current roll's included. */
  val total: Long get() = banked + lastDropped

  /** Takes in one snapshot and returns [total] after it. */
  fun saw(diagnostics: RollDiagnostics): Long {
    if (diagnostics.steps < lastSteps || diagnostics.droppedSteps < lastDropped) banked += lastDropped
    lastSteps = diagnostics.steps
    lastDropped = diagnostics.droppedSteps
    return total
  }
}
