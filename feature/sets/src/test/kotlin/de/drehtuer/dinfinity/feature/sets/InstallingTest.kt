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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Installing a dice set from a file (`design/dInfinity.dc.html`, option `1t`;
 * `docs/dice-sets.md`, "Installing from a URL or file").
 *
 * The order is the thing being relied on and it is [PackageInstaller]'s: fetch,
 * extract, validate *there*, and only then move into place. So what these say
 * is what the **screen** does with the two answers — that a refusal shows every
 * error rather than the first, that nothing appears in the list when one
 * happens, and that the temporary copy of the archive is let go however it
 * ends.
 *
 * Archives are built with the JDK's own zip writer rather than a test
 * dependency: an installable package is one file in one folder, and that needs
 * no library to write.
 */
@RunWith(RobolectricTestRunner::class)
class InstallingTest {
  private val temporary: File = Files.createTempDirectory("dinfinity-install-ui").toFile()
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
  fun `a good package installs and appears in the list`() {
    val presenter = loaded()

    presenter.install(zip("brass", toml("brass", "Brass and Bone")))
    await("the install never finished") { presenter.state.outcome != null }

    val outcome = presenter.state.outcome
    assertTrue("the install was refused: $outcome", outcome is PackageInstaller.Result.Installed)
    assertEquals("Brass and Bone", (outcome as PackageInstaller.Result.Installed).set.name)
    assertFalse("a first install said it replaced something", outcome.replaced)
    await("the new set never reached the list") { presenter.state.sets.any { it.id == "brass" } }
  }

  @Test
  fun `a package that does not validate is refused, with every error`() {
    // An author fixing a set wants the whole list. Stopping at the first
    // problem is one round trip per mistake.
    val presenter = loaded()

    presenter.install(zip("runes", "format = 1\n\n[set]\nid = \"runes\"\n"))
    await("the install never finished") { presenter.state.outcome != null }

    val outcome = presenter.state.outcome
    assertTrue(outcome is PackageInstaller.Result.Failed)
    assertTrue("nothing was said about what was wrong", (outcome as PackageInstaller.Result.Failed).report.isNotEmpty())
    assertEquals("something was installed anyway", listOf("builtin"), presenter.state.sets.map { it.id })
    assertFalse("a folder was left behind", File(root, "runes").exists())
  }

  @Test
  fun `installing over a set that is already there replaces it, and says so`() {
    // The same operation as an update, and the sheet has to read differently
    // or the player cannot tell one from the other.
    val presenter = loaded()
    presenter.install(zip("brass", toml("brass", "Brass")))
    await("the first install never finished") { presenter.state.outcome != null }
    presenter.dismiss()

    presenter.install(zip("brass", toml("brass", "Brass II")))
    await("the second install never finished") { presenter.state.outcome != null }

    val outcome = presenter.state.outcome as PackageInstaller.Result.Installed
    assertTrue("replacing a set did not say so", outcome.replaced)
    assertEquals("Brass II", outcome.set.name)
    await("the list still showed the old one") { presenter.state.sets.any { it.name == "Brass II" } }
  }

  @Test
  fun `a file that cannot be opened is refused without an install being tried`() {
    // The picker handed back something unreadable. To the player it is the
    // same sentence — nothing was installed, and here is why.
    val presenter = loaded()

    presenter.refused("nothing answered at that file")

    val outcome = presenter.state.outcome
    assertTrue(outcome is PackageInstaller.Result.Failed)
    assertEquals("nothing answered at that file", (outcome as PackageInstaller.Result.Failed).reason)
    assertFalse(presenter.state.installing)
  }

  @Test
  fun `the temporary copy is let go however the install ends`() {
    // The bytes are a stranger's and the copy exists only so the installer has
    // something to open. Leaving it behind on a failure is the case that would
    // actually matter.
    val presenter = loaded()
    var released = 0

    presenter.install(zip("runes", "not toml at all")) { released++ }
    await("the install never finished") { presenter.state.outcome != null }

    assertEquals("a refused install kept the archive", 1, released)
  }

  @Test
  fun `a second install while one is running is ignored`() {
    // Two extractions racing for one folder is the one thing the installer's
    // own ordering cannot protect against, because both of them are it.
    val presenter = loaded()
    val state = SetsState(installing = true)

    assertTrue("a running install did not say so", state.installing)

    presenter.install(zip("brass", toml("brass", "Brass")))
    await("the first install never finished") { presenter.state.outcome != null }
    assertFalse("it was still marked as installing afterwards", presenter.state.installing)
  }

  @Test
  fun `what the last install said can be put away`() {
    val presenter = loaded()
    presenter.install(zip("brass", toml("brass", "Brass")))
    await("the install never finished") { presenter.state.outcome != null }

    presenter.dismiss()

    assertNull(presenter.state.outcome)
  }

  @Test
  fun `an archive that is not an archive is refused rather than thrown`() {
    val presenter = loaded()
    val rubbish = File(temporary, "rubbish").apply { writeText("this is not a zip") }

    presenter.install(rubbish)
    await("the install never finished") { presenter.state.outcome != null }

    assertTrue(presenter.state.outcome is PackageInstaller.Result.Failed)
  }

  @Test
  fun `a set that installs becomes a set a formula can resolve against`() {
    // The point of the whole step. A package that installs and whose dice
    // cannot be rolled is a list entry, not a dice set.
    val presenter = loaded()
    assertNull("a set nobody installed was already in the catalogue", library.catalogue.set("brass"))

    presenter.install(zip("brass", toml("brass", "Brass")))
    await("the new set never reached the list") { presenter.state.sets.any { it.id == "brass" } }

    assertEquals("Brass", library.catalogue.set("brass")?.name)
    assertEquals(
      listOf("d6"),
      library.catalogue
        .set("brass")
        ?.dice
        ?.map { it.id },
    )
  }

