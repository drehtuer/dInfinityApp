package de.drehtuer.dinfinity.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The installed-set registry (`docs/dice-sets.md`, design `5a`).
 *
 * The rule these all turn on: **the disk is the list and this is the opinion**,
 * so the absence of a row means enabled. Most of what can go wrong here is a
 * row outliving the folder it was about.
 */
@RunWith(RobolectricTestRunner::class)
class InstalledSetRepositoryTest {
  private lateinit var database: DInfinityDatabase
  private lateinit var repository: InstalledSetRepository

  @Before
  fun open() {
    database =
      Room
        .inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext<Context>(),
          DInfinityDatabase::class.java,
        ).allowMainThreadQueries()
        .build()
    repository = InstalledSetRepository(database)
  }

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `a set nobody has an opinion about is on`() =
    runTest {
      // A package that installs is usable immediately. Writing a row to say so
      // would be a row that means nothing.
      assertTrue(repository.isEnabled("brass"))
      assertEquals(emptySet<String>(), repository.disabled.first())
    }

  @Test
  fun `switching a set off is remembered`() =
    runTest {
      repository.setEnabled("brass", enabled = false)

      assertFalse(repository.isEnabled("brass"))
      assertEquals(setOf("brass"), repository.disabled.first())
    }

  @Test
  fun `switching it back on is remembered too`() =
    runTest {
      repository.setEnabled("brass", enabled = false)
      repository.setEnabled("brass", enabled = true)

      assertTrue(repository.isEnabled("brass"))
      assertEquals(emptySet<String>(), repository.disabled.first())
    }

  @Test
  fun `only the sets that are off are listed`() =
    runTest {
      repository.setEnabled("brass", enabled = false)
      repository.setEnabled("copper", enabled = true)
      repository.setEnabled("amber", enabled = false)

      assertEquals(setOf("amber", "brass"), repository.disabled.first())
    }

  @Test
  fun `the bundled set cannot be switched off`() =
    runTest {
      // Every fallback resolves against it, so a player who turned it off
      // would have a d20 that no longer means anything — and no way to install
      // it back.
      repository.setEnabled(DiceSet.BUILTIN_ID, enabled = false)

      assertTrue("the bundled set was switched off", repository.isEnabled(DiceSet.BUILTIN_ID))
      assertEquals(emptySet<String>(), repository.disabled.first())
    }

  @Test
  fun `uninstalling forgets the opinion`() =
    runTest {
      repository.setEnabled("brass", enabled = false)

      repository.forget("brass")

      assertTrue("a reinstalled set came back switched off", repository.isEnabled("brass"))
    }

  @Test
  fun `rows for folders that are gone are pruned`() =
    runTest {
      // The case this exists for: a folder can vanish without the app being
      // asked. A row left behind would switch a *new* package off the moment
      // somebody installed one under the same id.
      repository.setEnabled("brass", enabled = false)
      repository.setEnabled("copper", enabled = false)

      repository.keepOnly(listOf("copper"))

      assertTrue("a set whose folder had gone kept its opinion", repository.isEnabled("brass"))
      assertFalse("pruning forgot a set that is still installed", repository.isEnabled("copper"))
    }

  @Test
  fun `pruning against nothing forgets everything`() =
    runTest {
      // Every folder gone at once is a real state — a restore that has not
      // finished — and it must not be read as "prune nothing".
      repository.setEnabled("brass", enabled = false)

      repository.keepOnly(emptyList())

      assertEquals(emptySet<String>(), repository.disabled.first())
    }

  @Test
  fun `an opinion survives being read twice`() =
    runTest {
      repository.setEnabled("brass", enabled = false)

      assertEquals(repository.disabled.first(), repository.disabled.first())
    }
}
