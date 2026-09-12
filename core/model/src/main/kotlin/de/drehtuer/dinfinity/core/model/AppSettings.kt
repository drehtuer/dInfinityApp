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
)
