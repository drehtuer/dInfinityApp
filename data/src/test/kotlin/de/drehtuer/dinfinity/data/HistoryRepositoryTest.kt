package de.drehtuer.dinfinity.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.RollHistoryRow
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
 * Past rolls, to read (`docs/statistics.md`, "History").
 *
 * The thing worth asserting here is what does *not* come out: a past roll is a
 * record, not something to re-run, and the guarantee is that the type the
 * screens see has no seed on it (`docs/architecture.md`, decision 13).
 */
@RunWith(RobolectricTestRunner::class)
class HistoryRepositoryTest {
  private lateinit var database: DInfinityDatabase
  private lateinit var history: HistoryRepository

  @Before
  fun open() {
    database =
      Room
        .inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext<Context>(),
          DInfinityDatabase::class.java,
        ).allowMainThreadQueries()
        .build()
    history = HistoryRepository(database)
  }

  @After
  fun close() {
    database.close()
  }

  @Test
  fun `the newest roll comes first`() =
    runTest {
      given(formula = "old", at = 1_000)
      given(formula = "new", at = 2_000)

      assertEquals(listOf("new", "old"), history.recent().first().map { it.formula })
    }

  @Test
  fun `the breakdown comes back with it`() =
    runTest {
      given(formula = "4d6dl1", breakdown = fourD6DropLowest())

      val entry = history.recent().first().single()
      assertEquals(
        listOf(6, 5, 4, 1),
        entry.groups
          .single()
          .dice
          .map { it.value },
      )
      assertEquals(
        listOf(6, 5, 4),
        entry.groups
          .single()
          .kept
          .map { it.value },
      )
    }

  @Test
  fun `a roll with a natural maximum in it says so, because the screen paints it`() =
    runTest {
      given(formula = "1d20", breakdown = one(value = 20, naturalMax = true))

      assertTrue(
        history
          .recent()
          .first()
          .single()
          .hasNaturalMax,
      )
    }

  @Test
  fun `a natural maximum that was dropped does not count`() =
    runTest {
      // It did not contribute to the total, so calling the roll a natural 20
      // would be the history telling a better story than the throw did.
      given(formula = "2d20kl1", breakdown = one(value = 20, naturalMax = true, dropped = true))

      assertFalse(
        history
          .recent()
          .first()
          .single()
          .hasNaturalMax,
      )
    }

  @Test
  fun `a roll with no dice has no breakdown to open`() =
    runTest {
      given(formula = "4 + 4", breakdown = RollResult(formula = "4 + 4", total = 8))

      assertFalse(
        history
          .recent()
          .first()
          .single()
          .hasBreakdown,
      )
    }

  @Test
  fun `one session's rolls can be asked for on their own`() =
    runTest {
      given(formula = "tuesday", session = "Tuesday")
      given(formula = "wednesday", session = "Wednesday")

      assertEquals(listOf("tuesday"), history.inSession("Tuesday").first().map { it.formula })
    }

  @Test
  fun `one saved roll's throws can be asked for on their own`() =
    runTest {
      given(formula = "8d6", savedRollId = "fireball")
      given(formula = "1d20")

      assertEquals(listOf("8d6"), history.forSavedRoll("fireball").first().map { it.formula })
    }

  @Test
  fun `a page is a page, not the whole table`() =
    runTest {
      repeat(5) { given(formula = "roll $it", at = it.toLong()) }

      assertEquals(2, history.recent(limit = 2).first().size)
    }

  private fun given(
    formula: String,
    at: Long = 1_000,
    session: String = "default",
    savedRollId: String? = null,
    breakdown: RollResult = one(value = 4),
  ) {
    runTest {
      database.rollHistory().insert(
        RollHistoryRow(
          timestamp = at,
          sessionId = session,
          savedRollId = savedRollId,
          formula = formula,
          total = breakdown.total,
          // Stored, and deliberately unreachable from what a screen is given.
          seed = 123_456_789,
          breakdownJson = Breakdown.of(breakdown),
        ),
      )
    }
  }

  private fun one(
    value: Int,
    naturalMax: Boolean = false,
    dropped: Boolean = false,
  ) = RollResult(
    formula = "1d20",
    total = value.toLong(),
    groups =
      listOf(
        RolledGroup(
          id = 0,
          notation = "1d20",
          setId = "builtin",
          requestedSetId = "builtin",
          subtotal = value.toLong(),
          dice =
            listOf(
              RolledDie(
                instanceIndex = 0,
                dieId = "d20",
                value = value,
                naturalMax = naturalMax,
                notes = if (dropped) setOf(DieNote.Dropped) else emptySet(),
              ),
            ),
        ),
      ),
  )

  private fun fourD6DropLowest() =
    RollResult(
      formula = "4d6dl1",
      total = 15,
      groups =
        listOf(
          RolledGroup(
            id = 0,
            notation = "4d6dl1",
            setId = "builtin",
            requestedSetId = "builtin",
            subtotal = 15,
            dice =
              listOf(
                RolledDie(instanceIndex = 0, dieId = "d6", value = 6),
                RolledDie(instanceIndex = 1, dieId = "d6", value = 5),
                RolledDie(instanceIndex = 2, dieId = "d6", value = 4),
                RolledDie(instanceIndex = 3, dieId = "d6", value = 1, notes = setOf(DieNote.Dropped)),
              ),
          ),
        ),
    )
}
