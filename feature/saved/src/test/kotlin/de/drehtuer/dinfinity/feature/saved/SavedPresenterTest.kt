package de.drehtuer.dinfinity.feature.saved

import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.notation.DiceCatalog
import de.drehtuer.dinfinity.data.SavedRollRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    repository = SavedRollRepository(database) { 1_000L }
  }

  @After
  fun close() {
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
    runBlocking { repository.save(SavedRollGroup(id = "thorin", name = "Thorin")) }
    val presenter = presenter()
    presenter.open("thorin")

    runBlocking { repository.deleteGroup("thorin", unfiledName = "Unfiled") }
    presenter.await("the deleted group was still showing") {
      presenter.state.activeGroupId == SavedRollGroup.UNFILED_ID
    }

    assertEquals(SavedRollGroup.UNFILED_ID, presenter.state.activeGroupId)
  }

  @Test
  fun `the switcher opens and closes, and choosing a group closes it`() {
    runBlocking { repository.save(SavedRollGroup(id = "thorin", name = "Thorin")) }
    val presenter = presenter()

    presenter.showGroups(true)
    assertTrue(presenter.state.switching)

    presenter.open("thorin")
    assertFalse("the switcher stayed open over the list it had just changed", presenter.state.switching)
  }

  @Test
  fun `a roll deleted is a roll gone from the list`() {
    runBlocking {
      repository.ensureUnfiled("Unfiled")
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
      repository.ensureUnfiled("Unfiled")
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

  private fun presenter(activeGroupId: String = SavedRollGroup.UNFILED_ID) =
    SavedPresenter(
      repository = repository,
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
