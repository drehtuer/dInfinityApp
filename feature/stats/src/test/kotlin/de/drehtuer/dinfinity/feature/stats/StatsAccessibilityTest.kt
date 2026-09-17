package de.drehtuer.dinfinity.feature.stats

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.DieStatisticsRepository
import de.drehtuer.dinfinity.data.SessionRepository
import de.drehtuer.dinfinity.data.StatisticsRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.DieStatsRow
import de.drehtuer.dinfinity.data.db.DieSummaryRow
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.ui.common.TOUCH_TARGET
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
 * What the statistics screen says to somebody who cannot see it
 * (`docs/architecture.md`, "Accessibility").
 *
 * Three things here are drawn and not written: the fair line behind each bar
 * of the histogram, which cut of the list is on, and a back arrow that is a
 * picture of a word. The first is the whole point of the screen — "is this die
 * cursed" is answered by comparing a bar to a line, and a reader that is told
 * only the count has been told nothing.
 */
@RunWith(RobolectricTestRunner::class)
class StatsAccessibilityTest {
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
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
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
  fun `a bar says its own share and what a fair die would give`() {
    // A d2 thrown four times, three of them heads: 75 % against a fair 50 %.
    given(dieId = "d2", sides = 2, throws = 4, sum = 7)
    faces(dieId = "d2", sides = 2, counts = mapOf(1 to 1L, 2 to 3L))
    val presenter = show()

    openDie(presenter, "d2")

    compose
      .onNodeWithTag(StatsTestTags.barOf(2))
      .assertContentDescriptionEquals("Face 2 came up 3 times, 75.0 %; a fair die, 50.0 %")
  }

  @Test
  fun `a face that has never come up says so rather than saying nothing`() {
    given(dieId = "d2", sides = 2, throws = 3, sum = 6)
    faces(dieId = "d2", sides = 2, counts = mapOf(2 to 3L))
    val presenter = show()

    openDie(presenter, "d2")

    compose
      .onNodeWithTag(StatsTestTags.barOf(1))
      .assertContentDescriptionEquals("Face 1 came up 0 times, 0 %; a fair die, 50.0 %")
  }

  @Test
  fun `the way back out of a die is a word, not an arrow`() {
    given(dieId = "d2", sides = 2, throws = 2, sum = 3)
    faces(dieId = "d2", sides = 2, counts = mapOf(1 to 1L, 2 to 1L))
    val presenter = show()

    openDie(presenter, "d2")

    compose.onNodeWithTag(StatsTestTags.BACK).assertContentDescriptionEquals("Back to the list of dice")
  }

  @Test
  fun `the back arrow is big enough to press`() {
    given(dieId = "d2", sides = 2, throws = 2, sum = 3)
    faces(dieId = "d2", sides = 2, counts = mapOf(1 to 1L, 2 to 1L))
    val presenter = show()

    openDie(presenter, "d2")

    compose.onNodeWithTag(StatsTestTags.BACK).assertWidthIsAtLeast(TOUCH_TARGET)
    compose.onNodeWithTag(StatsTestTags.BACK).assertHeightIsAtLeast(TOUCH_TARGET)
  }

  @Test
  fun `which order the list is in is in the semantics, not only in the accent`() {
    given(dieId = "d6", sides = 6, throws = 4, sum = 14)
    given(dieId = "d20", sides = 20, throws = 4, sum = 40)

    show()

    compose.onNodeWithTag(StatsTestTags.orderOf(DieOrder.Recent)).assertIsSelected()
    compose.onNodeWithTag(StatsTestTags.orderOf(DieOrder.Throws)).assertIsNotSelected()

    compose.onNodeWithTag(StatsTestTags.orderOf(DieOrder.Throws)).performClick()

    compose.onNodeWithTag(StatsTestTags.orderOf(DieOrder.Throws)).assertIsSelected()
    compose.onNodeWithTag(StatsTestTags.orderOf(DieOrder.Recent)).assertIsNotSelected()
  }

  @Test
  fun `a cut of the list is big enough to press`() {
    given(dieId = "d6", sides = 6, throws = 4, sum = 14)
    given(dieId = "d20", sides = 20, throws = 4, sum = 40)

    show()

    compose.onNodeWithTag(StatsTestTags.orderOf(DieOrder.Recent)).assertHeightIsAtLeast(TOUCH_TARGET)
  }

  @Test
  fun `the histogram is on screen at all`() {
    given(dieId = "d2", sides = 2, throws = 2, sum = 3)
    faces(dieId = "d2", sides = 2, counts = mapOf(1 to 1L, 2 to 1L))
    val presenter = show()

    openDie(presenter, "d2")

    compose.onNodeWithTag(StatsTestTags.HISTOGRAM).assertIsDisplayed()
  }

  private fun openDie(
    presenter: StatsPresenter,
    dieId: String,
  ) {
    compose.onNodeWithTag(StatsTestTags.dieOf("builtin", dieId)).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.selected != null }
  }

  private fun show(): StatsPresenter {
    val presenter =
      StatsPresenter(
        statistics = reading,
        writer = writing,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        sessions = null,
      )
    compose.setContent { StatsScreen(presenter = presenter) }
    compose.waitUntil(PATIENCE) { presenter.state.loaded }
    return presenter
  }

  private fun given(
    dieId: String,
    sides: Int,
    throws: Long,
    sum: Long,
  ) {
    runBlocking {
      database.dieSummary().upsert(
        DieSummaryRow(setId = "builtin", dieId = dieId, sides = sides, throws = throws, sum = sum),
      )
    }
  }

  private fun faces(
    dieId: String,
    sides: Int,
    counts: Map<Int, Long>,
  ) {
    runBlocking {
      counts.forEach { (value, count) ->
        database.dieStats().upsert(
          DieStatsRow(
            setId = "builtin",
            dieId = dieId,
            sessionId = SessionRepository.DEFAULT_ID,
            sides = sides,
            faceValue = value,
            count = count,
          ),
        )
      }
    }
  }

  private companion object {
    const val PATIENCE = 2_000L
  }
}
