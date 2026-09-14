package de.drehtuer.dinfinity.feature.stats

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.DieStatisticsRepository
import de.drehtuer.dinfinity.data.StatisticsRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.DieStatsRow
import de.drehtuer.dinfinity.data.db.DieSummaryRow
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What every die has done (`design/dInfinity.dc.html`, option 1w).
 *
 * Against a real database and the real bundled set, because the two things
 * most worth being sure of are a query's order and where a die's *faces* come
 * from — and a fake would supply both.
 */
@RunWith(RobolectricTestRunner::class)
class StatsScreenTest {
  @get:Rule
  val compose = createComposeRule()

  private lateinit var database: DInfinityDatabase
  private lateinit var reading: DieStatisticsRepository
  private lateinit var writing: StatisticsRepository
  private val scope = CoroutineScope(Dispatchers.Unconfined)

  @Before
  fun open() {
    database =
      Room
        .inMemoryDatabaseBuilder(
          ApplicationProvider.getApplicationContext<Context>(),
          DInfinityDatabase::class.java,
        ).allowMainThreadQueries()
        .build()
    reading = DieStatisticsRepository(database)
    writing = StatisticsRepository(database)
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
  }

  @Test
  fun `a fresh install says nothing has been thrown`() {
    show()

    compose.onNodeWithTag(StatsTestTags.EMPTY).assertIsDisplayed()
    compose.onNodeWithTag(StatsTestTags.LIST).assertDoesNotExist()
  }

  @Test
  fun `every die that has been thrown is listed with its average`() {
    given(dieId = "d20", sides = 20, throws = 4, sum = 40)
    show()

    compose
      .onNodeWithTag(StatsTestTags.meanOf("builtin", "d20"), useUnmergedTree = true)
      .assertTextContains("10.00")
  }

