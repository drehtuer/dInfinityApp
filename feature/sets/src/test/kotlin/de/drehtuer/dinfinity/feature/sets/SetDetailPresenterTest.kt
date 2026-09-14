package de.drehtuer.dinfinity.feature.sets

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
import de.drehtuer.dinfinity.dicesets.install.PackageMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
import java.io.File
import java.nio.file.Files

/**
 * One set, in detail (`design/dInfinity.dc.html`, options `6a` and `6b`).
 *
 * The screen is opened with an id rather than a row, so the first thing worth
 * testing is that it finds the set again — and the second is what it does when
 * it cannot, which is a real state: a set removed from the list screen leaves
 * a details screen behind it on the back stack.
 */
@RunWith(RobolectricTestRunner::class)
class SetDetailPresenterTest {
  private val temporary: File = Files.createTempDirectory("dinfinity-detail").toFile()
  private val root = File(temporary, "dicesets")
  private lateinit var database: DInfinityDatabase
  private lateinit var registry: InstalledSetRepository
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
    registry = InstalledSetRepository(database)
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
    temporary.deleteRecursively()
  }

  @Test
  fun `a set is found again by the id the route carried`() {
    write("brass", toml("brass", "Brass and Bone"))

    val presenter = loaded("brass")

    assertEquals("Brass and Bone", presenter.state.row?.name)
    assertEquals("1.0.0", presenter.state.row?.version)
    assertFalse(presenter.state.missing)
  }

  @Test
  fun `an id nothing is installed under is missing, not blank`() {
    // A details screen left on the back stack after the set was removed. The
    // screen has to tell this from "still reading", and only one of them is
    // worth a message.
    val presenter = loaded("gone")

    assertNull(presenter.state.row)
    assertTrue(presenter.state.missing)
  }

  @Test
  fun `nothing is called missing before the folder has been read`() {
    val fresh = SetDetailState()

    assertFalse("missing was claimed before anything was read", fresh.missing)
  }

  @Test
  fun `the author, the licence and the description come through`() {
    write("brass", toml("brass", "Brass", extra = ABOUT))

    val set = loaded("brass").state.row?.set

    assertEquals("A. Smith", set?.author)
    assertEquals("CC-BY-4.0", set?.license)
    assertEquals("Turned brass and bone.", set?.description)
  }

  @Test
  fun `the dice the set defines come through, with their shapes`() {
    // What the grid draws. The *shape* is what a die looks like, so a d6 of
    // skulls is still a cube.
    write("brass", toml("brass", "Brass"))

    val dice =
      loaded("brass")
        .state.row
        ?.set
        ?.dice
        .orEmpty()

    assertEquals(listOf("d6"), dice.map { it.id })
    assertEquals(DieShape.Cube, dice.single().shape)
    assertEquals(6, dice.single().shape.faceCount)
  }

  @Test
  fun `a package that will not load carries its report instead of its dice`() {
    // The whole of design 6b: the report stands where the dice would.
    write("runes", "format = 1\n\n[set]\nid = \"runes\"\n")

    val row = loaded("runes").state.row

    assertTrue("a broken package was not marked broken", row?.broken == true)
    assertTrue("the report was empty", row?.report?.isNotEmpty() == true)
    assertEquals("a broken package handed out dice", 0, row?.dice)
  }

  @Test
  fun `where it came from is carried, so the source can be a link`() {
    write("brass", toml("brass", "Brass"))
    File(root, "brass/${PackageMeta.FILE_NAME}").writeText(
      PackageMeta(source = "https://example.invalid/brass.zip", commit = "deadbeef").asJson(),
    )

    val meta = loaded("brass").state.row?.meta

    assertEquals("https://example.invalid/brass.zip", meta?.source)
    assertEquals("deadbeef", meta?.commit)
  }

  @Test
  fun `the bundled set is found without a folder to read`() {
    val row = loaded(DiceSet.BUILTIN_ID).state.row

    assertTrue("the bundled set was not marked bundled", row?.bundled == true)
    assertNull("the bundled set was given a folder", row?.folder)
    assertFalse(row?.broken == true)
  }

  @Test
  fun `switching off from here is remembered`() {
    write("brass", toml("brass", "Brass"))
    val presenter = loaded("brass")

    presenter.setEnabled(false)
    await("the set was never switched off") { presenter.state.row?.enabled == false }

    assertTrue("the folder was deleted by a disable", File(root, "brass").isDirectory)
    assertFalse(runBlocking { registry.isEnabled("brass") })
  }

  @Test
  fun `removing from here takes both halves and leaves the screen`() {
    write("brass", toml("brass", "Brass"))
    var left = 0
    val presenter = loaded("brass") { left++ }

    presenter.remove()
    await("the screen was never left") { left > 0 }

    assertTrue(presenter.state.gone)
    assertFalse("the folder outlived the removal", File(root, "brass").exists())
    assertTrue("a set installed again would come back off", runBlocking { registry.isEnabled("brass") })
  }

  @Test
  fun `the bundled set cannot be removed from here either`() {
    var left = 0
    val presenter = loaded(DiceSet.BUILTIN_ID) { left++ }

    presenter.remove()

    assertEquals("the bundled set was removed", 0, left)
    assertFalse(presenter.state.gone)
    assertTrue(presenter.state.row?.bundled == true)
  }

  private fun loaded(
    id: String,
    onGone: () -> Unit = {},
  ): SetDetailPresenter =
    SetDetailPresenter(
      id = id,
      library =
        SetLibrary(
          bundled = bundledSet(),
          installed = InstalledSets(root),
          registry = registry,
          io = Dispatchers.Unconfined,
          installer = PackageInstaller(root),
          defaultSetId = { DiceSet.BUILTIN_ID },
        ),
      scope = scope,
      onGone = onGone,
      defaultSetId = { DiceSet.BUILTIN_ID },
      onDefault = {},
    ).also { presenter -> await("the folder was never read") { presenter.state.loaded } }

  private fun await(
    why: String,
    until: () -> Boolean,
  ) {
    val deadline = System.currentTimeMillis() + PATIENCE_MS
    while (!until()) {
      if (System.currentTimeMillis() > deadline) fail(why)
      Thread.sleep(POLL_MS)
    }
  }

  private fun write(
    id: String,
    toml: String,
  ) {
    val folder = File(root, id).apply { mkdirs() }
    File(folder, DiceSetValidator.DICE_SET_FILE).writeText(toml)
  }

  private fun toml(
    id: String,
    name: String,
    extra: String = "",
  ) = """
    format = 1

    [set]
    id = "$id"
    name = "$name"
    version = "1.0.0"
    $extra

    [[die]]
    id = "d6"
    shape = "cube"
    faces = [1, 2, 3, 4, 5, 6]
    """.trimIndent()

  private fun bundledSet() =
    DiceSet(
      id = DiceSet.BUILTIN_ID,
      name = "Standard",
      version = "1.0.0",
      dice = listOf(Die(id = "d6", shape = DieShape.Cube, faces = (1..6).map { Face(it - 1, it, it.toString()) })),
    )

  private companion object {
    /** The three `[set]` keys the details screen prints (`docs/dice-sets.md`). */
    val ABOUT =
      """
      author = "A. Smith"
      license = "CC-BY-4.0"
      description = "Turned brass and bone."
      """.trimIndent()

    const val PATIENCE_MS = 5_000L
    const val POLL_MS = 5L
  }
}
