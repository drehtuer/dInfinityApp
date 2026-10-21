package de.drehtuer.dinfinity.fixtures

import de.drehtuer.dinfinity.core.model.DiceSet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The fixture every other module's tests build their dice from.
 *
 * `BuiltinDiceSetTest` holds its *values and shapes* to the shipped package;
 * what it does not compare is what is printed on the faces and how [StandardDice.set]
 * varies the set, and those are what the tests here pin.
 */
class StandardDiceTest {
  @Test
  fun `it holds exactly the standard dice, in the picker's order`() {
    assertEquals(DiceSet.StandardDieIds, StandardDice.all.map { it.id })
  }

  @Test
  fun `every die's faces are indexed in the order they are listed`() {
    // A face whose index disagrees with its position reads as the wrong face
    // the moment a test looks one up by what the simulation reports.
    StandardDice.all.forEach { die ->
      assertEquals(die.faces.indices.toList(), die.faces.map { it.index }, die.id)
    }
  }

  @Test
  fun `a plain die prints its score`() {
    listOf(StandardDice.d6, StandardDice.d20).forEach { die ->
      die.faces.forEach { face -> assertEquals(face.value.toString(), face.label, die.id) }
    }
  }

  @Test
  fun `the d10 scores ten on the face printed nought`() {
    val ten = StandardDice.d10.faces.single { it.label == "0" }

    assertEquals(10, ten.value)
    assertEquals(
      (1..9).map(Int::toString).toSet() + "0",
      StandardDice.d10.faces
        .map { it.label }
        .toSet(),
    )
  }

  @Test
  fun `the tens die prints two digits, so its blank face reads 00`() {
    assertEquals(
      "00",
      StandardDice.d10Tens.faces
        .single { it.value == 0 }
        .label,
    )
    assertEquals(
      "90",
      StandardDice.d10Tens.faces
        .single { it.value == 90 }
        .label,
    )
  }

  @Test
  fun `the fudge die prints a minus, a blank or a plus`() {
    val printed = StandardDice.fudge.faces.associate { it.value to it.label }

    assertEquals(mapOf(-1 to "−", 0 to "0", 1 to "+"), printed)
  }

  @Test
  fun `the bundled set carries the tables and another id does not`() {
    // Tables come from the bundled package only (`docs/tables.md`); a set
    // standing in for a download that suddenly had tables would test a case
    // that cannot happen.
    assertEquals(StandardDice.tables, StandardDice.set().tables)
    assertEquals(emptyList(), StandardDice.set(id = "brass").tables)
  }

  @Test
  fun `a set without a die leaves the others where they were`() {
    val set = StandardDice.set(id = "brass", without = setOf("d12", "df"), name = "Brass")

    assertEquals(DiceSet.StandardDieIds - setOf("d12", "df"), set.dice.map { it.id })
    assertEquals("brass", set.id)
    assertEquals("Brass", set.name)
  }
}
