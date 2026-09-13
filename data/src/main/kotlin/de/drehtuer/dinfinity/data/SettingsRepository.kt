package de.drehtuer.dinfinity.data

import de.drehtuer.dinfinity.core.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes the player's settings.
 *
 * An interface because the theme reads it at the top of the composition, and a
 * test of a screen should not need a file on disk to render one.
 *
 * **One write rather than one setter per setting.** The list of settings is
 * still growing — appearance, shake, haptics, sound, rounding, the default set,
 * the table and the session are all named in `docs/TODO.md` — and an interface
 * with a method for each of them is an interface that has to change every time
 * somebody adds a checkbox. [update] takes what changed; the named operations
 * live beside it as extensions, so call sites still read like English and the
 * interface stays two members long.
 */
interface SettingsRepository {
  /** Emits the current settings and then every change to them. */
  val settings: Flow<AppSettings>

  /**
   * Applies [change] to what is stored.
   *
   * Read and write together, so two settings changed at once cannot lose one
   * of the two.
   */
  suspend fun update(change: (AppSettings) -> AppSettings)
}
