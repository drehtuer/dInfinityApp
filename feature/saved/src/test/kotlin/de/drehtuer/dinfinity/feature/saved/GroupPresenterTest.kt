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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Making a group, renaming one, and taking one away
 * (`docs/dice-notation.md`, "Saved rolls").
 *
 * Against a real database, because every rule here is a fact about the group
 * list and a fake repository would only be asserting that the fake agrees with
 * the test.
 */
@RunWith(RobolectricTestRunner::class)
class GroupPresenterTest {
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
    repository = SavedRollRepository(database)
    groupRepository = SavedRollGroupRepository(database)
    library = SavedRollLibrary(repository, groupRepository)
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
  }

  @Test
  fun `nothing is open until a group is asked for`() {
    assertNull(presenter().draft)
  }

  @Test
  fun `a new group starts blank, unsavable and undeletable`() {
    val presenter = presenter()

    presenter.create()

    val draft = presenter.draft!!
    assertEquals("", draft.name)
    assertTrue(draft.fresh)
    // Nothing to save yet, and nothing to delete: a group that has never
    // existed cannot be taken away.
    assertFalse(draft.savable)
    assertFalse(draft.deletable)
  }

  @Test
  fun `a name makes it savable and writes a group that was not there`() {
    val presenter = presenter()
    presenter.create()

    presenter.name("Curse of Strahd")
    assertTrue(presenter.draft!!.savable)
    presenter.save()

    await("the group was never written") { names() == listOf("Curse of Strahd") }
    // Saving closes the sheet; there is nothing left to write.
    assertNull(presenter.draft)
  }

  @Test
  fun `the name is trimmed before it is written`() {
    val presenter = presenter()
    presenter.create()

    presenter.name("  Thorin  ")
    presenter.save()

    await("the group was never written") { names() == listOf("Thorin") }
  }

  @Test
  fun `a name only spaces long is not a name`() {
    val presenter = presenter()
    presenter.create()

    presenter.name("   ")

    assertFalse(presenter.draft!!.savable)
  }

  @Test
  fun `a name another group already has is refused, and the clash is named`() {
    // The rule an import enforces holds here too: a name that is taken is
    // taken, wherever the group came from (`docs/dice-notation.md`).
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    val presenter = presenter()
    presenter.create()

    presenter.name("D&D")

    assertEquals("D&D", presenter.draft!!.clash)
    assertFalse(presenter.draft!!.savable)
  }

  @Test
  fun `the clash ignores case, because two groups a capital apart are one group to a player`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    val presenter = presenter()
    presenter.create()

    presenter.name("d&d")

    assertEquals("D&D", presenter.draft!!.clash)
  }

  @Test
  fun `a group does not clash with itself when it is being renamed`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    val presenter = presenter()

    presenter.edit("dnd")

    assertNull(presenter.draft!!.clash)
    assertTrue(presenter.draft!!.savable)
  }

  @Test
  fun `renaming keeps the id, so the rolls in it stay in it`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    runBlocking { repository.save(SavedRoll(id = "axe", groupId = "dnd", name = "Axe", formula = "1d12")) }
    val presenter = presenter()

    presenter.edit("dnd")
    presenter.name("Curse of Strahd")
    presenter.save()

    await("the group was never renamed") { names() == listOf("Curse of Strahd") }
    assertEquals("dnd", runBlocking { repository.byId("axe") }?.groupId)
  }

  @Test
  fun `a save does nothing while the draft is not savable`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    val presenter = presenter()
    presenter.create()
    presenter.name("D&D")

    presenter.save()

    // One group, not two, and the sheet is still open for the name to be fixed.
    assertEquals(listOf("D&D"), names())
    assertEquals("D&D", presenter.draft?.clash)
  }

  @Test
  fun `only top-level groups are offered to put one inside, and never itself`() {
    given(
      SavedRollGroup(id = "dnd", name = "D&D"),
      SavedRollGroup(id = "thorin", name = "Thorin", parentId = "dnd"),
      SavedRollGroup(id = "pf", name = "Pathfinder"),
    )
    val presenter = presenter()

    presenter.edit("pf")

    assertEquals(listOf("dnd"), presenter.draft!!.parents.map { it.id })
  }

  @Test
  fun `a group with groups inside it cannot be put inside another`() {
    // The other half of one level. The repository refuses it too, because an
    // import writes without ever passing through this sheet.
    given(
      SavedRollGroup(id = "dnd", name = "D&D"),
      SavedRollGroup(id = "thorin", name = "Thorin", parentId = "dnd"),
      SavedRollGroup(id = "pf", name = "Pathfinder"),
    )
    val presenter = presenter()

    presenter.edit("dnd")

    assertFalse(presenter.draft!!.nestable)
  }

  @Test
  fun `a group put inside another is written with its parent`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    val presenter = presenter()
    presenter.create()
    presenter.name("Thorin")

    presenter.choose { copy(parentId = "dnd") }
    presenter.save()

    await("the group was never written") { group("Thorin") != null }
    assertEquals("dnd", group("Thorin")?.parentId)
  }

  @Test
  fun `a subgroup can be asked for directly from the group it belongs in`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    val presenter = presenter()

    presenter.create(inside = "dnd")

    assertEquals("dnd", presenter.draft!!.parentId)
  }

  @Test
  fun `a mark is chosen, and tapping the chosen one takes it off`() {
    val presenter = presenter()
    presenter.create()

    presenter.choose { copy(icon = "🐉") }
    assertEquals("🐉", presenter.draft!!.icon)
    presenter.choose { copy(icon = "") }
    assertEquals("", presenter.draft!!.icon)
  }

  @Test
  fun `deleting a group moves its rolls to Unfiled rather than deleting them`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    runBlocking { repository.save(SavedRoll(id = "axe", groupId = "dnd", name = "Axe", formula = "1d12")) }
    val presenter = presenter()

    presenter.edit("dnd")
    presenter.delete()

    await("the group was never deleted") { !names().contains("D&D") }
    assertEquals(SavedRollGroup.UNFILED_ID, runBlocking { repository.byId("axe") }?.groupId)
  }

  @Test
  fun `the sheet says how many rolls a deletion would move`() {
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    runBlocking {
      repository.save(SavedRoll(id = "axe", groupId = "dnd", name = "Axe", formula = "1d12"))
      repository.save(SavedRoll(id = "bow", groupId = "dnd", name = "Bow", formula = "1d8"))
    }
    val presenter = presenter()

    presenter.edit("dnd")

    assertEquals(2, presenter.draft!!.rolls)
  }

  @Test
  fun `Unfiled cannot be deleted, because it is where rolls go`() {
    runBlocking { groupRepository.ensureUnfiled("Unfiled") }
    val presenter = presenter()

    presenter.edit(SavedRollGroup.UNFILED_ID)

    assertFalse(presenter.draft!!.deletable)
  }

  @Test
  fun `a delete does nothing when the draft is not deletable`() {
    runBlocking { groupRepository.ensureUnfiled("Unfiled") }
    val presenter = presenter()
    presenter.edit(SavedRollGroup.UNFILED_ID)

    presenter.delete()

    assertEquals(listOf("Unfiled"), names())
  }

  @Test
  fun `editing a group nothing answers to opens nothing`() {
    val presenter = presenter()

    presenter.edit("never-existed")

    assertNull(presenter.draft)
  }

  @Test
  fun `dismissing throws the draft away`() {
    val presenter = presenter()
    presenter.create()
    presenter.name("Thorin")

    presenter.dismiss()

    assertNull(presenter.draft)
    assertEquals(emptyList<String>(), names())
  }

  @Test
  fun `a group deleted under an open sheet closes it`() {
    // Another screen, or an import, can take the group away while the sheet is
    // open. Going on editing a row that is gone would write it back.
    given(SavedRollGroup(id = "dnd", name = "D&D"))
    val presenter = presenter()
    presenter.edit("dnd")

    runBlocking { groupRepository.delete("dnd", "Unfiled") }

    await("the sheet stayed open on a group that is gone") { presenter.draft == null }
  }

  @Test
  fun `the id of a saved group is handed on, so whoever asked can use it`() {
    val presenter = presenter()
    val made = mutableListOf<String>()
    presenter.create()
    presenter.name("Thorin")

    presenter.save(onSaved = made::add)

    await("nothing was handed back") { made.isNotEmpty() }
    assertEquals(listOf(group("Thorin")?.id), made)
  }

  private fun presenter() =
    GroupPresenter(
      library = library,
      catalog = DiceCatalog.of(listOf(BuiltinDiceSet.set)),
      scope = scope,
      unfiledName = "Unfiled",
      ids = { "made-up" },
    ).also { await("the group list never arrived") { it.loaded } }

  /**
   * Waits for the database to answer.
   *
   * The presenter *watches* rather than reads, so the group list arrives on a
   * later turn of whichever loop Room answered on. On a phone that is a frame;
   * in a test it is a wait, and a wait with a reason attached is worth more
   * than a sleep with a comment.
   */
  private fun await(
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

  private fun given(vararg groups: SavedRollGroup) {
    runBlocking { groups.forEach { groupRepository.save(it) } }
  }

  private fun names(): List<String> = runBlocking { groupRepository.all.first() }.map { it.name }

  private fun group(name: String): SavedRollGroup? =
    runBlocking { groupRepository.all.first() }.firstOrNull { it.name == name }

  private companion object {
    const val PATIENCE = 2_000L
    const val A_MOMENT = 2L
  }
}
