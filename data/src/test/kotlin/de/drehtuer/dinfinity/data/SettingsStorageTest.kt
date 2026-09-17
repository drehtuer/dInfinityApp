package de.drehtuer.dinfinity.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.AccentColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [SettingsStorage] is the only place the app's real settings file is named, so
 * it is worth proving that what it builds actually works — the unit tests above
 * it all supply their own DataStore and would not notice if this wiring broke.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsStorageTest {
  private val context: Context get() = ApplicationProvider.getApplicationContext()

  @Test
  fun `the repository it builds round-trips an accent`() =
    runTest {
      val repository = SettingsStorage.create(context)
      repository.setAccentColor(AccentColor.Cobalt)
      assertEquals(AccentColor.Cobalt, repository.settings.first().accentColor)
    }

  /**
   * DataStore allows one instance per file per process and throws if a second
   * is created. Asking twice has to be safe, because every caller goes through
   * here.
   */
  @Test
  fun `asking twice does not open the file twice`() =
    runTest {
      SettingsStorage.create(context)
      val second = SettingsStorage.create(context.applicationContext)
      assertEquals(AccentColor.Default, second.settings.first().accentColor)
    }
}
