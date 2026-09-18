package de.drehtuer.dinfinity.feature.sets

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DiePhysical
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.data.InstalledSetRepository
import de.drehtuer.dinfinity.data.db.DInfinityDatabase
import de.drehtuer.dinfinity.designer.BitmapAtlas
import de.drehtuer.dinfinity.designer.Dot
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.DraftStore
import de.drehtuer.dinfinity.designer.ExportResult
import de.drehtuer.dinfinity.designer.MinePackage
import de.drehtuer.dinfinity.designer.MineSets
import de.drehtuer.dinfinity.designer.PackageFile
import de.drehtuer.dinfinity.designer.PhotoStore
import de.drehtuer.dinfinity.designer.PhysicalStore
import de.drehtuer.dinfinity.designer.SetLicense
import de.drehtuer.dinfinity.designer.Stroke
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.dicesets.install.PackageInstaller
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
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipInputStream

/**
 * "My dice", from a drawing to a file somebody can upload
 * (`design/dInfinity.dc.html`, option `8c`; `docs/face-designer.md`).
 *
 * The whole way through, with the real rasteriser and the real validator: a
 * draft on disk, the package built from it, the licence gate, the zip. The
 * only thing standing in for something is the share sheet, because what the
 * screen hands out is a file and where it goes is the application's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MineExportTest {
  private val temporary: File = Files.createTempDirectory("dinfinity-mine").toFile()
  private val root = File(temporary, "dicesets")
  private val drafts = DraftStore(File(temporary, "drafts"))
  private val photos = PhotoStore(File(temporary, "table-photos"))
  private val physical = PhysicalStore(File(temporary, PhysicalStore.FILE_NAME))
  private val scope = CoroutineScope(Dispatchers.Unconfined)
  private lateinit var database: DInfinityDatabase
  private lateinit var registry: InstalledSetRepository
  private var shared: PackageFile? = null

  private val d6 = Die.standard(id = "d6", shape = DieShape.Cube)

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
    registry = InstalledSetRepository(database)
    root.mkdirs()
  }

  @After
  fun close() {
    scope.cancel()
    database.close()
    temporary.deleteRecursively()
  }

  @Test
  fun `a drawing turns up on the list as an ordinary installed package`() {
    draw()

    val rows = runBlocking { library().all() }

    // Nothing on the list knows it is special: it is a folder in `dicesets/`
    // with a `diceset.toml` in it, like everything else there.
    val mine = rows.single { it.id == MinePackage.ID }
    assertEquals("My dice", mine.name)
    assertEquals(1, mine.dice)
    assertTrue(mine.personal)
    assertFalse(mine.broken)
  }

  @Test
  fun `nothing drawn is no package at all`() {
    val rows = runBlocking { library().all() }

    assertTrue(rows.none { it.id == MinePackage.ID })
  }

  @Test
  fun `the export will not happen until a licence has been chosen`() {
    draw()
    val presenter = detail(MinePackage.ID)

    assertTrue(presenter.state.personal)
    assertNull(presenter.state.license)
    assertFalse(presenter.state.canExport)

    presenter.export()

    // Pressed anyway — through a stale frame, a test, or an accessibility
    // service — and still nothing goes out.
    assertNull(shared)
    assertFalse(presenter.state.exported)
  }

  @Test
  fun `choosing a licence opens the gate, and the file carries it`() {
    draw()
    val presenter = detail(MinePackage.ID)

    presenter.choose(SetLicense.ShareAlike)
    assertTrue(presenter.state.canExport)
    presenter.export()
    await("nothing was shared") { shared != null }

    val file = shared ?: error("nothing was shared")
    assertEquals("my-dice.zip", file.name)
    assertEquals("CC-BY-SA-4.0", inside(file).license)
  }

  @Test
  fun `the exported package is what was drawn, read back by the standard reader`() {
    draw()
    val presenter = detail(MinePackage.ID)

    presenter.choose(SetLicense.Mit)
    presenter.export()
    await("nothing was shared") { shared != null }

    val set = inside(shared ?: error("nothing was shared"))
    val die = set.dice.single()
    assertEquals("d6", die.id)
    assertEquals(DieShape.Cube, die.shape)
    assertEquals(listOf(1, 2, 3, 4, 5, 6), die.values())
    assertEquals("textures/d6.png", die.texturePath)
  }

  @Test
  fun `what was chosen is what the screen says afterwards`() {
    draw()
    val presenter = detail(MinePackage.ID)

    presenter.choose(SetLicense.PublicDomain)
    presenter.export()
    await("the screen never caught up") { licenseOnScreen(presenter) == "CC0-1.0" }

    assertTrue(presenter.state.exported)
  }

  @Test
  fun `taking the choice back closes the gate again`() {
    draw()
    val presenter = detail(MinePackage.ID)
    presenter.choose(SetLicense.Mit)
    presenter.export()
    await("nothing was shared") { shared != null }

    presenter.choose(null)

    assertFalse(presenter.state.canExport)
    // And the note about the file that went out goes with it: it stood under
    // a licence that is no longer the one on the screen.
    assertFalse(presenter.state.exported)
  }

  @Test
  fun `my dice carries the steppers, and a run of taps accumulates`() {
    // The design of 2026-09-17: 0.1 g a tap, each one reading the live value
    // so a rapid run adds up rather than fighting the last frame
    // (`docs/dice-sets.md`, "Weight, translucency and size, as a person sets
    // them").
    draw()
    val presenter = detail(MinePackage.ID)
    assertTrue(presenter.state.editable)
    val before =
      presenter.state.physical
        ?.weightG
        ?.low ?: error("a package with a die has a weight")

    repeat(3) { presenter.weigh(1) }

    val after =
      presenter.state.physical
        ?.weightG
        ?.low ?: error("a package with a die has a weight")
    assertEquals(before + 3 * DiePhysical.WEIGHT_STEP_G, after, 1e-9)
  }

  @Test
  fun `what the steppers set is what the package on disk says afterwards`() {
    draw()
    val presenter = detail(MinePackage.ID)

    presenter.weigh(2)
    presenter.seeThrough(4)
    presenter.resize(1)

    val declared = presenter.state.declared ?: error("my dice was given nothing to edit")
    // A second library, so the package really is rebuilt from the records
    // rather than remembered: this is the path a drawn face takes too.
    val die = runBlocking { library().one(MinePackage.ID) }?.set?.dice?.single() ?: error("the package went missing")
    assertEquals(declared.density, die.material.density, 1e-9)
    assertEquals(0.2, die.material.translucency, 1e-9)
    assertEquals(105.0, DiePhysical.sizePercentOf(die.material.sizeMm), 1e-9)
  }

  @Test
  fun `the numbers are kept between one visit and the next`() {
    draw()
    detail(MinePackage.ID).weigh(1)

    val again = detail(MinePackage.ID)

    assertEquals(DiePhysical.weighted(DieMaterial(), 1).density, again.state.declared?.density ?: 0.0, 1e-9)
  }

  @Test
  fun `somebody else's package is not offered an export`() {
    write("brass")

    val presenter = detail("brass")

    assertFalse(presenter.state.personal)
    assertFalse(presenter.state.canExport)
  }

  @Test
  fun `a library with no designer behind it exports nothing and does not fall over`() {
    val bare =
      SetLibrary(
        bundled = bundled(),
        installed = InstalledSets(root),
        registry = registry,
        io = Dispatchers.Unconfined,
        installer = PackageInstaller(root),
        defaultSetId = { DiceSet.BUILTIN_ID },
      )

    assertEquals(ExportResult.Empty, runBlocking { bare.exportPersonal(SetLicense.Mit) })
  }

  /** What the details screen is currently saying the package is licensed under. */
  private fun licenseOnScreen(presenter: SetDetailPresenter): String? {
    val set = presenter.state.row?.set
    return set?.license
  }

  private fun draw() {
    drafts.save(
      Draft(die = d6).onFace(0) {
        it.draw(Stroke(dots = listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f)), colorArgb = INK, width = 0.05f))
      },
    )
  }

  private fun library() =
    SetLibrary(
      bundled = bundled(),
      installed = InstalledSets(root),
      registry = registry,
      io = Dispatchers.Unconfined,
      installer = PackageInstaller(root),
      defaultSetId = { DiceSet.BUILTIN_ID },
      personal =
        MineSets(
          drafts = drafts,
          root = root,
          painter = BitmapAtlas(),
          dice = { listOf(d6) },
          photos = photos,
          physical = physical,
        ),
    )

  private fun detail(id: String): SetDetailPresenter =
    SetDetailPresenter(
      id = id,
      library = library(),
      scope = scope,
      onGone = {},
      defaultSetId = { DiceSet.BUILTIN_ID },
      onDefault = {},
      onShare = { file -> shared = file },
    ).also { presenter -> await("the folder was never read") { presenter.state.loaded } }

  /** What the exported file says, through the validator an install uses. */
  private fun inside(file: PackageFile): DiceSet {
    val unpacked = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(file.bytes)).use { zip ->
      while (true) {
        val entry = zip.getNextEntry() ?: break
        unpacked[entry.name] = zip.readBytes()
      }
    }
    val read = DiceSetValidator.validate(PackageFiles.of(unpacked))
    assertTrue("$read", read is ValidationResult.Valid)
    return (read as ValidationResult.Valid).set
  }

  private fun write(id: String) {
    val folder = File(root, id).apply { mkdirs() }
    File(folder, DiceSetValidator.DICE_SET_FILE).writeText(
      """
      format = 1

      [set]
      id = "$id"
      name = "Brass and Bone"
      version = "1.0.0"

      [[die]]
      id = "d6"
      shape = "cube"
      faces = [1, 2, 3, 4, 5, 6]
      """.trimIndent(),
    )
  }

  private fun bundled() = DiceSet(id = DiceSet.BUILTIN_ID, name = "Standard", version = "1.0.0", dice = listOf(d6))

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

  private companion object {
    const val INK = 0xFF202020.toInt()
    const val PATIENCE_MS = 5_000L
    const val POLL_MS = 5L
  }
}
