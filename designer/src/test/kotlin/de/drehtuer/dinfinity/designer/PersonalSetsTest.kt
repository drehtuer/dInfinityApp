package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieMaterial
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * More than one personal set: the model, and the step to it from a phone that
 * had only "My dice" (`docs/architecture.md`, decision 79).
 *
 * Nothing here needs a device. The painter is handed in, and what is under
 * test is what the records and the folders say.
 */
class PersonalSetsTest {
  @get:Rule
  val temporary: TemporaryFolder = TemporaryFolder()

  private val cube = Die.standard(id = "d6", shape = DieShape.Cube)
  private val d20 = Die.standard(id = "d20", shape = DieShape.Icosahedron)

  private val files: File by lazy { temporary.newFolder("files") }
  private val root: File get() = File(files, "dicesets")
  private val records: File get() = File(files, PersonalSets.DIRECTORY)
  private val drafts: DraftStore get() = DraftStore(File(files, DraftStore.DIRECTORY))
  private val physical: PhysicalStore get() = PhysicalStore(File(files, PhysicalStore.FILE_NAME))

  /** "My dice" on the records a phone has had since there was one personal set. */
  private fun mine(): MineSets {
    root.mkdirs()
    return MineSets(
      drafts = drafts,
      root = root,
      painter = Drawings.headers(),
      dice = { listOf(cube, d20) },
      photos = PhotoStore(File(files, PhotoStore.DIRECTORY)),
      physical = physical,
    )
  }

  private fun personal(
    mine: MineSets = mine(),
    at: File = records,
  ) = PersonalSets(mine, at)

  // --- names and ids ------------------------------------------------------

  @Test
  fun `a name comes to an id by the slug rule`() {
    assertEquals("brass-bone", PersonalSetId.of("Brass & Bone"))
    assertEquals("cafe", PersonalSetId.of("Café"))
    assertEquals("arger", PersonalSetId.of("  Ärger "))
    assertEquals("x", PersonalSetId.of("--x--"))
    assertEquals("", PersonalSetId.of("!!!"))
  }

  @Test
  fun `a long name comes to an id no longer than a set id, ending in a letter or digit`() {
    val id = PersonalSetId.of("abcdefghi " + "abcdefghi ".repeat(4))

    assertTrue("'$id' is too long", id.length <= 40)
    assertTrue("'$id' is not a set id", DiceSet.IdPattern.matches(id))
  }

  @Test
  fun `what is wrong with a name is said, and a good name has nothing wrong with it`() {
    assertEquals(NewSet.Unnamed, PersonalSetId.problemWith("   "))
    assertEquals(NewSet.NameTooLong, PersonalSetId.problemWith("x".repeat(PersonalSetId.NAME_LIMIT + 1)))
    assertEquals(NewSet.BadId("me"), PersonalSetId.problemWith("Me"))
    assertEquals(NewSet.BadId(""), PersonalSetId.problemWith("???"))
    assertNull(PersonalSetId.problemWith("Props"))
    assertNull(PersonalSetId.problemWith("x".repeat(PersonalSetId.NAME_LIMIT)))
  }

  // --- the step from one personal set to several --------------------------

  @Test
  fun `a phone that had only My dice keeps it, its drafts and its weight, byte for byte`() {
    // The migration is that there is none: nothing moves, so nothing that was
    // on the disk before the update can be lost by it.
    drafts.save(Drawings.drawn(cube, 0))
    drafts.save(Drawings.drawn(d20, 3))
    physical.set(DieMaterial(density = 2.4, translucency = 0.2, sizeMm = 18.0))
    val before = mine()
    before.bringUpToDate()
    val draftsBefore = snapshot(File(files, DraftStore.DIRECTORY))
    val physicalBefore = File(files, PhysicalStore.FILE_NAME).readBytes()
    val packageBefore = snapshot(File(root, DiceSet.PERSONAL_ID))

    val sets = personal()
    sets.bringUpToDate()
    assertTrue(sets.create("Props") is NewSet.Made)
    sets.find("props")?.keep(Drawings.drawn(cube, 1))
    // The next launch, reading everything afresh.
    personal().bringUpToDate()

    assertEquals(draftsBefore.keys, snapshot(File(files, DraftStore.DIRECTORY)).keys)
    draftsBefore.forEach { (path, bytes) ->
      assertArrayEquals(path, bytes, File(File(files, DraftStore.DIRECTORY), path).readBytes())
    }
    assertArrayEquals(physicalBefore, File(files, PhysicalStore.FILE_NAME).readBytes())
    assertEquals(packageBefore.keys, snapshot(File(root, DiceSet.PERSONAL_ID)).keys)
    packageBefore.forEach { (path, bytes) ->
      assertArrayEquals(path, bytes, File(File(root, DiceSet.PERSONAL_ID), path).readBytes())
    }
    val read = installed(DiceSet.PERSONAL_ID)
    assertEquals(listOf("d20", "d6"), read.dice.map(Die::id))
    assertEquals(
      2.4,
      read.dice
        .first()
        .material.density,
      1e-12,
    )
  }

