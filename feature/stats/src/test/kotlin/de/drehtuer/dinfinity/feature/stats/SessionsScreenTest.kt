package de.drehtuer.dinfinity.feature.stats

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.data.Breakdown
import de.drehtuer.dinfinity.data.SessionRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.RollHistoryRow
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
 * The buckets statistics are filtered by
 * (`design/dInfinity.dc.html`, option 6c).
 *
 * Against a real database, because both counts on every row are SQL's and a
 * fake would be asserting that the fake counts.
 */
@RunWith(RobolectricTestRunner::class)
class SessionsScreenTest {
  @get:Rule
  val compose = createComposeRule()

  private lateinit var database: DInfinityDatabase
  private lateinit var sessions: SessionRepository
  private val scope = CoroutineScope(Dispatchers.Unconfined)
  private var next = 0

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
    sessions = SessionRepository(database, clock = { NOW }, ids = { "made-${next++}" })
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
  }

  @Test
  fun `a fresh install already has the session its rolls belong to`() {
    // Every history row has carried a session id since version 1, so the first
    // session is not made by opening this screen — it is named by it.
    show()

    compose.onNodeWithTag(SessionsTestTags.sessionOf(SessionRepository.DEFAULT_ID)).assertIsDisplayed()
  }

  @Test
  fun `the row says how many rolls are in it and how many were natural highs`() {
    rolled(SessionRepository.DEFAULT_ID, naturalMax = true)
    rolled(SessionRepository.DEFAULT_ID, naturalMax = false)
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.sessions.any { it.rolls == 2L } }

    compose
      .onNodeWithTag(SessionsTestTags.countsOf(SessionRepository.DEFAULT_ID), useUnmergedTree = true)
      .assertTextContains("2 rolls", substring = true)
    compose
      .onNodeWithTag(SessionsTestTags.countsOf(SessionRepository.DEFAULT_ID), useUnmergedTree = true)
      .assertTextContains("1 with a natural high", substring = true)
  }

  @Test
  fun `making a session names it and starts rolling into it`() {
    // Making a session and then having to tap it is two acts where the player
    // meant one.
    val active = mutableListOf<String>()
    val presenter = show(onActive = active::add)

    compose.onNodeWithTag(SessionsTestTags.NEW).performClick()
    compose.onNodeWithTag(SessionsTestTags.NAME).performTextInput("Tuesday campaign")
    compose.onNodeWithTag(SessionsTestTags.SAVE).performClick()

    compose.waitUntil(PATIENCE) { names().contains("Tuesday campaign") }
    assertEquals(listOf("made-0"), active)
    assertEquals("made-0", presenter.state.activeId)
  }

  @Test
  fun `a session with no name cannot be saved`() {
    show()

    compose.onNodeWithTag(SessionsTestTags.NEW).performClick()

    compose.onNodeWithTag(SessionsTestTags.SAVE).assertIsNotEnabled()
  }

  @Test
  fun `Cancel makes nothing`() {
    show()

    compose.onNodeWithTag(SessionsTestTags.NEW).performClick()
    compose.onNodeWithTag(SessionsTestTags.NAME).performTextInput("Tuesday")
    compose.onNodeWithTag(SessionsTestTags.CANCEL).performClick()

    compose.onNodeWithTag(SessionsTestTags.SHEET).assertDoesNotExist()
    assertTrue(names().none { it == "Tuesday" })
  }

  @Test
  fun `renaming keeps the rolls where they are`() {
    rolled(SessionRepository.DEFAULT_ID)
    show()

    compose.onNodeWithTag(SessionsTestTags.renameOf(SessionRepository.DEFAULT_ID)).performClick()
    compose.onNodeWithTag(SessionsTestTags.NAME).performTextReplacement("Curse of Strahd")
    compose.onNodeWithTag(SessionsTestTags.SAVE).performClick()

    compose.waitUntil(PATIENCE) { names() == listOf("Curse of Strahd") }
    // The id did not move, so the roll filed under it is still filed under it.
    assertEquals(1, runBlocking { database.rollHistory().inSession(SessionRepository.DEFAULT_ID, 10).first() }.size)
  }

  @Test
  fun `choosing a session is all it takes to roll into it`() {
    // Nothing starts and nothing stops: a session left running overnight is
    // not a thing that can happen, because nothing is running.
    val active = mutableListOf<String>()
    val presenter = show(onActive = active::add)
    val made = runBlocking { sessions.create("Tuesday") }
    compose.waitUntil(PATIENCE) { presenter.state.sessions.any { it.id == made } }

    compose.onNodeWithTag(SessionsTestTags.sessionOf(made)).performClick()

    assertEquals(listOf(made), active)
  }

  @Test
  fun `the first session is offered no way to delete itself`() {
    show()

    compose.onNodeWithTag(SessionsTestTags.deleteOf(SessionRepository.DEFAULT_ID)).assertDoesNotExist()
  }

  @Test
  fun `deleting a session moves its rolls rather than deleting them`() {
    val presenter = show()
    val made = runBlocking { sessions.create("Tuesday") }
    rolled(made)
    compose.waitUntil(PATIENCE) { presenter.state.sessions.any { it.id == made } }

    compose.onNodeWithTag(SessionsTestTags.deleteOf(made)).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.sessions.none { it.id == made } }
    assertEquals(1L, runBlocking { database.rollHistory().count() })
  }

  @Test
  fun `deleting the session being rolled into moves the rolling too`() {
    // Otherwise the next throw is filed under a session that is gone.
    val presenter = show()
    val made = runBlocking { sessions.create("Tuesday") }
    compose.waitUntil(PATIENCE) { presenter.state.sessions.any { it.id == made } }
    compose.onNodeWithTag(SessionsTestTags.sessionOf(made)).performClick()

    compose.onNodeWithTag(SessionsTestTags.deleteOf(made)).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.activeId == SessionRepository.DEFAULT_ID }
  }

  private fun names(): List<String> = runBlocking { sessions.sessions.first() }.map { it.name }

  private fun rolled(
    session: String,
    naturalMax: Boolean = false,
  ) {
    runBlocking {
      database.rollHistory().insert(
        RollHistoryRow(
          timestamp = NOW,
          sessionId = session,
          formula = "1d20",
          total = 20,
          seed = 0,
          breakdownJson = Breakdown.of(result(naturalMax)),
        ),
      )
    }
  }

  private fun result(naturalMax: Boolean) =
    RollResult(
      formula = "1d20",
      total = 20,
      groups =
        listOf(
          RolledGroup(
            id = 0,
            notation = "1d20",
            setId = "builtin",
            requestedSetId = "builtin",
            subtotal = 20,
            dice = listOf(RolledDie(instanceIndex = 0, dieId = "d20", value = 20, naturalMax = naturalMax)),
          ),
        ),
    )

  private fun show(onActive: (String) -> Unit = {}): SessionsPresenter {
    val presenter =
      SessionsPresenter(
        repository = sessions,
        scope = scope,
        defaultName = "First rolls",
        onActive = onActive,
      )
    compose.setContent { SessionsScreen(presenter = presenter) }
    compose.waitUntil(PATIENCE) { presenter.state.loaded }
    return presenter
  }

  private companion object {
    const val NOW = 1_000L
    const val PATIENCE = 2_000L
  }
}
