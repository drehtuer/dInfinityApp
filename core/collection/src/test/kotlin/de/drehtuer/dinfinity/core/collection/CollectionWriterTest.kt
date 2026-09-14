package de.drehtuer.dinfinity.core.collection

import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import de.drehtuer.dinfinity.core.model.TablePin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turning saved rolls into a file
 * (`docs/dice-notation.md`, "Export and import").
 *
 * The tests that matter are about what travels and what does not. A collection
 * somebody opens in a text editor should read like a list of their rolls, not
 * like a dump of a database.
 */
class CollectionWriterTest {
  @Test
  fun `everything exported carries every group and every roll`() {
    val collection =
      CollectionWriter.collect(
        groups = listOf(group("dnd", "D&D"), group("pf", "Pathfinder")),
        rolls = listOf(roll("a", "dnd", "Axe"), roll("b", "pf", "Bow")),
        name = "Everything",
      )

    assertEquals(listOf("d-d", "pathfinder"), collection.groups.map { it.id })
    assertEquals(listOf("Axe", "Bow"), collection.rolls.map { it.name })
  }

  @Test
  fun `one group exported takes its subgroups with it`() {
    // "Export a group (with its subgroups)" — a group naming a character
    // inside a campaign is not much use without the campaign.
    val collection =
      CollectionWriter.collect(
        groups = listOf(group("dnd", "D&D"), group("thorin", "Thorin", parent = "dnd"), group("pf", "Pathfinder")),
        rolls = emptyList(),
        only = "dnd",
        name = "D&D",
      )

    assertEquals(listOf("d-d", "thorin"), collection.groups.map { it.id })
  }

  @Test
  fun `one group exported leaves everything else behind`() {
    val collection =
      CollectionWriter.collect(
        groups = listOf(group("dnd", "D&D"), group("pf", "Pathfinder")),
        rolls = listOf(roll("a", "dnd", "Axe"), roll("b", "pf", "Bow")),
        only = "dnd",
        name = "D&D",
      )

    assertEquals(listOf("Axe"), collection.rolls.map { it.name })
  }

  @Test
  fun `a subgroup exported on its own is lifted to the top level`() {
    // Its parent is not in the file, and a parent id pointing at nothing would
    // be refused by the reader that has to take it back.
    val collection =
      CollectionWriter.collect(
        groups = listOf(group("dnd", "D&D"), group("thorin", "Thorin", parent = "dnd")),
        rolls = emptyList(),
        only = "thorin",
        name = "Thorin",
      )

    assertNull(collection.groups.single().parent)
  }

  @Test
  fun `ids are slugged from names rather than carried over`() {
    // The database's ids are whatever made them — a UUID from the editor, a
    // slug from an earlier import — and a collection should read the same
    // either way.
    val collection =
      CollectionWriter.collect(
        groups = listOf(group("3f8a-uuid-9c2b", "Curse of Strahd")),
        rolls = emptyList(),
        name = "Strahd",
      )

    assertEquals("curse-of-strahd", collection.groups.single().id)
  }

  @Test
  fun `rolls follow their group's new id`() {
    val collection =
      CollectionWriter.collect(
        groups = listOf(group("3f8a-uuid-9c2b", "Thorin")),
        rolls = listOf(roll("a", "3f8a-uuid-9c2b", "Axe")),
        name = "Thorin",
      )

    assertEquals("thorin", collection.rolls.single().group)
  }

  @Test
  fun `two groups that slug to the same id get different ones`() {
    val collection =
      CollectionWriter.collect(
        groups = listOf(group("x", "Thorin!"), group("y", "Thorin?")),
        rolls = emptyList(),
        name = "Both",
      )

    assertEquals(listOf("thorin", "thorin-2"), collection.groups.map { it.id })
  }

  @Test
  fun `what the app made of a roll does not travel`() {
    // No use counts, no timestamps, no colour tags of the app's own palette,
    // and no table pin naming a package the other phone has never heard of.
    val collection =
      CollectionWriter.collect(
        groups = listOf(group("dnd", "D&D")),
        rolls =
          listOf(
            SavedRoll(
              id = "a",
              groupId = "dnd",
              name = "Axe",
              formula = "1d12",
              icon = "🪓",
              colorArgb = 0xFF0000,
              favourite = true,
              tablePin = TablePin("brass", "green-felt"),
              createdAtEpochMs = 1_000,
              lastUsedAtEpochMs = 2_000,
              useCount = 47,
            ),
          ),
        name = "D&D",
      )

    val written = CollectionWriter.write(collection)
    assertTrue("47" !in written, "the use count travelled")
    assertTrue("green-felt" !in written, "the table pin travelled")
    assertTrue("colour" !in written, "the colour tag travelled")
    // What somebody wrote does travel.
    assertTrue("🪓" in written)
    assertTrue("favourite" in written)
  }

  @Test
  fun `a group with no mark writes no mark rather than an empty one`() {
    val written =
      CollectionWriter.write(
        CollectionWriter.collect(listOf(group("dnd", "D&D")), emptyList(), name = "D&D"),
      )

    assertTrue("\"icon\"" !in written)
  }

  @Test
  fun `exporting a group that is not there gives an empty collection rather than everything`() {
    // The one failure mode worth being sure about: a missing filter must not
    // quietly become "export the lot".
    val collection =
      CollectionWriter.collect(
        groups = listOf(group("dnd", "D&D")),
        rolls = listOf(roll("a", "dnd", "Axe")),
        only = "vanished",
        name = "Nothing",
      )

    assertEquals(emptyList(), collection.groups)
    assertEquals(emptyList(), collection.rolls)
  }

  @Test
  fun `exporting the same collection twice gives the same file`() {
    val groups = listOf(group("x", "Thorin"), group("y", "Thorin's spells"))
    val once = CollectionWriter.write(CollectionWriter.collect(groups, emptyList(), name = "T"))
    val twice = CollectionWriter.write(CollectionWriter.collect(groups, emptyList(), name = "T"))

    assertEquals(once, twice)
  }

  @Test
  fun `a name too long for the format is cut rather than refused`() {
    // Export is not the place to refuse what the app itself allowed somebody
    // to type; the file simply carries what fits.
    val collection =
      CollectionWriter.collect(
        groups = listOf(group("a", "x".repeat(CollectionLimits.MAX_NAME + 50))),
        rolls = emptyList(),
        name = "Long",
      )

    assertEquals(
      CollectionLimits.MAX_NAME,
      collection.groups
        .single()
        .name.length,
    )
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
  ) = SavedRoll(id = id, groupId = groupId, name = name, formula = "1d20")
}
