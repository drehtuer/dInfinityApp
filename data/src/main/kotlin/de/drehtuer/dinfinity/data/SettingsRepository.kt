package de.drehtuer.dinfinity.data

import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the player's settings.
 *
 * An interface because the theme reads it at the top of the composition, and a
 * test of a screen should not need a file on disk to render one.
 */
interface SettingsRepository {
  /** Emits the current settings and then every change to them. */
  val settings: Flow<AppSettings>

  suspend fun setAccentColor(accent: AccentColor)
}
