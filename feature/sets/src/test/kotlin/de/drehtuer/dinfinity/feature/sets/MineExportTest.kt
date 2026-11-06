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
import de.drehtuer.dinfinity.designer.NewSet
import de.drehtuer.dinfinity.designer.PackageFile
import de.drehtuer.dinfinity.designer.PersonalSets
import de.drehtuer.dinfinity.designer.PhotoStore
import de.drehtuer.dinfinity.designer.PhysicalStore
import de.drehtuer.dinfinity.designer.SetLicense
import de.drehtuer.dinfinity.designer.Stroke
import de.drehtuer.dinfinity.designer.TablePhoto
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
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

    assertEquals(ExportResult.Empty, runBlocking { bare.exportPersonal(MinePackage.ID, SetLicense.Mit) })
  }

  @Test
  fun `a named set is personal, has steppers of its own and goes out under its own name`() {
    // Decision 79: every personal set has its own export and its own record
    // of what its dice are made of — and "My dice" is none the wiser.
    draw()
    val id = named("Brass & Bone")
    val presenter = detail(id)

    assertTrue(presenter.state.personal)
    assertTrue("a new set does not start at the average", presenter.state.declared == DieMaterial())
    presenter.weigh(2)
    presenter.choose(SetLicense.Mit)
    presenter.export()
    await("nothing was shared") { shared != null }

    val file = shared ?: error("nothing was shared")
    assertEquals("brass-bone.zip", file.name)
    val set = inside(file)
    assertEquals("brass-bone", set.id)
    assertEquals("Brass & Bone", set.name)
    assertEquals(
      DiePhysical.weighted(DieMaterial(), 2).density,
      set.dice
        .single()
        .material.density,
      1e-9,
    )
    val mine = runBlocking { library().one(MinePackage.ID) }?.set?.dice?.single() ?: error("my dice went missing")
    assertEquals("my dice took the other set's weight", DieMaterial().density, mine.material.density, 1e-9)
  }

  @Test
  fun `removing a named set takes its records too, and leaves My dice alone`() {
    // A package whose records stayed would be built straight back at the
    // next reading: a set nobody could remove.
    draw()
    val id = named("Props")
    val library = library()
    val row = runBlocking { library.one(id) } ?: error("the named set is not on the list")

    runBlocking { library.remove(row) }

    val rows = runBlocking { library().all() }
    assertTrue("the named set came back", rows.none { it.id == id })
    assertTrue("my dice went with it", rows.any { it.id == MinePackage.ID })
    assertFalse("its records are still there", File(File(temporary, PersonalSets.DIRECTORY), id).exists())
  }

  @Test
  fun `each thing the share waits for closes the gate on its own`() {
    val ready =
      SetDetailState(
        row =
          SetRow(
            id = MinePackage.ID,
            name = "My dice",
            set = bundled(),
            report = emptyList(),
            meta = PackageMeta(),
            folder = null,
            enabled = true,
          ),
        license = SetLicense.Mit,
      )
    val row = ready.row ?: error("the row went missing")

    assertTrue("everything was in place and the share was still refused", ready.canExport)
    assertFalse("shared with no licence", ready.copy(license = null).canExport)
    assertFalse("shared twice at once", ready.copy(exporting = true).canExport)
    assertFalse("a package that stopped validating was shared", ready.copy(row = row.copy(set = null)).canExport)
    assertFalse("somebody else's package was shared", ready.copy(row = row.copy(id = "brass")).canExport)
    assertFalse("nothing at all was shared", ready.copy(row = null).canExport)
  }

  @Test
  fun `a licence chosen on somebody else's package still shares nothing`() {
    // The button is not drawn there. This is the rule rather than the courtesy.
    write("brass")
    val presenter = detail("brass")

    presenter.choose(SetLicense.Mit)
    presenter.export()

    assertNull("somebody else's package went out", shared)
    assertFalse(presenter.state.exporting)
    assertFalse(presenter.state.exported)
  }

  @Test
  fun `a package the validator refuses is not shared, and the screen says which lines`() {
    // It should never happen, so the way to it is a picture past the limit,
    // put in the record behind the validator's back. The folder still holds the
    // good package from before, so the gate is open when the press comes.
    draw()
    val presenter = detail(MinePackage.ID)
    presenter.choose(SetLicense.Mit)
    photos.add(TablePhoto("photo-big", "Big", webp(TOO_WIDE, TOO_WIDE)))

    presenter.export()
    await("the export never finished") { !presenter.state.exporting }

    assertNull("a package that does not validate went out", shared)
    assertFalse("the screen says a file went out", presenter.state.exported)
    assertTrue(
      "the report does not say what was wrong: ${presenter.state.exportProblem}",
      presenter.state.exportProblem.any { "$TOO_WIDE" in it.text },
    )
  }

  @Test
  fun `changing the licence takes the refusal away with it`() {
    draw()
    val presenter = detail(MinePackage.ID)
    presenter.choose(SetLicense.Mit)
    photos.add(TablePhoto("photo-big", "Big", webp(TOO_WIDE, TOO_WIDE)))
    presenter.export()
    await("the export never finished") { presenter.state.exportProblem.isNotEmpty() }

    presenter.choose(null)

    assertTrue("a report about the last attempt outlived it", presenter.state.exportProblem.isEmpty())
  }

  @Test
  fun `a stepper already at the end of its range writes nothing`() {
    // The package is rebuilt whenever the record moves, and rasterising every
    // drawn face for a tap that changed nothing would be a stall for nothing.
    draw()
    val presenter = detail(MinePackage.ID)
    presenter.resize(-A_LONG_WAY)
    await("the tap never reached the record") { physical.material() == presenter.state.declared }
    val floor = presenter.state.declared
    val stamp = physical.stamp()

    presenter.resize(-1)

    assertEquals(floor, presenter.state.declared)
    assertEquals("the record was written again for nothing", stamp, physical.stamp())
  }

  @Test
  fun `a tap arriving after the set has gone writes nothing`() {
    // The screen is on its way out by then, and a write would bring back a
    // record of a set somebody has just removed.
    draw()
    val presenter = detail(MinePackage.ID)
    presenter.remove()
    await("the set was never removed") { presenter.state.gone }
    val stamp = physical.stamp()

    presenter.weigh(1)

    assertEquals("a removed set was written to", stamp, physical.stamp())
  }

  @Test
  fun `a material meant for a set this phone did not write goes nowhere`() {
    val untouched = physical.stamp()
    val heavy = DiePhysical.weighted(DieMaterial(), 1)
    val bare =
      SetLibrary(
        bundled = bundled(),
        installed = InstalledSets(root),
        registry = registry,
        io = Dispatchers.Unconfined,
        installer = PackageInstaller(root),
        defaultSetId = { DiceSet.BUILTIN_ID },
      )

    runBlocking {
      library().setPersonalPhysical("brass", heavy)
      bare.setPersonalPhysical(MinePackage.ID, heavy)
    }

    assertEquals("My dice was written to on another set's behalf", untouched, physical.stamp())
    assertNull("a library with no designer has a material to show", runBlocking { bare.personalPhysical("mine") })
  }

  /** What the details screen is currently saying the package is licensed under. */
  private fun licenseOnScreen(presenter: SetDetailPresenter): String? {
    val set = presenter.state.row?.set
    return set?.license
  }

  private fun draw() {
    drafts.save(drawing())
  }

  private fun drawing(): Draft =
    Draft(die = d6).onFace(0) {
      it.draw(Stroke(dots = listOf(Dot(0.2f, 0.2f), Dot(0.8f, 0.8f)), colorArgb = INK, width = 0.05f))
    }

  private fun library() =
    SetLibrary(
      bundled = bundled(),
      installed = InstalledSets(root),
      registry = registry,
      io = Dispatchers.Unconfined,
      installer = PackageInstaller(root),
      defaultSetId = { DiceSet.BUILTIN_ID },
      personal = personal(),
    )

  /** "My dice" on the records it has always had, and named sets beside it. */
  private fun personal() =
    PersonalSets(
      mine =
        MineSets(
          drafts = drafts,
          root = root,
          painter = BitmapAtlas(),
          dice = { listOf(d6) },
          photos = photos,
          physical = physical,
        ),
      records = File(temporary, PersonalSets.DIRECTORY),
    )

  /** Makes a named set with one drawing in it, the way the designer's sheet does. */
  private fun named(name: String): String {
    val sets = personal()
    val made = sets.create(name) as? NewSet.Made ?: error("the set was not made")
    sets.find(made.id)?.keep(drawing()) ?: error("the set went missing")
    return made.id
  }

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

  /** The first bytes of a lossless WebP, which is as much as the validator reads. */
  private fun webp(
    width: Int,
    height: Int,
  ): ByteArray {
    val bits = (width - 1) or ((height - 1) shl WEBP_HEIGHT_SHIFT)
    val littleEndian = { value: Int -> ByteArray(Int.SIZE_BYTES) { at -> (value ushr (at * Byte.SIZE_BITS)).toByte() } }
    return "RIFF".encodeToByteArray() + littleEndian(0) + "WEBPVP8L".encodeToByteArray() + littleEndian(0) +
      byteArrayOf(WEBP_LOSSLESS) + littleEndian(bits)
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

    /** Past `MAX_TEXTURE_PIXELS`, which the validator reads out of the header. */
    const val TOO_WIDE = 4096

    /** More taps than any stepper has stops. */
    const val A_LONG_WAY = 1_000
    const val WEBP_HEIGHT_SHIFT = 14
    const val WEBP_LOSSLESS: Byte = 0x2F
    const val PATIENCE_MS = 5_000L
    const val POLL_MS = 5L
  }
}
