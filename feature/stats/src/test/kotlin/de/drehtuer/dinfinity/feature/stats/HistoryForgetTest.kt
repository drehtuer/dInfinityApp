package de.drehtuer.dinfinity.feature.stats

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.data.Breakdown
import de.drehtuer.dinfinity.data.HistoryRepository
import de.drehtuer.dinfinity.data.SavedRollGroupRepository
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.SessionRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.RollHistoryRow
import de.drehtuer.dinfinity.data.db.SessionRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
 * Forgetting part of the history (`docs/statistics.md`, "Export and reset").
 *
 * Apart from `HistoryScreenTest` because it is the one thing on the screen
 * that destroys anything, and the cases worth having — what is offered, what
 * goes, what stays and what the question says — are a class's worth on their
 * own.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryForgetTest {
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
        // Queries and invalidation on the calling thread, so a `Flow` from a
        // `@Query` emits when the write happens rather than when a pool thread
        // gets to it. Without it the first wait in a class races Room's own
        // executors, which surfaces as an unrelated test failing now and then.
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()
    history = HistoryRepository(database)
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
  }

  @Test
  fun `forgetting is not offered with everything showing`() {
    // "Forget the entire history" is a bigger thing than a filter being off,
    // and offering it beside a filter would make it look the same size.
    given("2d6", 7)
    show()

    compose.onNodeWithTag(HistoryTestTags.FORGET).assertDoesNotExist()
  }

  @Test
  fun `a filter that matches nothing has nothing to forget`() {
    // The way out of an empty filter is to clear it, not to delete the
    // nothing it is showing.
    savedRoll("fireball", "Fireball")
    given("2d6", 7)
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.rollChoices.isNotEmpty() }

    presenter.filterBy(HistoryFilter.OfSavedRoll("fireball", "Fireball"))
    compose.waitUntil(PATIENCE) { presenter.state.filteredToNothing }

    compose.onNodeWithTag(HistoryTestTags.FORGET).assertDoesNotExist()
  }

  @Test
  fun `forgetting a session's rolls takes them and leaves the session`() {
    session(id = "tuesday", name = "Tuesday campaign")
    given("2d6", 7, session = "tuesday")
    given("1d20", 20, session = "default")
    val presenter = show()

    presenter.filterBy(HistoryFilter.InSession("tuesday", "Tuesday campaign"))
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 1 }
    compose.onNodeWithTag(HistoryTestTags.FORGET).performClick()
    compose.onNodeWithTag(HistoryTestTags.FORGET_YES).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.filter == HistoryFilter.Everything }
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 1 }
    assertEquals(
      "the other session's roll went too",
      "1d20",
      presenter.state.rolls
        .single()
        .formula,
    )
    assertTrue(
      "the session itself was deleted",
      presenter.state.sessionChoices.any { it.id == "tuesday" },
    )
  }

  @Test
  fun `forgetting a saved roll's throws leaves the saved roll`() {
    savedRoll("fireball", "Fireball")
    given("8d6", 28, row = { copy(savedRollId = "fireball") })
    given("2d6", 7)
    val presenter = show()

    presenter.filterBy(HistoryFilter.OfSavedRoll("fireball", "Fireball"))
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 1 }
    compose.onNodeWithTag(HistoryTestTags.FORGET).performClick()
    compose.onNodeWithTag(HistoryTestTags.FORGET_YES).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.filter == HistoryFilter.Everything }
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 1 }
    assertEquals(
      "2d6",
      presenter.state.rolls
        .single()
        .formula,
    )
    assertTrue("the saved roll itself went", presenter.state.rollChoices.any { it.id == "fireball" })
  }

  @Test
  fun `keeping them keeps them`() {
    session(id = "tuesday", name = "Tuesday campaign")
    given("2d6", 7, session = "tuesday")
    val presenter = show()

    presenter.filterBy(HistoryFilter.InSession("tuesday", "Tuesday campaign"))
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 1 }
    compose.onNodeWithTag(HistoryTestTags.FORGET).performClick()
    compose.onNodeWithTag(HistoryTestTags.FORGET_NO).performClick()

    compose.onNodeWithTag(HistoryTestTags.FORGET_DIALOG).assertDoesNotExist()
    assertEquals(1, presenter.state.rolls.size)
  }

  @Test
  fun `the confirmation says what stays as well as what goes`() {
    // The per-die statistics count these throws whether the history lists them
    // or not. A dialog that only said "this cannot be undone" would leave
    // somebody waiting for their d20's record to change.
    session(id = "tuesday", name = "Tuesday campaign")
    given("2d6", 7, session = "tuesday")
    val presenter = show()

    presenter.filterBy(HistoryFilter.InSession("tuesday", "Tuesday campaign"))
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 1 }
    compose.onNodeWithTag(HistoryTestTags.FORGET).performClick()

    compose.onNodeWithText("Tuesday campaign", substring = true).assertIsDisplayed()
    compose.onNodeWithText("what each die has done", substring = true).assertIsDisplayed()
  }

  private fun given(
    formula: String,
    total: Long,
    at: Long = next++,
    session: String = "default",
    row: RollHistoryRow.() -> RollHistoryRow = { this },
  ) {
    runBlocking {
      database.rollHistoryWriting().insert(
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

  private fun session(
    id: String,
    name: String,
  ) {
    runBlocking { database.sessions().upsert(SessionRow(id = id, name = name)) }
  }

  private fun savedRoll(
    id: String,
    name: String,
  ) {
    runBlocking {
      val repository = SavedRollRepository(database)
      // A roll needs a group to be in: the foreign key says so, and Unfiled is
      // the one every roll falls back to.
      SavedRollGroupRepository(database).ensureUnfiled("Unfiled")
      repository.save(SavedRoll(id = id, groupId = SavedRollGroup.UNFILED_ID, name = name, formula = "2d6"))
    }
  }

  private fun show(
    onExport: (ExportFile) -> Unit = {},
    limit: Int = HistoryRepository.PAGE,
  ): HistoryPresenter {
    val presenter =
      HistoryPresenter(
        history = history,
        scope = scope,
        sessions = SessionRepository(database),
        saved = SavedRollRepository(database),
        limit = limit,
      )
    compose.setContent {
      HistoryScreen(presenter = presenter, formatter = { "at $it" }, onExport = onExport)
    }
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

  private companion object {
    const val PATIENCE = 2_000L
  }
}
