package de.drehtuer.dinfinity.feature.saved

import de.drehtuer.dinfinity.core.collection.CollectionReader
import de.drehtuer.dinfinity.core.collection.CollectionResult
import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What goes into an exported file and what it is called
 * (`docs/dice-notation.md`, "Export and import").
 *
 * The half of exporting that has judgements in it. Plain JVM tests: nothing
 * here touches Android, which is the point of keeping it apart from the
 * sharing.
 */
class CollectionExportTest {
  @Test
  fun `a file is named after what is in it`() {
    assertEquals("curse-of-strahd.dinfinity.json", CollectionExport.fileNameOf("Curse of Strahd"))
  }

  @Test
  fun `a name that would make a poor path is slugged rather than used as typed`() {
    // A group called "D&D / 5e?" is a perfectly good group and a poor file
    // name on most of the places a share sheet can send it.
    assertEquals("d-d-5e.dinfinity.json", CollectionExport.fileNameOf("D&D / 5e?"))
  }

  @Test
  fun `a name with nothing sluggable in it still gives a file name`() {
    assertEquals("collection.dinfinity.json", CollectionExport.fileNameOf("🎲🎲🎲"))
  }

  @Test
  fun `exporting one group takes that group and its subgroups`() {
    val file =
      CollectionExport.of(
        groups = listOf(group("dnd", "D&D"), group("thorin", "Thorin", "dnd"), group("pf", "Pathfinder")),
        rolls = listOf(roll("a", "thorin", "Axe"), roll("b", "pf", "Bow")),
        only = "dnd",
        called = "D&D",
      )

    val read = read(file)
    assertEquals(listOf("D&D", "Thorin"), read.collection.groups.map { it.name })
    assertEquals(listOf("Axe"), read.collection.rolls.map { it.name })
  }

  @Test
  fun `exporting everything takes everything`() {
    val file =
      CollectionExport.of(
        groups = listOf(group("dnd", "D&D"), group("pf", "Pathfinder")),
        rolls = listOf(roll("a", "dnd", "Axe"), roll("b", "pf", "Bow")),
        only = null,
        called = "Everything",
      )

    val read = read(file)
    assertEquals(listOf("Axe", "Bow"), read.collection.rolls.map { it.name })
  }

  @Test
  fun `what is exported is a file this app would accept back`() {
    // The one thing worth being certain of about an export: it is not a
    // format only the writer understands.
    val file =
      CollectionExport.of(
        groups = listOf(group("uuid-1", "Curse of Strahd"), group("uuid-2", "Thorin", "uuid-1")),
        rolls = listOf(roll("r", "uuid-2", "Longsword", formula = "1d20 + 7 [Attack]")),
        called = "Strahd",
      )

    assertTrue(CollectionReader.read(file.json) is CollectionResult.Loaded)
  }

  private fun read(file: CollectionFile): CollectionResult.Loaded {
    val result = CollectionReader.read(file.json)
    assertTrue("what was exported did not read back: $result", result is CollectionResult.Loaded)
    return result as CollectionResult.Loaded
  }

  private fun group(
    id: String,
    name: String,
    parent: String? = null,
  ) = SavedRollGroup(id = id, name = name, parentId = parent)

  private fun roll(
    id: String,
    groupId: String,
    name: String,
    formula: String = "1d20",
  ) = SavedRoll(id = id, groupId = groupId, name = name, formula = formula)
}