  @Test
  fun `a phone that never names a set never gets the folder for them`() {
    drafts.save(Drawings.drawn(cube, 0))
    val sets = personal()

    sets.bringUpToDate()

    assertEquals(listOf(DiceSet.PERSONAL_ID), sets.all().map(MineSets::id))
    assertFalse(records.exists())
  }

  // --- making one ----------------------------------------------------------

  @Test
  fun `a new set is on the disk at once, empty, valid and at the average`() {
    val sets = personal()

    val made = sets.create("  Brass & Bone ")

    assertEquals(NewSet.Made("brass-bone", "Brass & Bone"), made)
    val set = installed("brass-bone")
    assertEquals("Brass & Bone", set.name)
    assertEquals(MinePackage.NAMED_DESCRIPTION, set.description)
    assertTrue(set.dice.isEmpty())
    assertEquals(DieMaterial(), sets.find("brass-bone")?.physical())
    assertEquals(listOf(DiceSet.PERSONAL_ID, "brass-bone"), sets.all().map(MineSets::id))
    assertEquals(setOf(DiceSet.PERSONAL_ID, "brass-bone"), sets.ids())
    assertEquals("Brass & Bone\n", File(records, "brass-bone/${PersonalSets.NAME_FILE}").readText())
  }

  @Test
  fun `named sets are listed after My dice, by name`() {
    val sets = personal()
    sets.create("Zebra")
    sets.create("apple")

    assertEquals(listOf("My dice", "apple", "Zebra"), sets.all().map(MineSets::name))
    assertEquals(listOf("My dice", "apple", "Zebra"), personal().all().map(MineSets::name))
  }

  @Test
  fun `an id somebody already has is refused, and nothing is written`() {
    File(root, "brass").mkdirs()
    val sets = personal()
    sets.create("Props")

    assertEquals(NewSet.Taken("mine"), sets.create("Mine"))
    assertEquals(NewSet.Taken("builtin"), sets.create("Builtin"))
    assertEquals(NewSet.Taken("brass"), sets.create("Brass"))
    assertEquals(NewSet.Taken("props"), sets.create("PROPS!"))
    assertEquals(listOf(DiceSet.PERSONAL_ID, "props"), sets.all().map(MineSets::id))
  }

  @Test
  fun `a name that is no good is refused before anything is written`() {
    val sets = personal()

    assertEquals(NewSet.Unnamed, sets.create(""))
    assertEquals(NewSet.BadId("me"), sets.create("Me"))
    assertEquals(NewSet.NameTooLong, sets.create("y".repeat(PersonalSetId.NAME_LIMIT + 1)))
    assertFalse(records.exists())
  }

  @Test
  fun `a record the disk will not take is refused, and nothing is left`() {
    files.mkdirs()
    records.writeText("not a folder")
    val sets = personal()

    assertEquals(NewSet.NotWritten, sets.create("Props"))
    assertFalse(File(root, "props").exists())
  }

  @Test
  fun `a package the disk will not take takes the record back out`() {
    val packages = File(files, "packages-are-a-file")
    files.mkdirs()
    packages.writeText("not a folder")
    val sets = PersonalSets(blocked(packages), records)

    assertEquals(NewSet.NotWritten, sets.create("Props"))
    assertFalse("the record was left behind", File(records, "props").exists())
  }

  // --- each set its own records -------------------------------------------

  @Test
  fun `drafts are kept by set as well as by die`() {
    drafts.save(Drawings.drawn(d20, 0))
    val sets = personal()
    sets.create("Props")
    val props = sets.find("props") ?: error("the set went missing")

    props.keep(Drawings.drawn(cube, 0))
    sets.bringUpToDate()

    assertEquals(listOf("d6"), installed("props").dice.map(Die::id))
    assertEquals(listOf("d20"), installed(DiceSet.PERSONAL_ID).dice.map(Die::id))
    assertTrue(props.drawn())
    assertTrue(File(records, "props/${DraftStore.DIRECTORY}").isDirectory)
  }

  @Test
  fun `each set has its own weight`() {
    drafts.save(Drawings.drawn(cube, 0))
    val sets = personal()
    sets.create("Props")
    val props = sets.find("props") ?: error("the set went missing")
    props.keep(Drawings.drawn(cube, 0))

    props.setPhysical(DieMaterial(density = 3.0))
    sets.bringUpToDate()

    assertEquals(
      3.0,
      installed("props")
        .dice
        .single()
        .material.density,
      1e-12,
    )
    assertEquals(
      DieMaterial().density,
      installed(DiceSet.PERSONAL_ID)
        .dice
        .single()
        .material.density,
      1e-12,
    )
    assertTrue(File(records, "props/${PersonalSets.PHYSICAL_FILE}").isFile)
  }

