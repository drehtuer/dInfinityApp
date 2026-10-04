package de.drehtuer.dinfinity.feature.stats

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
 * The statistics screen cut to one session (`docs/statistics.md`, per session;
 * design option `6c`).
 *
 * Apart from `StatsScreenTest` because the session is a second axis across
 * everything that one tests — the list, the roll-up, the histogram and the
 * export all have to agree with it — and together they were more than one
 * class can hold and stay readable.
 */
@RunWith(RobolectricTestRunner::class)
class StatsSessionsTest {
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
  fun `a die opened inside a session shows that session's faces and no others`() {
    // The list is cut to the session, so the histogram behind a row has to be
    // too — a row saying three throws over a chart of seven would be two
    // answers to one question.
    given(dieId = "d20", sides = 20, throws = 7, sum = 140)
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 4L))
    faces(dieId = "d20", sides = 20, counts = mapOf(20 to 3L), sessionId = "tuesday")
    val presenter = show(sessions = twoSessions())
    compose.waitUntil(PATIENCE) { presenter.state.sessionsChoosable }
    compose.onNodeWithTag(StatsTestTags.sessionOf("tuesday")).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.dice.isNotEmpty() }

    open(presenter, "builtin", "d20")

    compose.waitUntil(PATIENCE) {
      presenter.state.selected
        ?.bars
        ?.isNotEmpty() == true
    }
    assertEquals(
      3L,
      presenter.state.selected
        ?.bars
        ?.single { it.value == 20 }
        ?.count,
    )
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

    compose.onNodeWithText("Only the dice thrown in this session", substring = true).assertIsDisplayed()
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

  private companion object {
    const val PATIENCE = 2_000L
  }
}
