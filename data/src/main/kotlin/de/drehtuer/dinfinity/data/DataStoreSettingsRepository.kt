package de.drehtuer.dinfinity.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.AppSettings
import de.drehtuer.dinfinity.core.model.Appearance
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Rounding
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.model.TablePin
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
 *
 * Every key is written on every change rather than only the one that moved.
 * `edit` is one atomic transaction either way, the file is a handful of
 * values, and writing the whole of what was decided means a setting can never
 * be half-applied.
 */
class DataStoreSettingsRepository(
  private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
  override val settings: Flow<AppSettings> =
    dataStore.data
      .catch { cause ->
        if (cause is IOException) emit(emptyPreferences()) else throw cause
      }.map(::settingsOf)

  override suspend fun update(change: (AppSettings) -> AppSettings) {
    dataStore.edit { preferences ->
      val changed = change(settingsOf(preferences))
      preferences[ACCENT_COLOUR] = changed.accentColor.id
      preferences[APPEARANCE] = changed.appearance.id
      preferences[POWER_SAVING] = changed.powerSaving
      preferences[SHAKE_TO_ROLL] = changed.shakeToRoll
      preferences[HAPTICS] = changed.haptics
      preferences[SOUND] = changed.sound
      preferences[ROUNDING] = changed.rounding.id
      preferences[WELCOME_SEEN] = changed.welcomeSeen
      preferences[ACTIVE_GROUP] = changed.activeGroupId
      preferences[ACTIVE_SESSION] = changed.activeSessionId
      preferences[DEFAULT_SET] = changed.defaultSetId
      // Two keys rather than one joined string: a table id may contain
      // anything a slug may, and a separator that can appear in a value is a
      // parser waiting to be written.
      changed.defaultTable.let { pin ->
        if (pin == null) {
          preferences.remove(TABLE_SET)
          preferences.remove(TABLE_ID)
        } else {
          preferences[TABLE_SET] = pin.setId
          preferences[TABLE_ID] = pin.tableId
        }
      }
    }
  }

  private fun settingsOf(preferences: Preferences): AppSettings =
    AppSettings(
      accentColor = AccentColor.ofId(preferences[ACCENT_COLOUR]),
      appearance = Appearance.of(preferences[APPEARANCE]),
      powerSaving = preferences[POWER_SAVING] == true,
      // Absent means on, because the default is on and a fresh install has no
      // key at all. `== true` would make every new install shake-less.
      shakeToRoll = preferences[SHAKE_TO_ROLL] ?: true,
      // Absent means on, for the same reason as the shake above: both default
      // to on, and a fresh install has no key at all.
      haptics = preferences[HAPTICS] ?: true,
      sound = preferences[SOUND] ?: true,
      rounding = Rounding.ofId(preferences[ROUNDING]),
      welcomeSeen = preferences[WELCOME_SEEN] == true,
      activeGroupId = preferences[ACTIVE_GROUP] ?: SavedRollGroup.UNFILED_ID,
      activeSessionId = preferences[ACTIVE_SESSION] ?: AppSettings.DEFAULT_SESSION_ID,
      defaultSetId = preferences[DEFAULT_SET] ?: DiceSet.BUILTIN_ID,
      // Both halves or neither: half a pin names no table.
      defaultTable =
        preferences[TABLE_SET]?.let { set ->
          preferences[TABLE_ID]?.let { table -> TablePin(setId = set, tableId = table) }
        },
    )

  companion object {
    /** The file this repository keeps, relative to the app's datastore directory. */
    const val FILE_NAME: String = "settings"

    private val ACCENT_COLOUR = stringPreferencesKey("accent_colour")
    private val APPEARANCE = stringPreferencesKey("appearance")
    private val POWER_SAVING = booleanPreferencesKey("power_saving")
    private val SHAKE_TO_ROLL = booleanPreferencesKey("shake_to_roll")
    private val HAPTICS = booleanPreferencesKey("haptics")
    private val SOUND = booleanPreferencesKey("sound")
    private val ROUNDING = stringPreferencesKey("rounding")
    private val WELCOME_SEEN = booleanPreferencesKey("welcome_seen")
    private val ACTIVE_GROUP = stringPreferencesKey("active_group")
    private val ACTIVE_SESSION = stringPreferencesKey("active_session")
    private val DEFAULT_SET = stringPreferencesKey("default_set")
    private val TABLE_SET = stringPreferencesKey("table_set")
    private val TABLE_ID = stringPreferencesKey("table_id")
  }
}