  @Test
  fun `switching a set off takes its dice out of the catalogue`() {
    // Switched off has to mean *not offered*, or the setting is decoration.
    val presenter = loaded()
    presenter.install(zip("brass", toml("brass", "Brass")))
    await("the new set never reached the list") { presenter.state.sets.any { it.id == "brass" } }

    presenter.setEnabled(presenter.state.sets.single { it.id == "brass" }, enabled = false)
    await("the set was never switched off") {
      presenter.state.sets
        .single { it.id == "brass" }
        .enabled
        .not()
    }

    assertNull("a set that was switched off still handed out dice", library.catalogue.set("brass"))
  }

  @Test
  fun `a set that stopped validating hands out no dice either`() {
    // The other way a set can be unusable. Both have to reach the catalogue,
    // or a broken package would go on resolving from whatever it said last.
    write("runes", "format = 1\n\n[set]\nid = \"runes\"\n")

    val presenter = loaded()

    assertTrue("a broken package was not listed", presenter.state.sets.any { it.id == "runes" })
    assertNull("a broken package handed out dice", library.catalogue.set("runes"))
  }

  @Test
  fun `the bundled set is always in the catalogue, and is what everything falls back to`() {
    loaded()

    assertEquals(DiceSet.BUILTIN_ID, library.catalogue.defaultSetId)
    assertEquals("Standard", library.catalogue.set(DiceSet.BUILTIN_ID)?.name)
  }

  @Test
  fun `removing a set takes its dice with it`() {
    val presenter = loaded()
    presenter.install(zip("brass", toml("brass", "Brass")))
    await("the new set never reached the list") { presenter.state.sets.any { it.id == "brass" } }

    presenter.remove(presenter.state.sets.single { it.id == "brass" })
    await("the set was never removed") { presenter.state.sets.none { it.id == "brass" } }

    assertNull("a removed set still handed out dice", library.catalogue.set("brass"))
  }

  @Test
  fun `the chosen default is what a plain die resolves against`() {
    val presenter = loaded()
    presenter.install(zip("brass", toml("brass", "Brass")))
    await("the new set never reached the list") { presenter.state.sets.any { it.id == "brass" } }

    chosenDefault = "brass"

    assertEquals("brass", library.catalogue.defaultSetId)
  }

  @Test
  fun `a default that is not installed falls back to the bundled set`() {
    // The setting can name a set that was removed, or one that has not been
    // installed yet. A catalogue pointing at a set nobody has cannot resolve
    // `d20` at all, so the bundled set stands in.
    loaded()

    chosenDefault = "a-set-nobody-has"

    assertEquals(DiceSet.BUILTIN_ID, library.catalogue.defaultSetId)
  }

  @Test
  fun `a default that is switched off falls back, and is remembered`() {
    // Falling back is about resolving a formula now. It must not quietly
    // rewrite the setting: a set switched off for an evening is still the one
    // the player chose.
    val presenter = loaded()
    presenter.install(zip("brass", toml("brass", "Brass")))
    await("the new set never reached the list") { presenter.state.sets.any { it.id == "brass" } }
    chosenDefault = "brass"

    presenter.setEnabled(presenter.state.sets.single { it.id == "brass" }, enabled = false)
    await("the set was never switched off") {
      presenter.state.sets
        .single { it.id == "brass" }
        .enabled
        .not()
    }

    assertEquals(DiceSet.BUILTIN_ID, library.catalogue.defaultSetId)
    assertEquals("the setting was rewritten behind the player", "brass", chosenDefault)
  }

  @Test
  fun `removing the default set falls back without leaving the catalogue broken`() {
    val presenter = loaded()
    presenter.install(zip("brass", toml("brass", "Brass")))
    await("the new set never reached the list") { presenter.state.sets.any { it.id == "brass" } }
    chosenDefault = "brass"

    presenter.remove(presenter.state.sets.single { it.id == "brass" })
    await("the set was never removed") { presenter.state.sets.none { it.id == "brass" } }

    assertEquals(DiceSet.BUILTIN_ID, library.catalogue.defaultSetId)
    assertEquals("Standard", library.catalogue.set(library.catalogue.defaultSetId)?.name)
  }

  private fun write(
    id: String,
    toml: String,
  ) {
    val folder = File(root, id).apply { mkdirs() }
    File(folder, DiceSetValidator.DICE_SET_FILE).writeText(toml)
  }

  /** What the settings say the default is, as the application's field would. */
  private var chosenDefault: String = DiceSet.BUILTIN_ID

  private val library: SetLibrary by lazy {
    SetLibrary(
      bundled = bundledSet(),
      installed = InstalledSets(root),
      registry = registry,
      io = Dispatchers.Unconfined,
      installer = PackageInstaller(root),
      defaultSetId = { chosenDefault },
    )
  }

  private fun loaded(): SetsPresenter =
    SetsPresenter(library, scope)
      .also { presenter -> await("the disk was never read") { presenter.state.loaded } }

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

  /** A package as one folder inside a zip, which is what a forge hands out. */
  private fun zip(
    id: String,
    toml: String,
  ): File {
    val file = File(temporary, "$id-${System.nanoTime()}.zip")
    ZipOutputStream(file.outputStream()).use { out ->
      val bytes = toml.encodeToByteArray()
      out.putNextEntry(ZipEntry("$id-main/${DiceSetValidator.DICE_SET_FILE}"))
      out.write(bytes)
      out.closeEntry()
    }
    return file
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
    const val PATIENCE_MS = 5_000L
    const val POLL_MS = 5L
  }
}
