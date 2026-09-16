package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A photograph becoming a table in the personal package
 * (`docs/tables.md`, "Your own photo").
 *
 * The rule under test is the one `.claude/CLAUDE.md` names: **the app's own
 * output is not a privileged path.** A photo is written into `mine/` and that
 * package goes through the same `DiceSetValidator` a downloaded one goes
 * through — so these tests read the folder back with the real validator rather
 * than asserting on what was passed in.
 *
 * Nothing here decodes a pixel. The bytes are a WebP header of the right size,
 * which is exactly what the validator reads (`ImageHeader`), and the decoding
 * is `BitmapPhotoTest`'s.
 */
class MinePhotoTablesTest {
  @get:Rule
  val temporary: TemporaryFolder = TemporaryFolder()

  private val cube = Die.standard(id = "d6", shape = DieShape.Cube)

  private lateinit var drafts: DraftStore
  private lateinit var photos: PhotoStore
  private lateinit var root: File

  private fun mine(limit: Int = PhotoTable.MAX_PHOTOS): MineSets {
    drafts = DraftStore(temporary.newFolder("drafts"))
    photos = PhotoStore(temporary.newFolder("photos"), limit = limit)
    root = temporary.newFolder("dicesets")
    return MineSets(
      drafts = drafts,
      root = root,
      painter = Drawings.headers(),
      dice = { listOf(cube) },
      photos = photos,
    )
  }

  /** What the standard reader makes of the folder on disk. */
  private fun installed(): ValidationResult = DiceSetValidator.validate(PackageFiles.of(File(root, "mine")))

  private fun looks(): List<TableLook> = (installed() as ValidationResult.Valid).set.tables

  @Test
  fun `a photo becomes a table the standard reader installs`() {
    val sets = mine()

    val result = sets.addPhoto("Oak table", Drawings.webp(2048, 1542))

    assertTrue("$result", result is PhotoResult.Added)
    // The look it hands back is the look the picker will list, so it has to be
    // the one that reached the file rather than a copy that drifted from it.
    assertEquals(looks().single(), (result as PhotoResult.Added).look)
    assertEquals(listOf("photo-oak-table"), looks().map(TableLook::id))
    assertEquals("Oak table", looks().single().name)
    assertEquals("tables/photo-oak-table.webp", looks().single().floorTexturePath)
    assertEquals(TableLook.Tiling(1, 1), looks().single().floorTiling)
  }

  @Test
  fun `the picture is really in the package, under the path the table names`() {
    val sets = mine()
    sets.addPhoto("Oak", Drawings.webp(64, 48))

    val texture = File(root, "mine/tables/photo-oak.webp")

    assertTrue("the texture the table points at is not there", texture.isFile)
    assertEquals(Drawings.webp(64, 48).size.toLong(), texture.length())
  }

  @Test
  fun `a package of nothing but photos is as ordinary as one of nothing but dice`() {
    val sets = mine()

    sets.addPhoto("Oak", Drawings.webp(64, 48))

    val valid = installed() as ValidationResult.Valid
    assertTrue("a package with no dice was refused", valid.set.dice.isEmpty())
    assertEquals(1, valid.set.tables.size)
  }

  @Test
  fun `a photo and a drawing live in the same package`() {
    val sets = mine()
    drafts.save(Drawings.drawn(cube, 0, 2))
    sets.bringUpToDate()

    sets.addPhoto("Oak", Drawings.webp(64, 48))

    val valid = installed() as ValidationResult.Valid
    assertEquals(listOf("d6"), valid.set.dice.map(Die::id))
    assertEquals(listOf("photo-oak"), valid.set.tables.map(TableLook::id))
  }

  @Test
  fun `a second photo of the same name is a second table`() {
    val sets = mine()
    sets.addPhoto("Oak", Drawings.webp(64, 48))

    sets.addPhoto("Oak", Drawings.webp(64, 48))

    assertEquals(listOf("photo-oak", "photo-oak-2"), looks().map(TableLook::id))
  }

