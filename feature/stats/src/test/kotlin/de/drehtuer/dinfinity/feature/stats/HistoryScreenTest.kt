package de.drehtuer.dinfinity.feature.stats

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
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

  @Test
  fun `the chooser is not drawn until there is something to choose between`() {
    // One session and no saved rolls is every install until somebody makes a
    // second. A chooser whose only option is "everything" cannot do anything.
    given("2d6", 7)

    show()

    compose.onNodeWithTag(HistoryTestTags.ALL).assertIsNotDisplayed()
  }

  @Test
  fun `one session's rolls can be picked out`() {
    session("tuesday", "Tuesday")
    session(SessionRepository.DEFAULT_ID, "First rolls")
    given("1d20", 20, session = "tuesday")
    given("2d6", 7, session = SessionRepository.DEFAULT_ID)
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.sessionChoices.size > 1 }

    compose.onNodeWithTag(HistoryTestTags.sessionChoiceOf("tuesday")).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.filter is HistoryFilter.InSession }
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 1 }
    assertEquals(listOf("1d20"), presenter.state.rolls.map { it.formula })
  }

  @Test
  fun `one saved roll's throws can be picked out, wherever they were made`() {
    savedRoll("fireball", "Fireball")
    given("8d6", 28, session = "tuesday") { copy(savedRollId = "fireball") }
    given("8d6", 30, session = SessionRepository.DEFAULT_ID) { copy(savedRollId = "fireball") }
    given("2d6", 7)
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.rollChoices.isNotEmpty() }

    compose.onNodeWithTag(HistoryTestTags.savedRollOf("fireball")).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.filter is HistoryFilter.OfSavedRoll }
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 2 }
    assertEquals(
      listOf(28L, 30L),
      presenter.state.rolls
        .map { it.total }
        .sorted(),
    )
  }

  @Test
  fun `going back to everything shows everything`() {
    savedRoll("fireball", "Fireball")
    given("8d6", 28) { copy(savedRollId = "fireball") }
    given("2d6", 7)
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.rollChoices.isNotEmpty() }
    presenter.filterBy(HistoryFilter.OfSavedRoll("fireball", "Fireball"))
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 1 }

    compose.onNodeWithTag(HistoryTestTags.ALL).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.filter == HistoryFilter.Everything }
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 2 }
  }

  @Test
  fun `a filter that hides everything does not say you have never rolled anything`() {
    // Wrong and discouraging in front of somebody who has rolled hundreds of
    // times and picked a quiet session.
    savedRoll("fireball", "Fireball")
    given("2d6", 7)
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.rollChoices.isNotEmpty() }

    presenter.filterBy(HistoryFilter.OfSavedRoll("fireball", "Fireball"))

    compose.waitUntil(PATIENCE) { presenter.state.filteredToNothing }
    compose.onNodeWithTag(HistoryTestTags.FILTERED_EMPTY).assertIsDisplayed()
    compose.onNodeWithTag(HistoryTestTags.EMPTY).assertIsNotDisplayed()
    assertEquals(false, presenter.state.empty)
  }

  @Test
  fun `the way out of an empty filter is offered`() {
    savedRoll("fireball", "Fireball")
    given("2d6", 7)
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.rollChoices.isNotEmpty() }
    presenter.filterBy(HistoryFilter.OfSavedRoll("fireball", "Fireball"))
    compose.waitUntil(PATIENCE) { presenter.state.filteredToNothing }

    compose.onNodeWithTag(HistoryTestTags.CLEAR_FILTER).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.filter == HistoryFilter.Everything }
    compose.waitUntil(PATIENCE) { presenter.state.rolls.isNotEmpty() }
  }

  @Test
  fun `changing the filter closes the open breakdown`() {
    // A row that was open in one filter is not the row under the finger in the
    // next, and an expanded breakdown would be showing the wrong roll's dice.
    savedRoll("fireball", "Fireball")
    given("8d6", 28) { copy(savedRollId = "fireball") }
    given("2d6", 7)
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 2 }
    presenter.open(
      presenter.state.rolls
        .first()
        .id,
    )
    assertEquals(
      presenter.state.rolls
        .first()
        .id,
      presenter.state.openId,
    )

    presenter.filterBy(HistoryFilter.OfSavedRoll("fireball", "Fireball"))

    compose.waitUntil(PATIENCE) { presenter.state.openId == null }
  }

  @Test
  fun `asking for the filter that is already on changes nothing`() {
    given("2d6", 7)
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.rolls.isNotEmpty() }
    presenter.open(
      presenter.state.rolls
        .first()
        .id,
    )

    presenter.filterBy(HistoryFilter.Everything)

    assertEquals(
      "the open breakdown was closed for nothing",
      presenter.state.rolls
        .first()
        .id,
      presenter.state.openId,
    )
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

  @Test
  fun `there is nothing to export before anything has been rolled`() {
    // A button that writes an empty file is a button that lies about having
    // done something.
    show()

    compose.onNodeWithTag(HistoryTestTags.EXPORT).assertDoesNotExist()
  }

  @Test
  fun `the export button asks which shape the file should take`() {
    given(formula = "2d6", total = 7)
    show()

    compose.onNodeWithTag(HistoryTestTags.EXPORT).performClick()

    compose.onNodeWithTag(HistoryTestTags.EXPORT_DIALOG).assertIsDisplayed()
  }

  @Test
  fun `choosing the flat form hands up a file of rows`() {
    given(formula = "2d6", total = 7)
    val exported = mutableListOf<ExportFile>()
    show(onExport = exported::add)

    compose.onNodeWithTag(HistoryTestTags.EXPORT).performClick()
    compose.onNodeWithTag(HistoryTestTags.EXPORT_CSV).performClick()

    compose.waitUntil(PATIENCE) { exported.isNotEmpty() }
    val file = exported.single()
    assertTrue("not a CSV: ${file.name}", file.name.endsWith(".csv"))
    assertEquals("text/csv", file.mediaType)
    assertTrue("the roll is not in the file: ${file.text}", file.text.contains("2d6"))
  }

  @Test
  fun `choosing the full form hands up a file with the breakdown in it`() {
    given(formula = "2d6", total = 7)
    val exported = mutableListOf<ExportFile>()
    show(onExport = exported::add)

    compose.onNodeWithTag(HistoryTestTags.EXPORT).performClick()
    compose.onNodeWithTag(HistoryTestTags.EXPORT_JSON).performClick()

    compose.waitUntil(PATIENCE) { exported.isNotEmpty() }
    val file = exported.single()
    assertTrue("not JSON: ${file.name}", file.name.endsWith(".json"))
    assertTrue("the breakdown is missing: ${file.text}", file.text.contains("\"groups\""))
  }

  @Test
  fun `the file carries every roll, not the page the list is showing`() {
    // The list is capped because nobody scrolls two hundred rolls. "Export my
    // history" means the history, and a file quietly missing all but the
    // newest page is worse than no file, because nothing about it says so.
    repeat(3) { given(formula = "1d20", total = 20) }
    val exported = mutableListOf<ExportFile>()
    val presenter = show(onExport = exported::add, limit = 1)

    assertEquals("the list should be showing one roll", 1, presenter.state.rolls.size)
    compose.onNodeWithTag(HistoryTestTags.EXPORT).performClick()
    compose.onNodeWithTag(HistoryTestTags.EXPORT_CSV).performClick()

    compose.waitUntil(PATIENCE) { exported.isNotEmpty() }
    val file = exported.single()
    val rows =
      file.text
        .trim()
        .lines()
        .drop(1)
    assertEquals("the export was cut to the page on screen: ${file.text}", 3, rows.size)
  }

  @Test
  fun `a filtered list exports what it is filtered to, and says so in the name`() {
    session(id = "tuesday", name = "Tuesday campaign")
    given(formula = "2d6", total = 7, session = "tuesday")
    given(formula = "1d20", total = 20, session = "default")
    val exported = mutableListOf<ExportFile>()
    val presenter = show(onExport = exported::add)

    presenter.filterBy(HistoryFilter.InSession("tuesday", "Tuesday campaign"))
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 1 }
    compose.onNodeWithTag(HistoryTestTags.EXPORT).performClick()
    compose.onNodeWithTag(HistoryTestTags.EXPORT_CSV).performClick()

    compose.waitUntil(PATIENCE) { exported.isNotEmpty() }
    val file = exported.single()
    assertEquals("tuesday-campaign.csv", file.name)
    assertTrue("the other session's roll is in the file", !file.text.contains("1d20"))
  }

  @Test
  fun `a list filtered to one saved roll exports under that roll's name`() {
    // The other filter, and the other half of what the file is named after.
    savedRoll(id = "fireball", name = "Fireball")
    given(formula = "8d6", total = 28, row = { copy(savedRollId = "fireball") })
    val exported = mutableListOf<ExportFile>()
    val presenter = show(onExport = exported::add)

    presenter.filterBy(HistoryFilter.OfSavedRoll("fireball", "Fireball"))
    compose.waitUntil(PATIENCE) { presenter.state.rolls.size == 1 }
    compose.onNodeWithTag(HistoryTestTags.EXPORT).performClick()
    compose.onNodeWithTag(HistoryTestTags.EXPORT_JSON).performClick()

    compose.waitUntil(PATIENCE) { exported.isNotEmpty() }
    val file = exported.single()
    assertEquals("fireball.json", file.name)
    assertTrue("the roll is not in the file: ${file.text}", file.text.contains("8d6"))
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
