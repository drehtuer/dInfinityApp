package de.drehtuer.dinfinity.feature.stats

import android.content.Context
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.DieNote
import de.drehtuer.dinfinity.core.model.RollResult
import de.drehtuer.dinfinity.core.model.RolledDie
import de.drehtuer.dinfinity.core.model.RolledGroup
import de.drehtuer.dinfinity.data.Breakdown
import de.drehtuer.dinfinity.data.HistoryRepository
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the history says to somebody who cannot see it
 * (`docs/architecture.md`, "Accessibility").
 *
 * Two things on this screen are drawn and not written: a total with a natural
 * maximum in it prints in the accent — the one thing a player scanning their
 * history is looking for — and a dropped die is struck through. Neither mark
 * reaches a screen reader, and neither reaches somebody who cannot pick the
 * accent out of the ink.
 */
@RunWith(RobolectricTestRunner::class)
class HistoryAccessibilityTest {
  @get:Rule
  val compose = createComposeRule()

  private lateinit var database: DInfinityDatabase
  private lateinit var history: HistoryRepository
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
    history = HistoryRepository(database)
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
  }

  @Test
  fun `a natural maximum and a dropped die are said as well as drawn`() {
    given(formula = "4d6dl1", total = 15, breakdown = fourD6DropLowest())
    val presenter = show()
    val id = onlyRoll(presenter)

    compose
      .onNodeWithTag(HistoryTestTags.totalOf(id), useUnmergedTree = true)
      .assertContentDescriptionEquals("15, with a natural maximum")

    compose.onNodeWithTag(HistoryTestTags.rollOf(id)).performClick()

    compose.onNodeWithContentDescription("Dropped: 1").assertIsDisplayed()
  }

  @Test
  fun `a roll with nothing special about it is left to read as its own number`() {
    // The number is the whole of what there is to say, and a label repeating
    // it would be one more thing to listen to on every row.
    given(formula = "2d6", total = 7, breakdown = nothingSpecial())
    val presenter = show()
    val id = onlyRoll(presenter)

    compose.onNodeWithTag(HistoryTestTags.totalOf(id), useUnmergedTree = true).assertTextEquals("7")
    compose.onNodeWithContentDescription("7, with a natural maximum").assertDoesNotExist()
  }

  /**
   * Which cut of the history is on is drawn in the accent and in bold, and the
   * chip's label is its own name — so the state of the control is nowhere a
   * screen reader can find it.
   */
  @Test
  fun `which cut of the history is on is in the semantics, not only in the accent`() {
    session("tuesday", "Tuesday")
    session(SessionRepository.DEFAULT_ID, "First rolls")
    given(formula = "1d20", total = 20, breakdown = nothingSpecial(), session = "tuesday")
    given(formula = "2d6", total = 7, breakdown = nothingSpecial(), at = 2)
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.sessionChoices.size > 1 }

    compose.onNodeWithTag(HistoryTestTags.ALL).assertIsSelected()
    compose.onNodeWithTag(HistoryTestTags.sessionChoiceOf("tuesday")).assertIsNotSelected()
    compose.onNodeWithTag(HistoryTestTags.ALL).assertHeightIsAtLeast(TOUCH_TARGET)

    compose.onNodeWithTag(HistoryTestTags.sessionChoiceOf("tuesday")).performClick()
    compose.waitUntil(PATIENCE) { presenter.state.filter is HistoryFilter.InSession }

    compose.onNodeWithTag(HistoryTestTags.sessionChoiceOf("tuesday")).assertIsSelected()
    compose.onNodeWithTag(HistoryTestTags.ALL).assertIsNotSelected()
  }

  private fun session(
    id: String,
    name: String,
  ) {
    runBlocking { database.sessions().upsert(SessionRow(id = id, name = name)) }
  }

  private fun onlyRoll(presenter: HistoryPresenter): Long =
    presenter.state.rolls
      .single()
      .id

  private fun show(): HistoryPresenter {
    val presenter =
      HistoryPresenter(
        history = history,
        scope = scope,
        sessions = SessionRepository(database),
        saved = SavedRollRepository(database),
      )
    compose.setContent { HistoryScreen(presenter = presenter, formatter = { "at $it" }) }
    compose.waitUntil(PATIENCE) { presenter.state.loaded }
    return presenter
  }

  private fun given(
    formula: String,
    total: Long,
    breakdown: RollResult,
    session: String = SessionRepository.DEFAULT_ID,
    at: Long = 1,
  ) {
    runBlocking {
      database.rollHistoryWriting().insert(
        RollHistoryRow(
          timestamp = at,
          sessionId = session,
          formula = formula,
          total = total,
          seed = 0,
          breakdownJson = Breakdown.of(breakdown),
        ),
      )
    }
  }

  /** Four dice, the highest face on one of them and the lowest thrown away. */
  private fun fourD6DropLowest() =
    RollResult(
      formula = "4d6dl1",
      total = 15,
      groups =
        listOf(
          group(
            "4d6dl1",
            15,
            listOf(
              RolledDie(instanceIndex = 0, dieId = "d6", value = 6, naturalMax = true),
              RolledDie(instanceIndex = 1, dieId = "d6", value = 5),
              RolledDie(instanceIndex = 2, dieId = "d6", value = 4),
              RolledDie(instanceIndex = 3, dieId = "d6", value = 1, notes = setOf(DieNote.Dropped)),
            ),
          ),
        ),
    )

  /** Two dice, neither of them on its highest face. */
  private fun nothingSpecial() =
    RollResult(
      formula = "2d6",
      total = 7,
      groups =
        listOf(
          group(
            "2d6",
            7,
            listOf(
              RolledDie(instanceIndex = 0, dieId = "d6", value = 4),
              RolledDie(instanceIndex = 1, dieId = "d6", value = 3),
            ),
          ),
        ),
    )

  private fun group(
    notation: String,
    subtotal: Long,
    dice: List<RolledDie>,
  ) = RolledGroup(
    id = 0,
    notation = notation,
    setId = "builtin",
    requestedSetId = "builtin",
    subtotal = subtotal,
    dice = dice,
  )

  private companion object {
    const val PATIENCE = 2_000L
  }
}