  @Test
  fun `choosing a die opens its histogram`() {
    given(dieId = "d20", sides = 20, throws = 1, sum = 20)
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 1L))
    val presenter = show()

    open(presenter, "builtin", "d20")

    compose.onNodeWithTag(StatsTestTags.DETAIL).assertIsDisplayed()
    compose.onNodeWithTag(StatsTestTags.HISTOGRAM).assertIsDisplayed()
  }

  @Test
  fun `the histogram has a bar for every value, including the ones never rolled`() {
    // "This d20 has never rolled a 20" is the most interesting thing it can
    // say, and a missing bar does not say it.
    given(dieId = "d20", sides = 20, throws = 1, sum = 11)
    faces(dieId = "d20", sides = 20, counts = mapOf(11 to 1L))
    val presenter = show()

    open(presenter, "builtin", "d20")

    compose.onNodeWithTag(StatsTestTags.barOf(20), useUnmergedTree = true).assertExists()
    compose.onNodeWithTag(StatsTestTags.barOf(1), useUnmergedTree = true).assertExists()
  }

  @Test
  fun `the natural highs and lows are the numbers the screen leads with`() {
    given(dieId = "d20", sides = 20, throws = 10, sum = 100)
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 3L, 1 to 2L))
    val presenter = show()

    open(presenter, "builtin", "d20")

    compose.onNodeWithTag(StatsTestTags.HIGHS).assertTextContains("3", substring = true)
    compose.onNodeWithTag(StatsTestTags.HIGHS).assertTextContains("natural 20", substring = true)
    compose.onNodeWithTag(StatsTestTags.LOWS).assertTextContains("2", substring = true)
  }

  @Test
  fun `a die whose set is gone keeps its record, and says the fair line is a guess`() {
    // The record is the player's. Hiding it would be worse, and drawing it
    // against a line nobody can justify would be worse still.
    given(setId = "brass", dieId = "d20", sides = 20, throws = 3, sum = 30)
    faces(setId = "brass", dieId = "d20", sides = 20, counts = mapOf(11 to 3L))
    val presenter = show()

    open(presenter, "brass", "d20")

    compose.onNodeWithTag(StatsTestTags.GUESSED, useUnmergedTree = true).assertExists()
  }

  @Test
  fun `a die whose set is installed says nothing of the sort`() {
    given(dieId = "d20", sides = 20, throws = 1, sum = 11)
    faces(dieId = "d20", sides = 20, counts = mapOf(11 to 1L))
    val presenter = show()

    open(presenter, "builtin", "d20")

    compose.onNodeWithTag(StatsTestTags.GUESSED, useUnmergedTree = true).assertDoesNotExist()
  }

  @Test
  fun `going back closes the die and shows the list again`() {
    given(dieId = "d20", sides = 20, throws = 1, sum = 11)
    val presenter = show()
    open(presenter, "builtin", "d20")

    compose.onNodeWithTag(StatsTestTags.BACK).performClick()

    compose.onNodeWithTag(StatsTestTags.LIST).assertIsDisplayed()
    compose.onNodeWithTag(StatsTestTags.DETAIL).assertDoesNotExist()
  }

  @Test
  fun `forgetting a die asks first`() {
    // Forgetting a campaign's worth of natural 20s by mis-tapping is not a
    // thing that should be possible.
    given(dieId = "d20", sides = 20, throws = 4, sum = 40)
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 4L))
    val presenter = show()
    open(presenter, "builtin", "d20")

    compose.onNodeWithTag(StatsTestTags.RESET_DIE).performScrollTo().performClick()

    compose.onNodeWithTag(StatsTestTags.CONFIRM).assertIsDisplayed()
    assertEquals(1, summaries())
  }

  @Test
  fun `saying no keeps it`() {
    given(dieId = "d20", sides = 20, throws = 4, sum = 40)
    val presenter = show()
    open(presenter, "builtin", "d20")
    compose.onNodeWithTag(StatsTestTags.RESET_DIE).performScrollTo().performClick()

    compose.onNodeWithTag(StatsTestTags.CONFIRM_NO).performClick()

    compose.onNodeWithTag(StatsTestTags.CONFIRM).assertDoesNotExist()
    assertEquals(1, summaries())
  }

  @Test
  fun `saying yes forgets that die and only that die`() {
    given(dieId = "d20", sides = 20, throws = 4, sum = 40)
    given(dieId = "d6", sides = 6, throws = 9, sum = 30)
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.dice.size == 2 }
    open(presenter, "builtin", "d20")
    compose.onNodeWithTag(StatsTestTags.RESET_DIE).performScrollTo().performClick()

    compose.onNodeWithTag(StatsTestTags.CONFIRM_YES).performClick()

    compose.waitUntil(PATIENCE) { summaries() == 1 }
    assertTrue(runBlocking { database.dieSummary().find("builtin", "d6") } != null)
  }

  @Test
  fun `forgetting everything is offered from the list and asks first too`() {
    given(dieId = "d20", sides = 20, throws = 4, sum = 40)
    show()

    compose.onNodeWithTag(StatsTestTags.RESET_ALL).performScrollTo().performClick()

    compose.onNodeWithTag(StatsTestTags.CONFIRM).assertIsDisplayed()
    compose.onNodeWithTag(StatsTestTags.CONFIRM_YES).performClick()

    compose.waitUntil(PATIENCE) { summaries() == 0 }
  }

  /**
   * Opens a die and waits for its faces.
   *
   * Choosing a die starts a second flow over the face counts, so the detail
   * arrives when Room answers rather than when the tap lands. On a phone that
   * is a frame; in a test it is this.
   */
  private fun open(
    presenter: StatsPresenter,
    setId: String,
    dieId: String,
  ) {
    compose.onNodeWithTag(StatsTestTags.dieOf(setId, dieId)).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.selected != null }
  }

  private fun summaries(): Int = runBlocking { database.dieSummary().all().first() }.size

  private fun given(
    setId: String = "builtin",
    dieId: String,
    sides: Int,
    throws: Long,
    sum: Long,
  ) {
    runBlocking {
      database.dieSummary().upsert(
        DieSummaryRow(setId = setId, dieId = dieId, sides = sides, throws = throws, sum = sum),
      )
    }
  }

  private fun faces(
    setId: String = "builtin",
    dieId: String,
    sides: Int,
    counts: Map<Int, Long>,
  ) {
    runBlocking {
      counts.forEach { (value, count) ->
        database.dieStats().upsert(
          DieStatsRow(setId = setId, dieId = dieId, sides = sides, faceValue = value, count = count),
        )
      }
    }
  }

  private fun show(): StatsPresenter {
    val presenter =
      StatsPresenter(
        statistics = reading,
        writer = writing,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
      )
    compose.setContent { StatsScreen(presenter = presenter) }
    compose.waitUntil(PATIENCE) { presenter.state.loaded }
    return presenter
  }

  private companion object {
    const val PATIENCE = 2_000L
  }
}