  @Test
  fun `a photo that makes the package invalid leaves nothing behind`() {
    // 4096 px is past `MAX_TEXTURE_PIXELS`, which the validator reads out of
    // the header. It cannot happen through `PhotoScaling`, which is the point:
    // the validator is the thing that decides, not the scaler.
    val sets = mine()
    sets.addPhoto("Oak", Drawings.webp(64, 48))

    val result = sets.addPhoto("Too big", Drawings.webp(4096, 4096))

    assertTrue("$result", result is PhotoResult.Rejected)
    assertTrue(
      "the report does not say what was wrong",
      (result as PhotoResult.Rejected).report.any { "4096" in it.text },
    )
    assertEquals("the refused photo was left in the store", setOf("photo-oak"), photos.ids())
    assertEquals("the refused table reached the package", listOf("photo-oak"), looks().map(TableLook::id))
  }

  @Test
  fun `a refused photo does not take the drawings with it`() {
    val sets = mine()
    drafts.save(Drawings.drawn(cube, 0))
    sets.bringUpToDate()

    sets.addPhoto("Too big", Drawings.webp(4096, 4096))

    val valid = installed() as ValidationResult.Valid
    assertEquals(listOf("d6"), valid.set.dice.map(Die::id))
    assertTrue(valid.set.tables.isEmpty())
  }

  @Test
  fun `a photo with no name is refused before anything is written`() {
    val sets = mine()

    assertEquals(PhotoResult.Unnamed, sets.addPhoto("   ", Drawings.webp(64, 48)))
    assertTrue(photos.ids().isEmpty())
  }

  @Test
  fun `the cap is a refusal with the number in it`() {
    val sets = mine(limit = 1)
    sets.addPhoto("Oak", Drawings.webp(64, 48))

    val result = sets.addPhoto("Elm", Drawings.webp(64, 48))

    assertEquals(PhotoResult.NoRoom(1), result)
    assertEquals("the refusal does not say how many are kept", 1, (result as PhotoResult.NoRoom).kept)
    assertEquals(setOf("photo-oak"), photos.ids())
  }

  @Test
  fun `a disk that will not take it says so and leaves nothing`() {
    drafts = DraftStore(temporary.newFolder("blocked-drafts"))
    photos = PhotoStore(temporary.newFolder("blocked-photos"))
    root = temporary.newFile("not-a-folder")
    val sets =
      MineSets(drafts = drafts, root = root, painter = Drawings.headers(), dice = { listOf(cube) }, photos = photos)

    val result = sets.addPhoto("Oak", Drawings.webp(64, 48))

    assertEquals(PhotoResult.NotWritten, result)
    assertTrue("the photo was kept although the package was not written", photos.ids().isEmpty())
  }

  @Test
  fun `removing a photo takes it out of the package too`() {
    val sets = mine()
    sets.addPhoto("Oak", Drawings.webp(64, 48))
    sets.addPhoto("Elm", Drawings.webp(64, 48))

    sets.removePhoto("photo-oak")

    assertEquals(listOf("photo-elm"), looks().map(TableLook::id))
    assertFalse(File(root, "mine/tables/photo-oak.webp").exists())
  }

  @Test
  fun `removing the last photo of an empty set takes the package away`() {
    val sets = mine()
    sets.addPhoto("Oak", Drawings.webp(64, 48))

    sets.removePhoto("photo-oak")

    assertFalse("the personal package outlived everything in it", File(root, "mine").exists())
  }

  @Test
  fun `removing one that is not there does nothing`() {
    val sets = mine()
    sets.addPhoto("Oak", Drawings.webp(64, 48))

    sets.removePhoto("photo-elm")

    assertEquals(listOf("photo-oak"), looks().map(TableLook::id))
  }

  @Test
  fun `a photo added by hand is picked up by the next reading`() {
    // The store is the record and the folder is a view of it, so the two can
    // never disagree for longer than one `bringUpToDate`.
    val sets = mine()
    photos.add(TablePhoto("photo-oak", "Oak", Drawings.webp(64, 48)))

    sets.bringUpToDate()

    assertEquals(listOf("photo-oak"), looks().map(TableLook::id))
  }

  @Test
  fun `a photo table goes out in the exported zip like anything else`() {
    val sets = mine()
    sets.addPhoto("Oak", Drawings.webp(64, 48))

    val ready = sets.export(SetLicense.Mit)

    assertTrue("$ready", ready is ExportResult.Ready)
  }
}
