package de.drehtuer.dinfinity.feature.stats

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        // Queries and invalidation on the calling thread, so a `Flow` from a
        // `@Query` emits when the write happens rather than when a pool thread
        // gets to it. Without it the first wait in a class races Room's own
        // executors, which surfaces as an unrelated test failing now and then.
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
    given(dieId = "d20", sides = 20, throws = 3, sum = 30, row = { copy(setId = "brass") })
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

  @Test
  fun `the cuts are not offered until there is more than one set to cut by`() {
    // One set is every set. A filter row that only ever says "all sets" is a
    // control that cannot do anything.
    given(dieId = "d6", sides = 6, throws = 10, sum = 35)

    show()

    compose.onNodeWithTag(StatsTestTags.ALL_SETS).assertIsNotDisplayed()
  }

  @Test
  fun `a set can be picked out of the list`() {
    given(dieId = "d6", sides = 6, throws = 10, sum = 35)
    given(dieId = "d6", sides = 6, throws = 4, sum = 14, row = { copy(setId = "brass") })
    val presenter = show()

    compose.onNodeWithTag(StatsTestTags.setOf("brass")).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.setFilter == "brass" }
    assertEquals(listOf("brass"), presenter.state.dice.map { it.setId })
  }

  @Test
  fun `going back to every set shows them all again`() {
    given(dieId = "d6", sides = 6, throws = 10, sum = 35)
    given(dieId = "d6", sides = 6, throws = 4, sum = 14, row = { copy(setId = "brass") })
    val presenter = show()
    compose.onNodeWithTag(StatsTestTags.setOf("brass")).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.setFilter == "brass" }

    compose.onNodeWithTag(StatsTestTags.ALL_SETS).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.setFilter == null }
    assertEquals(2, presenter.state.dice.size)
  }

  @Test
  fun `a filter that hides everything says so, rather than looking like an empty history`() {
    given(dieId = "d6", sides = 6, throws = 10, sum = 35)
    given(dieId = "d6", sides = 6, throws = 4, sum = 14, row = { copy(setId = "brass") })
    val presenter = show()

    presenter.filterBy("a-set-with-no-record")
    compose.waitForIdle()

    compose.onNodeWithTag(StatsTestTags.FILTERED_EMPTY).assertIsDisplayed()
    compose.onNodeWithTag(StatsTestTags.EMPTY).assertIsNotDisplayed()
  }

  @Test
  fun `rolling up counts every set's dice of a kind together`() {
    // "All my d20s". Throws and totals add up, so the mean is the mean of
    // everything thrown rather than the mean of two means.
    given(dieId = "d6", sides = 6, throws = 10, sum = 40)
    given(dieId = "d6", sides = 6, throws = 30, sum = 100, row = { copy(setId = "brass") })
    val presenter = show()

    compose.onNodeWithTag(StatsTestTags.ACROSS_SETS).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.acrossSets }
    val pooled = presenter.state.dice.single()
    assertEquals("d6", pooled.name)
    assertEquals(40L, pooled.summary.throws)
    assertEquals(140L, pooled.summary.sum)
  }

  @Test
  fun `a rolled-up row claims no streak, because two dice do not share one`() {
    // A streak is a run within one die's own sequence. Two dice's runs do not
    // join end to end.
    given(dieId = "d6", sides = 6, throws = 10, sum = 40)
    given(dieId = "d6", sides = 6, throws = 30, sum = 100, row = { copy(setId = "brass") })
    val presenter = show()

    presenter.rollUp(true)
    compose.waitForIdle()

    assertEquals(
      0,
      presenter.state.dice
        .single()
        .summary.highestStreakMax,
    )
  }

  @Test
  fun `rolling up and filtering by set put each other away`() {
    given(dieId = "d6", sides = 6, throws = 10, sum = 40)
    given(dieId = "d6", sides = 6, throws = 30, sum = 100, row = { copy(setId = "brass") })
    val presenter = show()

    presenter.filterBy("brass")
    presenter.rollUp(true)

    assertEquals(null, presenter.state.setFilter)

    presenter.filterBy("brass")
    assertEquals(false, presenter.state.acrossSets)
  }

  @Test
  fun `a rolled-up die opens a histogram of every set's faces`() {
    given(dieId = "d6", sides = 6, throws = 6, sum = 21)
    given(dieId = "d6", sides = 6, throws = 6, sum = 21, row = { copy(setId = "brass") })
    faces(setId = "builtin", dieId = "d6", sides = 6, counts = mapOf(1 to 6L))
    faces(setId = "brass", dieId = "d6", sides = 6, counts = mapOf(1 to 6L))
    val presenter = show()
    presenter.rollUp(true)
    compose.waitForIdle()

    presenter.select("", "d6")

    compose.waitUntil(PATIENCE) { presenter.state.selected != null }
    val bars = presenter.state.selected!!.bars
    assertEquals("the two sets' counts were not added", 12L, bars.single { it.value == 1 }.count)
  }

  private fun summaries(): Int = runBlocking { database.dieSummary().all().first() }.size

  @Test
  fun `the list opens most recently used first, because that is what somebody came about`() {
    given(dieId = "d6", sides = 6, throws = 100, sum = 350, row = { copy(lastRolledAtEpochMs = 1_000) })
    given(dieId = "d20", sides = 20, throws = 3, sum = 30, row = { copy(lastRolledAtEpochMs = 2_000) })
    val presenter = show()

    assertEquals(listOf("d20", "d6"), presenter.state.dice.map { it.dieId })
  }

  @Test
  fun `most thrown first puts the records worth trusting at the top`() {
    // A die thrown three times has a shape that means nothing.
    given(dieId = "d6", sides = 6, throws = 100, sum = 350, row = { copy(lastRolledAtEpochMs = 1_000) })
    given(dieId = "d20", sides = 20, throws = 3, sum = 30, row = { copy(lastRolledAtEpochMs = 2_000) })
    val presenter = show()

    compose.onNodeWithTag(StatsTestTags.orderOf(DieOrder.Throws)).performClick()

    assertEquals(listOf("d6", "d20"), presenter.state.dice.map { it.dieId })
  }

  @Test
  fun `highest average first, and a die nobody has thrown is last rather than lowest`() {
    // It has not come out low. It has not come out.
    given(dieId = "d6", sides = 6, throws = 10, sum = 20, row = { copy(lastRolledAtEpochMs = 1_000) })
    given(dieId = "d20", sides = 20, throws = 10, sum = 150, row = { copy(lastRolledAtEpochMs = 2_000) })
    given(dieId = "d4", sides = 4, throws = 0, sum = 0, row = { copy(lastRolledAtEpochMs = 3_000) })
    val presenter = show()

    compose.onNodeWithTag(StatsTestTags.orderOf(DieOrder.Average)).performClick()

    assertEquals(listOf("d20", "d6", "d4"), presenter.state.dice.map { it.dieId })
  }

  @Test
  fun `the order is not offered when there is only one die to arrange`() {
    given(dieId = "d6", sides = 6, throws = 10, sum = 35)
    show()

    compose.onNodeWithTag(StatsTestTags.orderOf(DieOrder.Throws)).assertDoesNotExist()
  }

  @Test
  fun `the line above the list says which order it is actually in`() {
    given(dieId = "d6", sides = 6, throws = 100, sum = 350, row = { copy(lastRolledAtEpochMs = 1_000) })
    given(dieId = "d20", sides = 20, throws = 3, sum = 30, row = { copy(lastRolledAtEpochMs = 2_000) })
    show()

    compose.onNodeWithText("most recently used first", substring = true).assertIsDisplayed()
    compose.onNodeWithTag(StatsTestTags.orderOf(DieOrder.Throws)).performClick()
    compose.onNodeWithText("most thrown first", substring = true).assertIsDisplayed()
  }

  @Test
  fun `re-ordering does not close the die somebody is reading`() {
    // The order is about the list behind the histogram, and closing it would
    // be the screen deciding what they meant.
    given(dieId = "d6", sides = 6, throws = 10, sum = 35, row = { copy(lastRolledAtEpochMs = 1_000) })
    given(dieId = "d20", sides = 20, throws = 10, sum = 100, row = { copy(lastRolledAtEpochMs = 2_000) })
    val presenter = show()
    presenter.select("builtin", "d6")
    compose.waitUntil(PATIENCE) { presenter.state.selected != null }

    presenter.orderBy(DieOrder.Throws)

    assertEquals(
      "d6",
      presenter.state.selected!!
        .row.dieId,
    )
  }

  /**
   * One die's record.
   *
   * The row is built here and adjusted through `row` rather than every column
   * being a parameter of its own: detekt's limit is the signal, and a helper
   * that lists a whole table has stopped helping and started restating it.
   */
  private fun given(
    dieId: String,
    sides: Int,
    throws: Long,
    sum: Long,
    row: DieSummaryRow.() -> DieSummaryRow = { this },
  ) {
    runBlocking {
      database.dieSummary().upsert(
        DieSummaryRow(setId = "builtin", dieId = dieId, sides = sides, throws = throws, sum = sum).row(),
      )
    }
  }

  private fun faces(
    setId: String = "builtin",
    dieId: String,
    sides: Int,
    counts: Map<Int, Long>,
    sessionId: String = SessionRepository.DEFAULT_ID,
  ) {
    runBlocking {
      counts.forEach { (value, count) ->
        database.dieStats().upsert(
          DieStatsRow(
            setId = setId,
            dieId = dieId,
            sessionId = sessionId,
            sides = sides,
            faceValue = value,
            count = count,
          ),
        )
      }
    }
  }

  @Test
  fun `there is nothing to export before anything has been rolled`() {
    show()

    compose.onNodeWithTag(StatsTestTags.EXPORT).assertDoesNotExist()
  }

  @Test
  fun `the flat file is one row per face of every die`() {
    given(dieId = "d6", sides = 6, throws = 60, sum = 210)
    faces(dieId = "d6", sides = 6, counts = mapOf(1 to 10L, 2 to 10L, 3 to 10L))
    val exported = mutableListOf<ExportFile>()
    show(onExport = exported::add)

    compose.onNodeWithTag(StatsTestTags.EXPORT).performClick()
    compose.onNodeWithTag(StatsTestTags.EXPORT_CSV).performClick()

    compose.waitUntil(PATIENCE) { exported.isNotEmpty() }
    val file = exported.single()
    assertEquals("text/csv", file.mediaType)
    assertEquals(
      "a header and three faces",
      4,
      file.text
        .trim()
        .lines()
        .size,
    )
  }

  @Test
  fun `the full file carries the die's summary as well as its faces`() {
    given(dieId = "d6", sides = 6, throws = 60, sum = 210)
    faces(dieId = "d6", sides = 6, counts = mapOf(1 to 10L))
    val exported = mutableListOf<ExportFile>()
    show(onExport = exported::add)

    compose.onNodeWithTag(StatsTestTags.EXPORT).performClick()
    compose.onNodeWithTag(StatsTestTags.EXPORT_JSON).performClick()

    compose.waitUntil(PATIENCE) { exported.isNotEmpty() }
    val file = exported.single()
    assertTrue("the throws are missing: ${file.text}", file.text.contains("\"throws\": 60"))
    assertTrue("the faces are missing: ${file.text}", file.text.contains("\"faces\""))
  }

  @Test
  fun `a list cut to one set exports that set and names the file after it`() {
    given(dieId = "d6", sides = 6, throws = 10, sum = 35)
    given(dieId = "d6", sides = 6, throws = 10, sum = 35, row = { copy(setId = "brass") })
    faces(setId = "builtin", dieId = "d6", sides = 6, counts = mapOf(1 to 10L))
    faces(setId = "brass", dieId = "d6", sides = 6, counts = mapOf(1 to 10L))
    val exported = mutableListOf<ExportFile>()
    val presenter = show(onExport = exported::add)

    presenter.filterBy("brass")
    compose.onNodeWithTag(StatsTestTags.EXPORT).performClick()
    compose.onNodeWithTag(StatsTestTags.EXPORT_CSV).performClick()

    compose.waitUntil(PATIENCE) { exported.isNotEmpty() }
    val file = exported.single()
    assertEquals("brass.csv", file.name)
    assertTrue("the other set reached the file: ${file.text}", !file.text.contains("builtin"))
  }

  @Test
  fun `the roll-up is a way of looking, not a thing to export`() {
    // A pooled row stands for every d6 at once and belongs to no set, which is
    // right on a screen and wrong in a file. The per-die rows are also the
    // ones a roll-up can be recomputed from; the reverse is not true.
    given(dieId = "d6", sides = 6, throws = 10, sum = 35)
    given(dieId = "d6", sides = 6, throws = 10, sum = 35, row = { copy(setId = "brass") })
    faces(setId = "builtin", dieId = "d6", sides = 6, counts = mapOf(1 to 10L))
    faces(setId = "brass", dieId = "d6", sides = 6, counts = mapOf(1 to 10L))
    val exported = mutableListOf<ExportFile>()
    val presenter = show(onExport = exported::add)

    presenter.rollUp(true)
    compose.onNodeWithTag(StatsTestTags.EXPORT).performClick()
    compose.onNodeWithTag(StatsTestTags.EXPORT_CSV).performClick()

    compose.waitUntil(PATIENCE) { exported.isNotEmpty() }
    val file = exported.single()
    assertTrue("the real sets are missing: ${file.text}", file.text.contains("builtin") && file.text.contains("brass"))
  }

  private fun show(
    onExport: (ExportFile) -> Unit = {},
    sessions: SessionRepository? = null,
  ): StatsPresenter {
    val presenter =
      StatsPresenter(
        statistics = reading,
        writer = writing,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        sessions = sessions,
      )
    compose.setContent { StatsScreen(presenter = presenter, onExport = onExport) }
    compose.waitUntil(PATIENCE) { presenter.state.loaded }
    return presenter
  }

  /** Two sessions with something thrown in each, which is when the chooser appears. */
  private fun twoSessions(): SessionRepository {
    val sessions = SessionRepository(database)
    runBlocking {
      sessions.ensureDefault("First rolls")
      database.sessions().upsert(
        de.drehtuer.dinfinity.data.db
          .SessionRow(id = "tuesday", name = "Tuesday", startedAtEpochMs = 1),
      )
    }
    return sessions
  }

  @Test
  fun `there is no session chooser until there is a second session`() {
    given(dieId = "d20", sides = 20, throws = 4, sum = 40)
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 4L))

    show(sessions = SessionRepository(database).also { runBlocking { it.ensureDefault("First rolls") } })

    compose.onNodeWithTag(StatsTestTags.ALL_SESSIONS).assertDoesNotExist()
  }

  @Test
  fun `choosing a session shows only what was thrown in it`() {
    given(dieId = "d20", sides = 20, throws = 7, sum = 140)
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 4L))
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 3L), sessionId = "tuesday")
    val presenter = show(sessions = twoSessions())

    compose.waitUntil(PATIENCE) { presenter.state.sessionsChoosable }
    compose.onNodeWithTag(StatsTestTags.sessionOf("tuesday")).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.dice.isNotEmpty() }

    assertEquals(
      "the session's own throws, not every throw of that die",
      3L,
      presenter.state.dice
        .single()
        .summary.throws,
    )
  }

  @Test
  fun `going back to all sessions puts every throw back`() {
    given(dieId = "d20", sides = 20, throws = 7, sum = 140)
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 4L))
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 3L), sessionId = "tuesday")
    val presenter = show(sessions = twoSessions())
    compose.waitUntil(PATIENCE) { presenter.state.sessionsChoosable }
    compose.onNodeWithTag(StatsTestTags.sessionOf("tuesday")).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.cutToSession }

    compose.onNodeWithTag(StatsTestTags.ALL_SESSIONS).performClick()

    compose.waitUntil(PATIENCE) { !presenter.state.cutToSession }
    assertEquals(
      7L,
      presenter.state.dice
        .single()
        .summary.throws,
    )
  }

  @Test
  fun `a session with nothing in it is a filter that found nothing, not an empty history`() {
    // The mistake this exists to make impossible: telling somebody who has
    // rolled hundreds of times that they never have, because they picked a
    // quiet campaign (`docs/statistics.md`).
    given(dieId = "d20", sides = 20, throws = 4, sum = 40)
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 4L))
    val presenter = show(sessions = twoSessions())
    compose.waitUntil(PATIENCE) { presenter.state.sessionsChoosable }

    compose.onNodeWithTag(StatsTestTags.sessionOf("tuesday")).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.cutToSession }
    assertTrue("an empty session read as an empty history", !presenter.state.empty)
    compose.onNodeWithTag(StatsTestTags.EMPTY).assertDoesNotExist()
    compose.onNodeWithTag(StatsTestTags.FILTERED_EMPTY).assertIsDisplayed()
  }

  @Test
  fun `the file is the whole record even while a session is on screen`() {
    // A session cuts what is *looked at*. The streaks a file carries are runs
    // through a die's whole sequence and a session has none of its own, so an
    // export taken here would otherwise write zeros.
    given(dieId = "d20", sides = 20, throws = 7, sum = 140)
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 4L))
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 3L), sessionId = "tuesday")
    val presenter = show(sessions = twoSessions())
    compose.waitUntil(PATIENCE) { presenter.state.sessionsChoosable }
    compose.onNodeWithTag(StatsTestTags.sessionOf("tuesday")).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.cutToSession }

    assertEquals(
      "the export would have carried one session's throws",
      7L,
      presenter.state.recorded
        .single()
        .summary.throws,
    )
  }

  @Test
  fun `all my d20s can be asked of one session`() {
    // The one combination that needs both cuts at once. The set filter and the
    // roll-up cancel each other out; a session does not cancel either.
    given(dieId = "d20", sides = 20, throws = 5, sum = 100)
    faces(setId = "builtin", dieId = "d20", sides = 20, counts = mapOf(20 to 2L))
    faces(setId = "builtin", dieId = "d20", sides = 20, counts = mapOf(20 to 3L), sessionId = "tuesday")
    val presenter = show(sessions = twoSessions())
    compose.waitUntil(PATIENCE) { presenter.state.sessionsChoosable }

    compose.onNodeWithTag(StatsTestTags.sessionOf("tuesday")).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.dice.isNotEmpty() }
    presenter.rollUp(across = true)
    presenter.select(setId = "", dieId = "d20")

    compose.waitUntil(PATIENCE) { presenter.state.selected != null }
    assertEquals(
      "the pool counted every session's throws, not this one's",
      3L,
      presenter.state.selected
        ?.bars
        ?.first { it.value == 20 }
        ?.count,
    )
  }

  @Test
  fun `tapping the session that is already chosen changes nothing`() {
    given(dieId = "d20", sides = 20, throws = 7, sum = 140)
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 4L))
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 3L), sessionId = "tuesday")
    val presenter = show(sessions = twoSessions())
    compose.waitUntil(PATIENCE) { presenter.state.sessionsChoosable }
    compose.onNodeWithTag(StatsTestTags.sessionOf("tuesday")).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.dice.isNotEmpty() }

    compose.onNodeWithTag(StatsTestTags.sessionOf("tuesday")).performClick()

    assertEquals(
      "the list was emptied and rebuilt for a filter that did not move",
      3L,
      presenter.state.dice
        .single()
        .summary.throws,
    )
  }

  @Test
  fun `a roll landing while a session is chosen does not put the other sessions back`() {
    given(dieId = "d20", sides = 20, throws = 7, sum = 140)
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 4L))
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 3L), sessionId = "tuesday")
    val presenter = show(sessions = twoSessions())
    compose.waitUntil(PATIENCE) { presenter.state.sessionsChoosable }
    compose.onNodeWithTag(StatsTestTags.sessionOf("tuesday")).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.dice.isNotEmpty() }

    // The all-time list is watched the whole time, for the export and for
    // "has anything ever been rolled". Its next answer must not become the
    // list on screen.
    given(dieId = "d6", sides = 6, throws = 1, sum = 3)

    compose.waitUntil(PATIENCE) { presenter.state.allTime.size == 2 }
    assertEquals(
      "a roll in another session walked into the list",
      listOf("d20"),
      presenter.state.dice.map(DieRow::dieId),
    )
  }

  private companion object {
    const val PATIENCE = 2_000L
  }
}
