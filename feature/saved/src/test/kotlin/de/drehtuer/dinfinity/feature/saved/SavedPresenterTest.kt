package de.drehtuer.dinfinity.feature.saved

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.SavedRollGroupRepository
import de.drehtuer.dinfinity.data.SavedRollLibrary
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * What the saved-rolls screen is showing, without the pixels.
 *
 * The part worth testing here is what the screen cannot show: that an empty
 * list and a list that has not arrived yet are different things, and that a
 * group deleted underneath the screen does not leave it showing nothing with
 * no way back.
 */
@RunWith(RobolectricTestRunner::class)
class SavedPresenterTest {
  private lateinit var database: DInfinityDatabase
  private lateinit var repository: SavedRollRepository
  private lateinit var groupRepository: SavedRollGroupRepository
  private lateinit var library: SavedRollLibrary
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
    repository = SavedRollRepository(database) { 1_000L }
    groupRepository = SavedRollGroupRepository(database)
    library = SavedRollLibrary(repository, groupRepository)
  }

  @After
  fun close() {
    // The presenter watches the database for as long as its scope lives, so
    // the scope has to go first: a collector left running against a closed
    // connection fails the *next* test, which is a long way from here.
    scope.cancel()
    database.close()
  }

  @Test
  fun `nothing saved and nothing loaded are different states`() {
    // "Nothing saved yet" under a list that simply has not arrived is the app
    // telling a player their rolls are gone.
    val fresh = SavedState()
    assertFalse("an unloaded screen claimed to be empty", fresh.empty)

    val presenter = presenter()
    assertTrue("the screen never finished loading", presenter.state.loaded)
    assertTrue(presenter.state.empty)
  }

  @Test
  fun `Unfiled is made as soon as the screen opens`() {
    val presenter = presenter()

    assertEquals(
      SavedRollGroup.UNFILED_ID,
      presenter.state.activeGroup
        ?.group
        ?.id,
    )
  }

  @Test
  fun `a group deleted underneath the screen sends it back to Unfiled`() {
    runBlocking { groupRepository.save(SavedRollGroup(id = "thorin", name = "Thorin")) }
    val presenter = presenter()
    presenter.open("thorin")

    runBlocking { groupRepository.delete("thorin", unfiledName = "Unfiled") }
    presenter.await("the deleted group was still showing") {
      presenter.state.activeGroupId == SavedRollGroup.UNFILED_ID
    }

    assertEquals(SavedRollGroup.UNFILED_ID, presenter.state.activeGroupId)
  }

  @Test
  fun `the switcher opens and closes, and choosing a group closes it`() {
    runBlocking { groupRepository.save(SavedRollGroup(id = "thorin", name = "Thorin")) }
    val presenter = presenter()

    presenter.showGroups(true)
    assertTrue(presenter.state.switching)

    presenter.open("thorin")
    assertFalse("the switcher stayed open over the list it had just changed", presenter.state.switching)
  }

  @Test
  fun `a roll deleted is a roll gone from the list`() {
    runBlocking {
      groupRepository.ensureUnfiled("Unfiled")
      repository.save(
        SavedRoll(id = "fireball", groupId = SavedRollGroup.UNFILED_ID, name = "Fireball", formula = "8d6"),
      )
    }
    val presenter = presenter()

    presenter.delete("fireball")
    presenter.await("the deleted roll was still in the list") { presenter.state.rolls.isEmpty() }

    assertTrue(presenter.state.rolls.isEmpty())
  }

  @Test
  fun `using a roll is recorded even though the list is what shows it`() {
    runBlocking {
      groupRepository.ensureUnfiled("Unfiled")
      repository.save(
        SavedRoll(id = "fireball", groupId = SavedRollGroup.UNFILED_ID, name = "Fireball", formula = "8d6"),
      )
    }
    val presenter = presenter()

    presenter.used("fireball")
    presenter.await("the use was not counted") {
      presenter.state.rolls
        .singleOrNull()
        ?.roll
        ?.useCount == 1
    }

    assertEquals(
      1,
      presenter.state.rolls
        .single()
        .roll.useCount,
    )
  }

  @Test
  fun `a move reorders the list at once and is written down when the finger lifts`() {
    // Live under the finger, on disk on release: a drag across a screenful of
    // rolls is one transaction rather than thirty
    // (`docs/dice-notation.md`, "Saved rolls").
    given("first", "second", "third")
    val presenter = presenter()

    presenter.move("third", 0)

    assertEquals(listOf("third", "first", "second"), presenter.shown())
    assertEquals(
      "the order was written down before the drag ended",
      listOf("first", "second", "third"),
      stored(),
    )

    presenter.settle()
    presenter.await("the order was never written down") { stored() == listOf("third", "first", "second") }
  }

  @Test
  fun `a move onto the row it started on changes nothing`() {
    given("first", "second")
    val presenter = presenter()

    presenter.move("second", 1)

    assertEquals(listOf("first", "second"), presenter.shown())
  }

  @Test
  fun `a move of a roll that is not in the list is ignored`() {
    given("first")
    val presenter = presenter()

    presenter.move("gone", 0)

    assertEquals(listOf("first"), presenter.shown())
  }

  @Test
  fun `settling without a drag writes nothing`() {
    given("first", "second")
    val presenter = presenter()

    presenter.settle()

    assertEquals(listOf("first", "second"), stored())
  }

  @Test
  fun `what the database says while a drag is still in the air does not snap the row back`() {
    // A `used` count, an import, anything: an emission mid-drag must not put
    // the row back under the finger that is moving it.
    given("first", "second", "third")
    val presenter = presenter()
    presenter.move("third", 0)

    presenter.used("first")
    presenter.await("the use was never counted") {
      presenter.state.rolls.any { it.roll.id == "first" && it.roll.useCount == 1 }
    }

    assertEquals(listOf("third", "first", "second"), presenter.shown())
  }

  @Test
  fun `opening another group forgets an order that was never settled`() {
    runBlocking { groupRepository.save(SavedRollGroup(id = "thorin", name = "Thorin")) }
    given("first", "second")
    val presenter = presenter()
    presenter.move("second", 0)

    presenter.open("thorin")
    presenter.open(SavedRollGroup.UNFILED_ID)

    assertEquals(listOf("first", "second"), presenter.shown())
  }

  private fun given(vararg ids: String) {
    runBlocking {
      groupRepository.ensureUnfiled("Unfiled")
      ids.forEach { id ->
        repository.save(SavedRoll(id = id, groupId = SavedRollGroup.UNFILED_ID, name = id, formula = "1d20"))
      }
    }
  }

  private fun SavedPresenter.shown(): List<String> = state.rolls.map { it.roll.id }

  private fun stored(): List<String> =
    runBlocking { repository.inGroup(SavedRollGroup.UNFILED_ID).first() }.map(SavedRoll::id)

  private fun presenter(activeGroupId: String = SavedRollGroup.UNFILED_ID) =
    SavedPresenter(
      library = library,
      catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
      scope = scope,
      unfiledName = "Unfiled",
      activeGroupId = activeGroupId,
    ).also { presenter -> presenter.await("the screen never finished loading") { presenter.state.loaded } }

  /**
   * Waits for the database to answer.
   *
   * The presenter *watches* rather than reads, so its state arrives on a later
   * turn of whichever loop Room answered on. On a phone that is a frame; in a
   * test it is a wait, and a wait with a reason attached is worth more than a
   * sleep with a comment.
   */
  private fun SavedPresenter.await(
    why: String,
    until: () -> Boolean,
  ) {
    val deadline = System.currentTimeMillis() + PATIENCE
    while (System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      if (until()) return
      Thread.sleep(A_MOMENT)
    }
    fail(why)
  }

  private companion object {
    const val PATIENCE = 2_000L
    const val A_MOMENT = 2L
  }
}
