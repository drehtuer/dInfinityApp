package de.drehtuer.dinfinity.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import de.drehtuer.dinfinity.core.model.AccentColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataStoreSettingsRepositoryTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private fun dataStore(scope: TestScope): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
      scope = TestScope(scope.testScheduler + UnconfinedTestDispatcher(scope.testScheduler)),
      produceFile = { File(temporaryFolder.root, "${DataStoreSettingsRepository.FILE_NAME}.preferences_pb") },
    )

  @Test
  fun `an empty store reads as the default accent`() =
    runTest {
      val repository = DataStoreSettingsRepository(dataStore(this))
      assertEquals(AccentColor.Default, repository.settings.first().accentColor)
    }

  @Test
  fun `a chosen accent is read back`() =
    runTest {
      val repository = DataStoreSettingsRepository(dataStore(this))
      repository.setAccentColor(AccentColor.Sky)
      assertEquals(AccentColor.Sky, repository.settings.first().accentColor)
    }

  @Test
  fun `every accent survives the round trip`() =
    runTest {
      val repository = DataStoreSettingsRepository(dataStore(this))
      AccentColor.entries.forEach { accent ->
        repository.setAccentColor(accent)
        assertEquals(accent, repository.settings.first().accentColor)
      }
    }

  @Test
  fun `power saving is off until it is turned on, and is read back`() =
    runTest {
      val repository = DataStoreSettingsRepository(dataStore(this))
      assertFalse("a new install started without drawing the dice", repository.settings.first().powerSaving)

      repository.setPowerSaving(true)
      assertTrue("power saving did not survive being written", repository.settings.first().powerSaving)

      repository.setPowerSaving(false)
      assertFalse("power saving could be turned on but not off", repository.settings.first().powerSaving)
    }

  @Test
  fun `the first launch is remembered as having happened`() =
    runTest {
      val repository = DataStoreSettingsRepository(dataStore(this))
      assertFalse("a new install had already been welcomed", repository.settings.first().welcomeSeen)

      repository.setWelcomeSeen()

      assertTrue("the welcome would have come back", repository.settings.first().welcomeSeen)
    }

  @Test
  fun `observers see a change without re-reading`() =
    runTest {
      val repository = DataStoreSettingsRepository(dataStore(this))
      repository.settings.test {
        assertEquals(AccentColor.Default, awaitItem().accentColor)
        repository.setAccentColor(AccentColor.Violet)
        assertEquals(AccentColor.Violet, awaitItem().accentColor)
        cancelAndIgnoreRemainingEvents()
      }
    }

  /**
   * The id is what is on disk, so it is what a future version has to keep
   * understanding. Asserting the stored string — not just the round trip —
   * means a rename of the enum constant cannot quietly reset every install.
   */
  @Test
  fun `the accent is stored under a stable key as its id`() =
    runTest {
      val store = dataStore(this)
      DataStoreSettingsRepository(store).setAccentColor(AccentColor.Amber)
      val stored =
        store.data
          .first()
          .asMap()
          .mapKeys { (key, _) -> key.name }
      assertEquals(mapOf("accent_colour" to "amber"), stored)
    }
}
