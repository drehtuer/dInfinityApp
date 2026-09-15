package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * "My dice", as a folder on disk and as a file on its way out
 * (`docs/face-designer.md`, "Flow", step 5; design `8c`).
 *
 * Nothing here uses a device. The one part that does is the painter, which is
 * handed in — so what these tests can say is the whole of what the export
 * *decides*: which dice are in it, what the file says, that it validates, and
 * that the folder either holds a good package or holds the one it had before.
 */
class MineSetsTest {
  @get:Rule
  val temporary: TemporaryFolder = TemporaryFolder()

  private val cube = Die.standard(id = "d6", shape = DieShape.Cube)
  private val d20 = Die.standard(id = "d20", shape = DieShape.Icosahedron)

  private lateinit var drafts: DraftStore
  private lateinit var root: File

  private fun mine(
    painter: AtlasPainter = Drawings.headers(),
    author: String? = null,
  ): MineSets {
    drafts = DraftStore(temporary.newFolder("drafts"))
    root = temporary.newFolder("dicesets")
    return MineSets(drafts = drafts, root = root, painter = painter, dice = { listOf(cube, d20) }, author = { author })
  }

  @Test
  fun `a drawing becomes a package the standard reader installs`() {
    val sets = mine()
    drafts.save(Drawings.drawn(cube, 0, 2))

    sets.bringUpToDate()

    val installed = DiceSetValidator.validate(PackageFiles.of(File(root, "mine")))
    assertTrue("$installed", installed is ValidationResult.Valid)
    assertEquals(listOf("d6"), (installed as ValidationResult.Valid).set.dice.map(Die::id))
  }

  @Test
  fun `a package built without a licence says unspecified rather than nothing`() {
    val sets = mine()
    drafts.save(Drawings.drawn(cube, 0))

    sets.bringUpToDate()

    assertEquals(SetLicense.UNSPECIFIED, installed().license)
  }

  @Test
  fun `exporting writes the chosen licence into the file and into the folder`() {
    val sets = mine()
    drafts.save(Drawings.drawn(cube, 0))

    val result = sets.export(SetLicense.Attribution)

    assertTrue(result is ExportResult.Ready)
    assertEquals("CC-BY-4.0", inside(result as ExportResult.Ready).license)
    // And the screen goes on saying it afterwards, because the folder says it.
    assertEquals("CC-BY-4.0", installed().license)
  }

  @Test
  fun `a licence somebody chose survives the next stroke they draw`() {
    val sets = mine()
    drafts.save(Drawings.drawn(cube, 0))
    sets.export(SetLicense.Gpl)

    drafts.save(Drawings.drawn(d20, 1))
    sets.bringUpToDate()

    assertEquals("GPL-2.0-or-later", installed().license)
    assertEquals(listOf("d20", "d6"), installed().dice.map(Die::id))
  }

  @Test
  fun `the file is a zip of the package, read back by the same validator`() {
    val sets = mine()
    drafts.save(Drawings.drawn(d20, 3))

    val ready = sets.export(SetLicense.Mit) as ExportResult.Ready
    val unpacked = unzip(ready.file.bytes)

    // The round trip: drawn here, zipped, unzipped, and read by the code an
    // installed package goes through.
    val read = DiceSetValidator.validate(PackageFiles.of(unpacked))
    assertTrue("$read", read is ValidationResult.Valid)
    val die = (read as ValidationResult.Valid).set.dice.single()
    assertEquals("d20", die.id)
    assertEquals(DieShape.Icosahedron, die.shape)
    assertEquals(d20.values(), die.values())
    assertEquals("textures/d20.png", die.texturePath)
    assertNotNull(unpacked["textures/d20.png"])
  }

  @Test
  fun `the file is called what the share sheet offers`() {
    val sets = mine()
    drafts.save(Drawings.drawn(cube, 0))

    assertEquals("my-dice.zip", (sets.export(SetLicense.Mit) as ExportResult.Ready).file.name)
  }

  @Test
  fun `nothing drawn is nothing to export`() {
    val sets = mine()

    assertEquals(ExportResult.Empty, sets.export(SetLicense.Mit))
  }

  @Test
  fun `a draft on a die that is not installed is not in the package`() {
    val sets = mine()
    drafts.save(Drawings.drawn(Die.standard(id = "d8", shape = DieShape.Octahedron), 0))
    drafts.save(Drawings.drawn(cube, 0))

    sets.bringUpToDate()

    // The draft file is kept — re-installing the package brings the drawing
    // back (`docs/face-designer.md`) — but a die nothing defines is not a die
    // this package can carry.
    assertEquals(listOf("d6"), installed().dice.map(Die::id))
    assertTrue(drafts.known().contains("d8"))
  }

