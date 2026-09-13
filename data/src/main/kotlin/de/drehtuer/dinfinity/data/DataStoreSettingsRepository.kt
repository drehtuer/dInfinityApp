package de.drehtuer.dinfinity.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Settings on disk, in a Preferences DataStore.
 *
 * Settings are cosmetic and small. A file that cannot be read is therefore not
 * worth crashing over: [settings] falls back to the defaults and the next write
 * repairs it. That is only true for *read* failures — a write that fails still
 * throws, because silently not saving what someone just chose is worse than an
 * error.
 */
class DataStoreSettingsRepository(
  private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
  override val settings: Flow<AppSettings> =
    dataStore.data
      .catch { cause ->
        if (cause is IOException) emit(emptyPreferences()) else throw cause
      }.map { preferences ->
        AppSettings(
          accentColor = AccentColor.ofId(preferences[ACCENT_COLOUR]),
          powerSaving = preferences[POWER_SAVING] == true,
          welcomeSeen = preferences[WELCOME_SEEN] == true,
        )
      }

  override suspend fun setAccentColor(accent: AccentColor) {
    dataStore.edit { preferences -> preferences[ACCENT_COLOUR] = accent.id }
  }

  override suspend fun setPowerSaving(on: Boolean) {
    dataStore.edit { preferences -> preferences[POWER_SAVING] = on }
  }

  override suspend fun setWelcomeSeen() {
    dataStore.edit { preferences -> preferences[WELCOME_SEEN] = true }
  }

  companion object {
    /** The file this repository keeps, relative to the app's datastore directory. */
    const val FILE_NAME: String = "settings"

    private val ACCENT_COLOUR = stringPreferencesKey("accent_colour")
    private val POWER_SAVING = booleanPreferencesKey("power_saving")
    private val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
  }
}