  @Test
  fun `each set has its own export, under its own file name`() {
    val sets = personal()
    sets.create("Props")
    val props = sets.find("props") ?: error("the set went missing")
    assertEquals(ExportResult.Empty, props.export(SetLicense.Mit))

    props.keep(Drawings.drawn(cube, 0))
    val ready = props.export(SetLicense.Mit) as ExportResult.Ready

    assertEquals("props.zip", ready.file.name)
    assertEquals("MIT", installed("props").license)
  }

  @Test
  fun `a named set with its drawings gone stays on the list, empty`() {
    // It was made on purpose; a phone with nothing drawn has no "My dice",
    // but a set somebody named is theirs until they remove it.
    val sets = personal()
    sets.create("Props")
    val props = sets.find("props") ?: error("the set went missing")
    props.keep(Drawings.drawn(cube, 0))
    sets.bringUpToDate()

    props.keep(Draft(die = cube))
    sets.bringUpToDate()

    assertTrue(installed("props").dice.isEmpty())
    assertFalse(props.drawn())
  }

  // --- reading and removing ------------------------------------------------

  @Test
  fun `only a folder with a name in it is a set, and nobody can name one mine`() {
    File(records, "half-made").mkdirs()
    File(records, ".props.writing").mkdirs()
    File(records, ".props.writing/${PersonalSets.NAME_FILE}").writeText("Props")
    File(records, "mine").mkdirs()
    File(records, "mine/${PersonalSets.NAME_FILE}").writeText("Not mine")
    File(records, "blank").mkdirs()
    File(records, "blank/${PersonalSets.NAME_FILE}").writeText("\n")
    File(records, "two-lines").mkdirs()
    File(records, "two-lines/${PersonalSets.NAME_FILE}").writeText("  Two lines \nsecond")
    File(records, "loose.txt").writeText("not a folder")

    val sets = personal()

    assertEquals(listOf(DiceSet.PERSONAL_ID, "two-lines"), sets.all().map(MineSets::id))
    assertEquals("Two lines", sets.find("two-lines")?.name)
    assertNull(sets.find("half-made"))
  }

  @Test
  fun `removing a named set takes its package and its records, and nothing else`() {
    drafts.save(Drawings.drawn(cube, 0))
    val sets = personal()
    sets.create("Props")
    sets.bringUpToDate()

    assertTrue(sets.forget("props"))

    assertFalse(File(root, "props").exists())
    assertFalse(File(records, "props").exists())
    assertEquals(listOf(DiceSet.PERSONAL_ID), sets.all().map(MineSets::id))
    assertTrue(File(root, DiceSet.PERSONAL_ID).isDirectory)
    assertTrue(drafts.known().contains("d6"))
  }

  @Test
  fun `My dice and a set nobody has are not forgotten`() {
    drafts.save(Drawings.drawn(cube, 0))
    val sets = personal()
    sets.bringUpToDate()

    assertFalse(sets.forget(DiceSet.PERSONAL_ID))
    assertFalse(sets.forget("props"))
    assertTrue(File(root, DiceSet.PERSONAL_ID).isDirectory)
  }

  @Test
  fun `My dice keeps the file name and description it always had`() {
    assertEquals(MinePackage.FILE_NAME, MinePackage.fileNameOf(MinePackage.ID))
    assertEquals("props.zip", MinePackage.fileNameOf("props"))
    assertEquals(MinePackage.DESCRIPTION, MinePackage.descriptionOf(MinePackage.ID))
    assertEquals(MinePackage.NAMED_DESCRIPTION, MinePackage.descriptionOf("props"))
  }

  /** "My dice", but with its packages going somewhere the disk refuses. */
  private fun blocked(packages: File): MineSets =
    MineSets(
      drafts = drafts,
      root = packages,
      painter = Drawings.headers(),
      dice = { listOf(cube) },
      photos = PhotoStore(File(files, PhotoStore.DIRECTORY)),
      physical = physical,
    )

  private fun installed(id: String): DiceSet =
    (DiceSetValidator.validate(PackageFiles.of(File(root, id))) as ValidationResult.Valid).set

  /** Every file under [folder], by its path inside it. */
  private fun snapshot(folder: File): Map<String, ByteArray> =
    folder
      .walkTopDown()
      .filter(File::isFile)
      .associate { it.relativeTo(folder).path to it.readBytes() }
}
