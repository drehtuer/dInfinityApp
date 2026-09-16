package de.drehtuer.dinfinity.feature.roll

/**
 * What the tray has on it, in words (`docs/architecture.md`, "Accessibility").
 *
 * The tray is a `Surface` the roll thread draws to, and there is nothing in the
 * view hierarchy under it: to a screen reader it is a rectangle the size of the
 * screen with nothing in it at all. The whole of the app's home screen is that
 * rectangle, so the one thing it must not be is silent.
 *
 * It says how many dice and what they came to, and never *which faces* — those
 * are on the result sheet below, die by die, and saying them twice would make
 * every throw two announcements of the same thing.
 *
 * A sealed interface over [RollState] rather than a `when` at the draw site, so
 * that what the tray is said to hold in each state can be asserted on the JVM
 * and so the compiler catches a state nobody gave words to.
 */
internal sealed interface TrayReading {
  /** Nothing to throw: nothing typed, or a formula that cannot be thrown. */
  data object Empty : TrayReading

  /** Dice ready to go, not yet thrown. */
  data class Ready(
    val dice: Int,
  ) : TrayReading

  /** In the air. */
  data class Rolling(
    val dice: Int,
  ) : TrayReading

  /** Landed, and this is the total. */
  data class Settled(
    val total: Long,
  ) : TrayReading

  companion object {
    /**
     * What [state] leaves on the table.
     *
     * `Invalid` and `TooMany` are [Empty] rather than states of their own: no
     * body is ever created for either, so the tray really is empty, and *why*
     * is said where it can be fixed — under the formula, in the field.
     */
    fun of(state: RollState): TrayReading =
      when (state) {
        RollState.Empty, is RollState.Invalid, is RollState.TooMany -> Empty
        is RollState.Ready -> Ready(state.diceCount)
        is RollState.Rolling -> Rolling(state.diceCount)
        is RollState.Settled -> Settled(state.result.total)
      }
  }
}