  @Test
  fun `the last drawing deleted takes the package with it`() {
    val sets = mine()
    drafts.save(Drawings.drawn(cube, 0))
    sets.bringUpToDate()
    assertTrue(File(root, "mine").isDirectory)

    drafts.forget("d6")
    sets.bringUpToDate()

    assertFalse(File(root, "mine").exists())
  }

  @Test
  fun `a rebuild happens only when a drawing has moved on`() {
    val sets = mine()
    drafts.save(Drawings.drawn(cube, 0))
    sets.bringUpToDate()
    val written = File(root, "mine/${DiceSetValidator.DICE_SET_FILE}")
    written.writeText("this is not a set file")

    sets.bringUpToDate()

    // Rasterising every drawn face is far too much to do on every reading of
    // the folder, so an unchanged drafts folder is left alone — which is
    // exactly what this vandalised file proves.
    assertEquals("this is not a set file", written.readText())
  }

  @Test
  fun `a drawing that changes brings the package back into step`() {
    val sets = mine()
    drafts.save(Drawings.drawn(cube, 0))
    sets.bringUpToDate()
    assertEquals(listOf("d6"), installed().dice.map(Die::id))

    drafts.save(Drawings.drawn(d20, 0))
    sets.bringUpToDate()

    assertEquals(listOf("d20", "d6"), installed().dice.map(Die::id))
  }

  @Test
  fun `a die whose atlas will not paint still exports, with its labels`() {
    val sets = mine(painter = AtlasPainter.NONE)
    drafts.save(Drawings.drawn(cube, 0))

    val ready = sets.export(SetLicense.Mit) as ExportResult.Ready

    assertNull(inside(ready).dice.single().texturePath)
  }

  @Test
  fun `the author is written when there is one to write`() {
    val sets = mine(author = "Ada")
    drafts.save(Drawings.drawn(cube, 0))

    assertEquals("Ada", inside(sets.export(SetLicense.Mit) as ExportResult.Ready).author)
  }

  @Test
  fun `the staging folders are not mistaken for packages`() {
    val sets = mine()
    drafts.save(Drawings.drawn(cube, 0))

    sets.export(SetLicense.Mit)

    // A scan skips anything beginning with a dot (`InstalledSets.scan`), so a
    // swap caught halfway cannot show up as a second dice set.
    val left = root.listFiles().orEmpty().map { it.name }
    assertEquals(listOf("mine"), left.filterNot { it.startsWith(".") })
  }

  @Test
  fun `a package with nobody's name on it is the default`() {
    // The author field is left out rather than filled with "You", which would
    // be a name on somebody else's phone.
    drafts = DraftStore(temporary.newFolder("plain-drafts"))
    root = temporary.newFolder("plain-sets")
    val sets = MineSets(drafts = drafts, root = root, painter = Drawings.headers(), dice = { listOf(cube) })
    drafts.save(Drawings.drawn(cube, 0))

    val ready = sets.export(SetLicense.Mit) as ExportResult.Ready

    assertNull(inside(ready).author)
  }

  @Test
  fun `a folder that cannot be written costs the folder, not the file`() {
    // `root` is a plain file here, so nothing can be created under it. The zip
    // is what was asked for and it is still handed over; the installed package
    // is simply not brought up to date, which the next reading will try again.
    val blocked = temporary.newFile("not-a-folder")
    drafts = DraftStore(temporary.newFolder("blocked-drafts"))
    val sets = MineSets(drafts = drafts, root = blocked, painter = Drawings.headers(), dice = { listOf(cube) })
    drafts.save(Drawings.drawn(cube, 0))

    val result = sets.export(SetLicense.Mit)

    assertTrue(result is ExportResult.Ready)
    assertTrue(blocked.isFile)
  }

  @Test
  fun `a rejection carries every line of the report, not the first`() {
    // It should be unreachable — the app's own package validates — and it is
    // carried rather than swallowed because a drawing that produces an invalid
    // set is a bug, and the lines say which (`docs/dice-sets.md`, rule 2).
    val lines =
      DiceSetValidator.validate(PackageFiles.ofDiceSetToml("format = 9")).messages

    val rejected = ExportResult.Rejected(lines)

    assertEquals(lines, rejected.report)
    assertTrue(rejected.report.isNotEmpty())
  }

  /** What the installed folder says, through the real validator. */
  private fun installed() =
    (DiceSetValidator.validate(PackageFiles.of(File(root, "mine"))) as ValidationResult.Valid).set

  /** What the exported zip says, through the real validator. */
  private fun inside(ready: ExportResult.Ready) =
    (DiceSetValidator.validate(PackageFiles.of(unzip(ready.file.bytes))) as ValidationResult.Valid).set

  private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
    val out = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
      while (true) {
        val entry = zip.getNextEntry() ?: break
        out[entry.name] = zip.readBytes()
      }
    }
    return out
  }
}
