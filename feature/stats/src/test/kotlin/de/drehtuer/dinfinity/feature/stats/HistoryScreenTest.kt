package de.drehtuer.dinfinity.feature.stats

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.data.Breakdown
import de.drehtuer.dinfinity.data.HistoryRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.RollHistoryRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every roll, with what it was made of
 * (`design/dInfinity.dc.html`, option 1x).
 *
 * Against a real database, because the order the list is in is SQL's: newest
 * first, and that is the thing a player notices most about this screen.
 *
 * The time is formatted by a function handed in, so these assert what a row
 * says rather than what timezone the machine running them is in.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryScreenTest {
  @get:Rule
  val compose = createComposeRule()

  private lateinit var database: DInfinityDatabase
  private lateinit var history: HistoryRepository
  private val scope = CoroutineScope(Dispatchers.Unconfined)
  private var next = 1L

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
    scope.cancel()
    database.close()
  }

  @Test
  fun `a history with nothing in it says so`() {
    show()

    compose.onNodeWithTag(HistoryTestTags.EMPTY).assertIsDisplayed()
    compose.onNodeWithTag(HistoryTestTags.LIST).assertDoesNotExist()
  }

  @Test
  fun `a roll is listed with its formula and its total`() {
    given(formula = "2d6", total = 9)
    val presenter = show()

    val id =
      presenter.state.rolls
        .single()
        .id
    compose.onNodeWithTag(HistoryTestTags.totalOf(id), useUnmergedTree = true).assertTextEquals("9")
    compose.onNodeWithText("2d6", substring = true).assertIsDisplayed()
  }

  @Test
  fun `the newest roll is first, which is SQL's order and not the screen's`() {
    given(formula = "old", total = 1, at = 1_000)
    given(formula = "new", total = 2, at = 2_000)
    val presenter = show()

    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 2 }
    assertOrder(presenter, listOf("new", "old"))
  }

  @Test
  fun `a tap opens the breakdown, and a second tap closes it`() {
    given(formula = "2d6", total = 9)
    val presenter = show()
    val id =
      presenter.state.rolls
        .single()
        .id

    compose.onNodeWithTag(HistoryTestTags.rollOf(id)).performClick()
    compose.onNodeWithTag(HistoryTestTags.breakdownOf(id), useUnmergedTree = true).assertIsDisplayed()

    compose.onNodeWithTag(HistoryTestTags.rollOf(id)).performClick()
    compose.onNodeWithTag(HistoryTestTags.breakdownOf(id), useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `only one breakdown is open at a time`() {
    // Fifty open breakdowns is not a list.
    given(formula = "first", total = 1)
    given(formula = "second", total = 2)
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 2 }
    val ids = presenter.state.rolls.map { it.id }

    compose.onNodeWithTag(HistoryTestTags.rollOf(ids[0])).performClick()
    compose.onNodeWithTag(HistoryTestTags.rollOf(ids[1])).performClick()

    compose.onNodeWithTag(HistoryTestTags.breakdownOf(ids[0]), useUnmergedTree = true).assertDoesNotExist()
    compose.onNodeWithTag(HistoryTestTags.breakdownOf(ids[1]), useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun `the breakdown shows every die, dropped ones included`() {
    // A player wants to see the 1 that `4d6dl1` threw away.
    given(formula = "4d6dl1", total = 15) { copy(breakdownJson = Breakdown.of(fourD6DropLowest())) }
    val presenter = show()
    val id =
      presenter.state.rolls
        .single()
        .id

    compose.onNodeWithTag(HistoryTestTags.rollOf(id)).performClick()

    compose.onNodeWithText("6 5 4 1", substring = true).assertIsDisplayed()
  }

  @Test
  fun `a throw that had corrections says so, because a number that climbs is a bug`() {
    given(formula = "2d6", total = 9) { copy(anomalies = 2) }
    val presenter = show()
    val id =
      presenter.state.rolls
        .single()
        .id

    compose.onNodeWithTag(HistoryTestTags.rollOf(id)).performClick()

    compose.onNodeWithTag(HistoryTestTags.anomaliesOf(id), useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun `a clean throw says nothing about corrections`() {
    given(formula = "2d6", total = 9)
    val presenter = show()
    val id =
      presenter.state.rolls
        .single()
        .id

    compose.onNodeWithTag(HistoryTestTags.rollOf(id)).performClick()

    compose.onNodeWithTag(HistoryTestTags.anomaliesOf(id), useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `a roll of nothing but arithmetic has nothing to open`() {
    given(formula = "4 + 4", total = 8) {
      copy(breakdownJson = Breakdown.of(RollResult(formula = "4 + 4", total = 8)))
    }
    val presenter = show()
    val id =
      presenter.state.rolls
        .single()
        .id

    compose.onNodeWithTag(HistoryTestTags.rollOf(id)).performClick()

    compose.onNodeWithTag(HistoryTestTags.breakdownOf(id), useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `with one session there are no session headings to repeat`() {
    // Which is every install until Step 4.9.
    given(formula = "2d6", total = 9)
    show()

    compose.onNodeWithTag(HistoryTestTags.sessionOf("default")).assertDoesNotExist()
  }

  @Test
  fun `with more than one session the headings say which is which`() {
    given(formula = "old", total = 1, at = 1_000, session = "Tuesday")
    given(formula = "new", total = 2, at = 2_000, session = "Wednesday")
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 2 }

    compose.onNodeWithTag(HistoryTestTags.sessionOf("Wednesday")).assertIsDisplayed()
    compose.onNodeWithTag(HistoryTestTags.sessionOf("Tuesday")).assertIsDisplayed()
  }

  @Test
  fun `no seed reaches the screen, because the type it draws from has none`() {
    // A past roll is a record, not something to re-run (decision 13). The
    // guarantee is structural: `HistoryEntry` has no seed to show.
    given(formula = "2d6", total = 9) { copy(seed = 123_456_789) }
    val presenter = show()
    val id =
      presenter.state.rolls
        .single()
        .id

    compose.onNodeWithTag(HistoryTestTags.rollOf(id)).performClick()

    compose.onNodeWithText("123456789", substring = true).assertDoesNotExist()
  }

  private fun assertOrder(
    presenter: HistoryPresenter,
    formulas: List<String>,
  ) {
    org.junit.Assert.assertEquals(formulas, presenter.state.rolls.map { it.formula })
  }

  private fun given(
    formula: String,
    total: Long,
    at: Long = next++,
    session: String = "default",
    row: RollHistoryRow.() -> RollHistoryRow = { this },
  ) {
    runBlocking {
      database.rollHistory().insert(
        RollHistoryRow(
          timestamp = at,
          sessionId = session,
          formula = formula,
          total = total,
          seed = 0,
          breakdownJson = Breakdown.of(twoD6(formula, total)),
        ).row(),
      )
    }
  }

  private fun show(): HistoryPresenter {
    val presenter = HistoryPresenter(history = history, scope = scope)
    compose.setContent { HistoryScreen(presenter = presenter, formatter = { "at $it" }) }
    compose.waitUntil(PATIENCE) { presenter.state.loaded }
    return presenter
  }

  private fun twoD6(
    formula: String,
    total: Long,
  ) = RollResult(
    formula = formula,
    total = total,
    groups =
      listOf(
        RolledGroup(
          id = 0,
          notation = formula,
          setId = "builtin",
          requestedSetId = "builtin",
          subtotal = total,
          dice =
            listOf(
              RolledDie(instanceIndex = 0, dieId = "d6", value = 6, naturalMax = true),
              RolledDie(instanceIndex = 1, dieId = "d6", value = 3),
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
                RolledDie(instanceIndex = 0, dieId = "d6", value = 6, naturalMax = true),
                RolledDie(instanceIndex = 1, dieId = "d6", value = 5),
                RolledDie(instanceIndex = 2, dieId = "d6", value = 4),
                RolledDie(instanceIndex = 3, dieId = "d6", value = 1, notes = setOf(DieNote.Dropped)),
              ),
          ),
        ),
    )

  private companion object {
    const val PATIENCE = 2_000L
  }
}
