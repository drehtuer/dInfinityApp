package de.drehtuer.dinfinity.core.model

/**
 * Everything the player can change in Settings that the rest of the app reads.
 *
 * One value object rather than a preference per reader: a screen observes the
 * whole thing and recomposes once, and adding a setting does not mean adding
 * another stream to plumb through.
 */
data class AppSettings(
  val accentColor: AccentColor = AccentColor.Default,
  /**
   * Roll without drawing the dice (`design/dInfinity.dc.html`, option 1z).
   *
   * Off by default and **only ever changed here**: a roll that silently
   * stopped rendering because the battery dipped would be a surprise in the
   * middle of a game (`docs/architecture.md`, decision 16). It is the same
   * simulation either way, so the same seed gives the same faces — what is
   * saved is the drawing.
   */
  val powerSaving: Boolean = false,
)
