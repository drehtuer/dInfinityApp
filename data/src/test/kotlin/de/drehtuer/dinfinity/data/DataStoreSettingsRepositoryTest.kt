package de.drehtuer.dinfinity.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import de.drehtuer.dinfinity.core.model.AccentColor
import de.drehtuer.dinfinity.core.model.Appearance
import de.drehtuer.dinfinity.core.model.Rounding
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
   * Every key, and the exact string stored under it.
   *
   * What is on disk is what a future version has to go on understanding, so
   * this asserts the stored form rather than a round trip: renaming an enum
   * constant, or an id, would otherwise quietly reset every install.
   *
   * All the keys are written on every change — `edit` is one transaction
   * either way, and writing the whole of what was decided means a setting
   * cannot be half-applied — so all of them are asserted here.
   */
  @Test
  fun `every setting is stored under a stable key, in a stable form`() =
    runTest {
      val store = dataStore(this)
      DataStoreSettingsRepository(store).setAccentColor(AccentColor.Amber)

      val stored =
        store.data
          .first()
          .asMap()
          .mapKeys { (key, _) -> key.name }
      assertEquals(
        mapOf(
          "accent_colour" to "amber",
          "appearance" to "system",
          "power_saving" to false,
          "shake_to_roll" to true,
          "haptics" to true,
          "sound" to true,
          "rounding" to "down",
          "welcome_seen" to false,
          "active_group" to "unfiled",
          "active_session" to "default",
          "default_set" to "builtin",
        ),
        stored,
      )
    }

  @Test
  fun `the set a plain d20 comes from is read back`() =
    runTest {
      val repository = DataStoreSettingsRepository(dataStore(this))

      assertEquals("a fresh install did not start on the bundled dice", "builtin", settingsOf(repository).defaultSetId)

      repository.setDefaultSet("brass")

      assertEquals("brass", settingsOf(repository).defaultSetId)
    }

  @Test
  fun `a default set is remembered even while that set is not installed`() =
    runTest {
      // Whether it is installed is a question for the moment a formula is
      // resolved, not for the moment somebody taps a button: a set switched
      // off for an evening should still be the default when it comes back.
      val repository = DataStoreSettingsRepository(dataStore(this))

      repository.setDefaultSet("a-set-nobody-has")

      assertEquals("a-set-nobody-has", settingsOf(repository).defaultSetId)
    }

  @Test
  fun `the appearance comes back`() =
    runTest {
      val repository = DataStoreSettingsRepository(dataStore(this))

      repository.setAppearance(Appearance.Dark)

      assertEquals(Appearance.Dark, repository.settings.first().appearance)
    }

  @Test
  fun `shake is on until somebody turns it off`() =
    runTest {
      // A fresh install has no key at all, and reading an absent boolean as
      // false would ship every new install without the thing that makes this a
      // dice app.
      val repository = DataStoreSettingsRepository(dataStore(this))

      assertEquals(true, repository.settings.first().shakeToRoll)
      repository.setShakeToRoll(false)
      assertEquals(false, repository.settings.first().shakeToRoll)
    }

  @Test
  fun `haptics and sound are on until somebody turns them off`() =
    runTest {
      // Same rule as the shake above, and the same reason: a fresh install has
      // neither key, and reading an absent boolean as false would ship a silent
      // app to everybody who had never opened Settings.
      val repository = DataStoreSettingsRepository(dataStore(this))

      assertEquals(true, repository.settings.first().haptics)
      assertEquals(true, repository.settings.first().sound)

      repository.setHaptics(false)
      assertEquals(false, repository.settings.first().haptics)
      assertEquals("turning haptics off silenced the sound too", true, repository.settings.first().sound)

      repository.setSound(false)
      assertEquals(false, repository.settings.first().sound)
    }

  @Test
  fun `the default rounding comes back`() =
    runTest {
      val repository = DataStoreSettingsRepository(dataStore(this))

      repository.setRounding(Rounding.Nearest)

      assertEquals(Rounding.Nearest, repository.settings.first().rounding)
    }

  @Test
  fun `two settings changed one after the other both survive`() =
    runTest {
      // The whole reason `update` reads and writes together: a write that only
      // knew about its own field would drop the other.
      val repository = DataStoreSettingsRepository(dataStore(this))

      repository.setAppearance(Appearance.Light)
      repository.setRounding(Rounding.Up)

      val settings = repository.settings.first()
      assertEquals(Appearance.Light, settings.appearance)
      assertEquals(Rounding.Up, settings.rounding)
    }

  @Test
  fun `a stored value this version does not know falls back rather than throwing`() =
    runTest {
      // A preferences file written by a newer version, or edited by hand.
      val store = dataStore(this)
      store.edit { preferences ->
        preferences[stringPreferencesKey("appearance")] = "sepia"
        preferences[stringPreferencesKey("rounding")] = "sideways"
      }

      val settings = DataStoreSettingsRepository(store).settings.first()
      assertEquals(Appearance.System, settings.appearance)
      assertEquals(Rounding.Default, settings.rounding)
    }

  private suspend fun settingsOf(repository: DataStoreSettingsRepository) = repository.settings.first()
}
