package de.drehtuer.dinfinity.feature.stats

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.HistoryRepository
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.data.db.RollHistoryRow
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A saved roll's totals against its exact distribution
 * (`design/dInfinity.dc.html`, options `8b` and `9e`).
 *
 * Against a real database, because what this screen shows is the join of two
 * tables and a distribution, and a fake of the first would assert that the
 * fake joins.
 *
 * The cases that matter are the ones where the screen could overclaim: a roll
 * nobody has thrown, a drift with four throws behind it, and a formula that
 * stopped resolving after the rolls were made.
 */
@RunWith(RobolectricTestRunner::class)
class SavedStatsScreenTest {
  @get:Rule
  val compose = createComposeRule()

  private lateinit var database: DInfinityDatabase
  private lateinit var saved: SavedRollRepository
  private lateinit var history: HistoryRepository
  private val scope = CoroutineScope(Dispatchers.Unconfined)

  /** One screen per test: `setContent` may only be called once on a rule. */
  private var shown: SavedStatsPresenter? = null

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
    saved = SavedRollRepository(database)
    history = HistoryRepository(database)
    runBlocking { saved.save(SavedRollGroup(id = GROUP, name = "Thorin")) }
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
  }

  @Test
  fun `a group with no saved rolls says so`() {
    show()

    compose.onNodeWithTag(SavedStatsTestTags.EMPTY).assertIsDisplayed()
  }

  @Test
  fun `the group's saved rolls are listed`() {
    given("fireball", "Fireball", "8d6")
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.rolls.isNotEmpty() }

    compose.onNodeWithTag(SavedStatsTestTags.rollOf("fireball")).assertTextContains("Fireball", substring = true)
    compose.onNodeWithTag(SavedStatsTestTags.rollOf("fireball")).assertTextContains("8d6", substring = true)
  }

  @Test
  fun `a roll nobody has thrown says so rather than showing an empty chart`() {
    // A mean of nothing is not a mean. A chart of nothing is not a chart.
    given("fireball", "Fireball", "2d6")
    val presenter = show()
    compose.waitUntil(PATIENCE) { presenter.state.rolls.isNotEmpty() }

    compose.onNodeWithTag(SavedStatsTestTags.rollOf("fireball")).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.selected != null }
    compose.onNodeWithTag(SavedStatsTestTags.NEVER).assertIsDisplayed()
    compose.onNodeWithTag(SavedStatsTestTags.CHART).assertIsNotDisplayed()
  }

  @Test
  fun `the throws, the mean and what was expected`() {
    given("fireball", "Fireball", "2d6")
    rolled("fireball", totals = listOf(4L, 6L, 8L))
    val presenter = opened("fireball")

    assertEquals(
      3L,
      presenter.state.selected!!
        .comparison.throws,
    )
    compose
      .onNodeWithTag(
        SavedStatsTestTags.THROWS,
        useUnmergedTree = true,
      ).assertTextContains("3 throws", substring = true)
    compose.onNodeWithTag(SavedStatsTestTags.MEAN, useUnmergedTree = true).assertTextContains("6.00", substring = true)
    compose
      .onNodeWithTag(
        SavedStatsTestTags.EXPECTED,
        useUnmergedTree = true,
      ).assertTextContains("7.00", substring = true)
  }

  @Test
  fun `the range is shown against the range the formula can reach`() {
    given("fireball", "Fireball", "2d6")
    rolled("fireball", totals = listOf(4L, 9L))
    opened("fireball")

    compose.onNodeWithTag(SavedStatsTestTags.RANGE, useUnmergedTree = true).assertTextContains("4", substring = true)
    compose.onNodeWithTag(SavedStatsTestTags.RANGE, useUnmergedTree = true).assertTextContains("12", substring = true)
  }

  @Test
  fun `a drift with too few throws behind it is not judged`() {
    // Three throws of 12 is a mean five above expectation and means nothing.
    given("fireball", "Fireball", "2d6")
    rolled("fireball", totals = List(3) { 12L })
    val presenter = opened("fireball")

    assertNull(
      presenter.state.selected!!
        .comparison.driftInErrors,
    )
    compose.onNodeWithTag(SavedStatsTestTags.VERDICT).assertTextContains("Too few", substring = true)
  }

  @Test
  fun `a drift with enough throws behind it is called worth a look, and no more`() {
    given("fireball", "Fireball", "2d6")
    rolled("fireball", totals = List(60) { 12L })
    val presenter = opened("fireball")

    assertTrue(
      presenter.state.selected!!
        .comparison.worthALook,
    )
    compose.onNodeWithTag(SavedStatsTestTags.VERDICT).assertTextContains("Worth a look", substring = true)
  }

  @Test
  fun `a roll that lands where it should is not remarked on at all`() {
    given("fireball", "Fireball", "2d6")
    rolled("fireball", totals = (2..12).flatMap { total -> List(6 - kotlin.math.abs(7 - total)) { total.toLong() } })
    opened("fireball")

    compose.onNodeWithTag(SavedStatsTestTags.VERDICT).assertIsNotDisplayed()
  }

  @Test
  fun `only this roll's own throws are counted`() {
    // The same formula typed by hand is a different question. The history
    // knows the difference because a roll started from a saved roll carries
    // its id.
    given("fireball", "Fireball", "2d6")
    rolled("fireball", totals = listOf(7L))
    rolled(savedRollId = null, totals = listOf(12L, 12L, 12L))
    val presenter = opened("fireball")

    assertEquals(
      1L,
      presenter.state.selected!!
        .comparison.throws,
    )
    assertEquals(
      7.0,
      presenter.state.selected!!
        .comparison.mean!!,
      1e-9,
    )
  }

  @Test
  fun `a formula that no longer resolves keeps its bars and loses its marks`() {
    // The set it names has been uninstalled. What the dice did is still the
    // player's record; hiding it would lose the only copy.
    given("fireball", "Fireball", "brass:d6")
    rolled("fireball", totals = listOf(3L, 5L))
    val presenter = opened("fireball")

    val stats = presenter.state.selected!!
    assertTrue("a formula that cannot resolve was graphed anyway", stats.observedOnly)
    assertEquals(2L, stats.comparison.throws)
    compose.onNodeWithTag(SavedStatsTestTags.NO_EXPECTATION).assertIsDisplayed()
    compose.onNodeWithTag(SavedStatsTestTags.CHART).assertIsDisplayed()
    compose.onNodeWithTag(SavedStatsTestTags.EXPECTED, useUnmergedTree = true).assertIsNotDisplayed()
  }

  @Test
  fun `a formula that does not parse at all keeps its bars too`() {
    // A roll saved before a grammar changed, or edited by an import. It never
    // reaches the planner, and the answer to the player is the same as for one
    // that plans and cannot resolve.
    given("broken", "Broken", "this is not a formula")
    rolled("broken", totals = listOf(4L))
    val presenter = opened("broken")

    assertTrue(presenter.state.selected!!.observedOnly)
    assertEquals(
      1L,
      presenter.state.selected!!
        .comparison.throws,
    )
  }

  @Test
  fun `each way a formula can fail to graph says its own reason`() {
    // Three different things — a formula that no longer parses, a set that is
    // not installed, and a throw the table cannot hold — and one message
    // standing for all of them would be wrong about two.
    given("broken", "Broken", "this is not a formula")
    given("missing", "Missing", "brass:d6")
    given("huge", "Huge", "${TOO_MANY}d1000")
    listOf("broken", "missing", "huge").forEach { rolled(it, totals = listOf(4L)) }

    val reasons =
      listOf("broken", "missing", "huge").map { id ->
        opened(id).state.selected!!.noExpectation
      }

    assertEquals("a reason was missing", 3, reasons.count { !it.isNullOrBlank() })
    assertEquals("two failures were given the same words: $reasons", 3, reasons.toSet().size)
  }

  @Test
  fun `going back returns to the list`() {
    given("fireball", "Fireball", "2d6")
    rolled("fireball", totals = listOf(7L))
    val presenter = opened("fireball")

    compose.onNodeWithTag(SavedStatsTestTags.BACK).performClick()

    compose.waitUntil(PATIENCE) { presenter.state.selected == null }
    compose.onNodeWithTag(SavedStatsTestTags.LIST).assertIsDisplayed()
  }

  private fun opened(rollId: String): SavedStatsPresenter {
    val presenter = shown ?: show().also { shown = it }
    compose.waitUntil(PATIENCE) { presenter.state.rolls.any { it.id == rollId } }
    presenter.select(rollId)
    // Waiting for "something is selected" is not enough once a second roll is
    // opened: the previous one is still there, and the wait returns before the
    // new one lands.
    compose.waitUntil(PATIENCE) {
      presenter.state.selected
        ?.roll
        ?.id == rollId
    }
    return presenter
  }

  private fun show(): SavedStatsPresenter {
    val presenter =
      SavedStatsPresenter(
        saved = saved,
        history = history,
        catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
        scope = scope,
        groupId = GROUP,
      )
    compose.setContent { SavedStatsScreen(presenter = presenter) }
    compose.waitUntil(PATIENCE) { presenter.state.loaded }
    return presenter
  }

  private fun given(
    id: String,
    name: String,
    formula: String,
  ) {
    runBlocking { saved.save(SavedRoll(id = id, groupId = GROUP, name = name, formula = formula)) }
  }

  private fun rolled(
    savedRollId: String?,
    totals: List<Long>,
  ) {
    runBlocking {
      totals.forEachIndexed { index, total ->
        database.rollHistory().insert(
          RollHistoryRow(
            timestamp = index.toLong(),
            sessionId = "default",
            savedRollId = savedRollId,
            formula = "2d6",
            total = total,
            seed = 0,
            breakdownJson = "[]",
          ),
        )
      }
    }
  }

  /**
   * The numbers are one block for a screen reader — `semantics(mergeDescendants)`
   * — so the tagged children only exist in the unmerged tree. Merging is right
   * for TalkBack; reading the unmerged tree is right for a test.
   */
  private companion object {
    const val GROUP = "thorin"

    /** Enough dice that the exact distribution is refused rather than computed. */
    const val TOO_MANY = 500
    const val PATIENCE = 3_000L
  }
}
