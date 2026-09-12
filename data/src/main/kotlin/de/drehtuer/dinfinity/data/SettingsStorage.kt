package de.drehtuer.dinfinity.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * The one settings file, created lazily on first use.
 *
 * The delegate has to be a top-level property: DataStore enforces a single
 * instance per file per process, and that is what enforces it.
 */
private val Context.settingsDataStore by preferencesDataStore(name = DataStoreSettingsRepository.FILE_NAME)

/** Builds the app's real [SettingsRepository]. Tests build their own. */
object SettingsStorage {
  fun create(context: Context): SettingsRepository =
    DataStoreSettingsRepository(context.applicationContext.settingsDataStore)
}
