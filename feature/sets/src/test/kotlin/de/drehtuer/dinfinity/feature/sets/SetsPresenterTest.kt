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
 * The dice-set list (`design/dInfinity.dc.html`, option `5a`).
 *
 * Two sources meet here — what is on disk, and what the player has switched
 * off — and most of what these say is about the join: that the bundled set is
 * a row without a folder, that a package which stopped validating is still
 * listed rather than dropped, and that removing one takes both halves.
 *
 * Against a real database and a real folder. What the registry does is SQL and
 * what the scanner does is validation over files, and a fake of either would
 * only assert that the fake works.
 */
@RunWith(RobolectricTestRunner::class)
class SetsPresenterTest {
  private val temporary: File = Files.createTempDirectory("dinfinity-sets").toFile()
  private val root = File(temporary, "dicesets")
  private lateinit var database: DInfinityDatabase
  private lateinit var registry: InstalledSetRepository

  /**
   * Unconfined, and waited for by polling.
   *
   * Room answers a suspend query on its own executor, so a test scheduler never
   * sees that work finish and `advanceUntilIdle` returns before anything has
   * happened at all. The other screens that read a real database are driven the
   * same way, for the same reason.
   */
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
    registry = InstalledSetRepository(database)
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
    temporary.deleteRecursively()
  }

  @Test
  fun `the bundled set is always there, even with nothing installed`() {
    val presenter = loaded()

    val rows = presenter.state.sets
    assertEquals(listOf("builtin"), rows.map { it.id })
    assertTrue("the bundled set was not marked as bundled", rows.single().bundled)
    assertNull("the bundled set was given a folder", rows.single().folder)
    assertTrue("nothing installed did not read as empty", presenter.state.empty)
  }

  @Test
  fun `an installed set is listed after the bundled one`() {
    write("brass", toml("brass", "Brass and Bone"))

    val presenter = loaded()

    assertEquals(listOf("builtin", "brass"), presenter.state.sets.map { it.id })
    assertEquals("Brass and Bone", presenter.state.sets[1].name)
    assertFalse("an installed set read as empty", presenter.state.empty)
  }

  @Test
  fun `the installed sets are ordered by name, not by folder`() {
    // The name is what the row shows, so sorting by anything else reads as an
    // unsorted list.
    write("zinc", toml("zinc", "Amber"))
    write("amber", toml("amber", "Zinc"))

    val presenter = loaded()

    assertEquals(listOf("builtin", "zinc", "amber"), presenter.state.sets.map { it.id })
  }

  @Test
  fun `a set that no longer validates is listed as broken, not dropped`() {
    // The point of listing it at all: the player can see what is wrong and
    // update it. Dropping it would leave a folder they can see in a file
    // manager and not in the app.
    write("runes", BROKEN_TOML)

    val row = loaded().state.sets.single { it.id == "runes" }

    assertTrue("a broken package was not marked broken", row.broken)
    assertFalse("a broken package was called usable", row.usable)
    assertTrue("nothing was reported wrong with it", row.problems > 0)
    assertEquals("a broken package claimed to define dice", 0, row.dice)
  }

  @Test
  fun `a row carries the set, the report and where it came from`() {
    // The details screen reads all three off the row rather than re-reading
    // the disk, so a row that dropped any of them would cost a validation pass
    // per tap (`6a`, `6b`).
    write("brass", toml("brass", "Brass"))
    write("runes", BROKEN_TOML)

    val presenter = loaded()

    val good = presenter.state.sets.single { it.id == "brass" }
    assertEquals("Brass", good.set?.name)
    assertEquals("the report of a set with nothing wrong was not empty", emptyList<Any>(), good.report)
    assertEquals("1.0.0", good.version)

    val bad = presenter.state.sets.single { it.id == "runes" }
    assertNull("a broken package handed out a set", bad.set)
    assertTrue("a broken package handed out no report", bad.report.isNotEmpty())

    // Nothing installed it, so there is no note beside it to read.
    assertNull(good.meta.source)
  }

  @Test
  fun `what a row says is decided by what the player can do about it`() {
    // The order is the rule: a package that will not load says so before
    // anything else, because nothing else about it matters until that is
    // fixed — including that it has been switched off.
    val broken = row(broken = true, enabled = false)
    assertEquals(SetStatus.Broken, broken.status)

    assertEquals(SetStatus.Off, row(enabled = false).status)
    assertEquals(SetStatus.Bundled, row(bundled = true).status)
    assertEquals(SetStatus.Ready, row().status)
  }

  @Test
  fun `switching a set off keeps it in the list and marks it unusable`() {
    write("brass", toml("brass", "Brass"))
    val presenter = loaded()

    presenter.setEnabled(presenter.state.sets.single { it.id == "brass" }, enabled = false)
    await("the set was never switched off") {
      !presenter.state.sets
        .single { it.id == "brass" }
        .enabled
    }

    val after = presenter.state.sets.single { it.id == "brass" }
    assertFalse(after.usable)
    assertFalse("a set switched off was called broken", after.broken)
    assertTrue("the folder was deleted by a disable", File(root, "brass").isDirectory)
  }

  @Test
  fun `switched off and will not load are different states`() {
    // The remedies are opposite — one is a tap, the other is an update — so a
    // screen that could not tell them apart would send the player to the wrong
    // one.
    write("brass", toml("brass", "Brass"))
    write("runes", BROKEN_TOML)
    val presenter = loaded()

    presenter.setEnabled(presenter.state.sets.single { it.id == "brass" }, enabled = false)
    await("the set was never switched off") {
      !presenter.state.sets
        .single { it.id == "brass" }
        .enabled
    }

    val off = presenter.state.sets.single { it.id == "brass" }
    val broken = presenter.state.sets.single { it.id == "runes" }
    assertTrue("switched off was confused with broken", !off.enabled && !off.broken)
    assertTrue("broken was confused with switched off", broken.enabled && broken.broken)
  }

  @Test
  fun `removing a set takes the folder and the opinion`() {
    write("brass", toml("brass", "Brass"))
    val presenter = loaded()
    presenter.setEnabled(presenter.state.sets.single { it.id == "brass" }, enabled = false)
    await("the set was never switched off") {
      !presenter.state.sets
        .single { it.id == "brass" }
        .enabled
    }

    presenter.remove(presenter.state.sets.single { it.id == "brass" })
    await("the set was never removed") { presenter.state.sets.none { it.id == "brass" } }

    assertEquals(listOf("builtin"), presenter.state.sets.map { it.id })
    assertFalse("the folder outlived the removal", File(root, "brass").exists())
    assertTrue(
      "a set installed again under the same id would come back switched off",
      runBlocking { registry.isEnabled("brass") },
    )
  }

  @Test
  fun `the bundled set cannot be acted on at all`() {
    val presenter = loaded()
    val bundled = presenter.state.sets.single()

    presenter.act(bundled)
    assertNull("the sheet opened on the set that has nothing to offer", presenter.state.acting)

    presenter.setEnabled(bundled, enabled = false)
    presenter.remove(bundled)

    assertTrue(
      "the bundled set was switched off",
      presenter.state.sets
        .single()
        .enabled,
    )
    assertTrue(
      "the bundled set was removed",
      presenter.state.sets
        .single()
        .bundled,
    )
  }

  @Test
  fun `a long press opens the sheet and cancelling closes it`() {
    write("brass", toml("brass", "Brass"))
    val presenter = loaded()

    presenter.act(presenter.state.sets.single { it.id == "brass" })
    assertEquals("brass", presenter.state.acting?.id)

    presenter.act(null)
    assertNull(presenter.state.acting)
  }

  @Test
  fun `an opinion about a folder that has gone is forgotten on the next reading`() {
    // A file manager, a restore, an update that failed halfway. A row left
    // behind would switch a *new* package off the moment somebody installed
    // one under the same id.
    write("brass", toml("brass", "Brass"))
    val presenter = loaded()
    presenter.setEnabled(presenter.state.sets.single { it.id == "brass" }, enabled = false)
    await("the set was never switched off") {
      !presenter.state.sets
        .single { it.id == "brass" }
        .enabled
    }

    File(root, "brass").deleteRecursively()
    presenter.refresh()
    await("the folder was never noticed as gone") { presenter.state.sets.none { it.id == "brass" } }

    assertTrue("the opinion outlived the folder", runBlocking { registry.isEnabled("brass") })
  }

  @Test
  fun `nothing is called empty until the disk has actually been read`() {
    // Otherwise "only the built-in dice" flashes up on every visit, before the
    // first reading has finished.
    val state = SetsState()

    assertFalse("empty was claimed before anything was read", state.empty)
    assertTrue("a loaded reading with one row is not empty", SetsState(loaded = true).empty)
  }

  /** A row with nothing read off a disk, for the rules that are only about the row. */
  private fun row(
    broken: Boolean = false,
    enabled: Boolean = true,
    bundled: Boolean = false,
  ) = SetRow(
    id = "brass",
    name = "Brass",
    set = if (broken) null else bundledSet(),
    report = emptyList(),
    meta = PackageMeta.Unknown,
    folder = null,
    enabled = enabled,
    bundled = bundled,
  )

  /** A presenter that has finished its first reading of the disk. */
  private fun loaded(): SetsPresenter =
    presenter().also { presenter -> await("the disk was never read") { presenter.state.loaded } }

  private fun presenter(): SetsPresenter = SetsPresenter(library(), scope)

  /** Polls until [until] holds, because Room answers on a thread of its own. */
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

  /** The two halves joined, with the disk and the database both real. */
  private fun library() =
    SetLibrary(
      bundled = bundledSet(),
      installed = InstalledSets(root),
      registry = registry,
      io = Dispatchers.Unconfined,
      installer = PackageInstaller(root),
      defaultSetId = { DiceSet.BUILTIN_ID },
    )

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
  ) = """
    format = 1

    [set]
    id = "$id"
    name = "$name"
    version = "1.0.0"

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
    /** A set with no name and no dice: enough to be a folder, not enough to be a set. */
    const val BROKEN_TOML = "format = 1\n\n[set]\nid = \"runes\"\n"

    const val PATIENCE_MS = 5_000L
    const val POLL_MS = 5L
  }
}
