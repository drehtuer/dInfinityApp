package de.drehtuer.dinfinity.core.collection

import de.drehtuer.dinfinity.core.model.SavedRoll
import de.drehtuer.dinfinity.core.model.SavedRollGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What is written can be read back.
 *
 * The reader and the writer are the two halves of one format, and the way a
 * format goes wrong is that each half is correct about a different thing. A
 * file this app writes must be a file this app accepts — including the
 * awkward ones: emoji, an apostrophe, a group inside a group, a formula with
 * a label in brackets.
 */
class CollectionRoundTripTest {
  @Test
  fun `a collection survives being written and read`() {
    val groups =
      listOf(
        SavedRollGroup(id = "uuid-1", name = "Curse of Strahd", icon = "🏰"),
        SavedRollGroup(id = "uuid-2", name = "Thorin", icon = "⚔️", parentId = "uuid-1"),
      )
    val rolls =
      listOf(
        SavedRoll(id = "r1", groupId = "uuid-2", name = "Longsword", formula = "1d20 + 7 [Attack]", icon = "🗡️"),
        SavedRoll(id = "r2", groupId = "uuid-2", name = "Fireball", formula = "8d6 [Fire]", sortOrder = 1),
      )

    val written = CollectionWriter.write(CollectionWriter.collect(groups, rolls, name = "Strahd"))
    val read = CollectionReader.read(written)

    assertTrue(read is CollectionResult.Loaded, "what we wrote did not read: $read")
    assertEquals("Strahd", read.collection.name)
    assertEquals(listOf("Curse of Strahd", "Thorin"), read.collection.groups.map { it.name })
    assertEquals(
      "curse-of-strahd",
      read.collection.groups
        .first { it.name == "Thorin" }
        .parent,
    )
    assertEquals(listOf("Longsword", "Fireball"), read.collection.rolls.map { it.name })
    assertEquals(
      "1d20 + 7 [Attack]",
      read.collection.rolls
        .first()
        .formula,
    )
  }

  @Test
  fun `a name with quotes and newlines in it survives`() {
    // Not a hypothetical: a group called `He said "roll"` is a group somebody
    // will make, and hand-rolled JSON writing is where that goes wrong.
    val groups = listOf(SavedRollGroup(id = "x", name = "He said \"roll\"\tnow"))

    val written = CollectionWriter.write(CollectionWriter.collect(groups, emptyList(), name = "Quotes"))
    val read = CollectionReader.read(written)

    assertTrue(read is CollectionResult.Loaded, "quotes broke the file: $read")
    assertEquals(
      "He said \"roll\"\tnow",
      read.collection.groups
        .single()
        .name,
    )
  }

  @Test
  fun `a group whose name has no letters at all still gets a usable id`() {
    val groups = listOf(SavedRollGroup(id = "x", name = "🎲🎲🎲"))

    val written = CollectionWriter.write(CollectionWriter.collect(groups, emptyList(), name = "Emoji"))
    val read = CollectionReader.read(written)

    assertTrue(read is CollectionResult.Loaded, "an emoji-only name broke the file: $read")
    assertTrue(
      Slugs.valid(
        read.collection.groups
          .single()
          .id,
      ),
    )
    assertEquals(
      "🎲🎲🎲",
      read.collection.groups
        .single()
        .name,
    )
  }

  @Test
  fun `the biggest collection the limits allow still reads`() {
    // 50 groups and 500 rolls is what the format promises to carry, so it is
    // worth knowing that it does rather than assuming.
    val groups = (1..CollectionLimits.MAX_GROUPS).map { SavedRollGroup(id = "g$it", name = "Group $it") }
    val rolls =
      (1..CollectionLimits.MAX_ROLLS).map {
        SavedRoll(id = "r$it", groupId = "g${it % CollectionLimits.MAX_GROUPS + 1}", name = "R$it", formula = "2d6+1")
      }

    val written = CollectionWriter.write(CollectionWriter.collect(groups, rolls, name = "Big"))
    val read = CollectionReader.read(written)

    assertTrue(read is CollectionResult.Loaded, "the biggest allowed collection was refused: $read")
    assertTrue(written.toByteArray().size < CollectionLimits.MAX_BYTES, "it did not fit in the size limit")
  }
}
