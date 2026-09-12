package de.drehtuer.dinfinity.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import de.drehtuer.dinfinity.core.model.AccentColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/**
 * The documented bargain on [DataStoreSettingsRepository]: a settings file that
 * cannot be *read* falls back to the defaults, because settings are cosmetic
 * and crashing over them is worse than losing them; a *write* that fails still
 * throws, because silently not saving what someone just chose is worse than an
 * error.
 *
 * Both halves are asserted here. A real DataStore cannot be made to fail on
 * demand, so these use a stub that fails exactly as asked.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsReadFailureTest {
  private class FailingDataStore(
    private val failure: Throwable,
  ) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw failure }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw failure
  }

  private class UnreadableThenEmpty(
    private val failure: Throwable,
  ) : DataStore<Preferences> {
    override val data: Flow<Preferences> =
      flow {
        emit(preferencesOf(stringPreferencesKey("accent_colour") to AccentColor.Moss.id))
        throw failure
      }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw failure
  }

  @Test
  fun `a file that cannot be read reads as the defaults`() =
    runTest {
      val repository = DataStoreSettingsRepository(FailingDataStore(IOException("disk gone")))
      assertEquals(AccentColor.Default, repository.settings.first().accentColor)
    }

  /**
   * Only IO failures are forgiven. Anything else — a corrupt-file exception
   * from a future migration, a programming error — is a bug, and swallowing it
   * would hide it behind a silently wrong accent.
   */
  @Test
  fun `a failure that is not IO is not swallowed`() =
    runTest {
      val repository = DataStoreSettingsRepository(FailingDataStore(IllegalStateException("corrupt")))
      val thrown = runCatching { repository.settings.first() }.exceptionOrNull()
      assertTrue("expected the failure to propagate, got $thrown", thrown is IllegalStateException)
    }

  @Test
  fun `what was read before the failure still arrives`() =
    runTest {
      val repository = DataStoreSettingsRepository(UnreadableThenEmpty(IOException("truncated")))
      assertEquals(AccentColor.Moss, repository.settings.first().accentColor)
    }

  @Test
  fun `a write that fails throws rather than losing the choice silently`() =
    runTest {
      val repository = DataStoreSettingsRepository(FailingDataStore(IOException("read-only")))
      val thrown = runCatching { repository.setAccentColor(AccentColor.Sky) }.exceptionOrNull()
      assertTrue("expected the write to fail, got $thrown", thrown is IOException)
    }
}
