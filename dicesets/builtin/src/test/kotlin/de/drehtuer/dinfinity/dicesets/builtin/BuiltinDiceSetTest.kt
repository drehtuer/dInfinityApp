package de.drehtuer.dinfinity.dicesets.builtin

import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.FaceRead
import de.drehtuer.dinfinity.core.model.TableLight
import de.drehtuer.dinfinity.core.model.TableSound
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import de.drehtuer.dinfinity.fixtures.StandardDice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bundled package, held to exactly the rules a downloaded one is held to.
 *
 * If any of this fails, the app has no dice — which is why it is tested here
 * rather than trusted because the file is checked into the repository.
 */
class BuiltinDiceSetTest {
  private val set = BuiltinDiceSet.set

  @Test
  fun `the bundled package passes the validator, with nothing to complain about`() {
    val result = DiceSetValidator.validate(BuiltinDiceSet.files())
    assertTrue("the bundled set was rejected: $result", result is ValidationResult.Valid)
    assertEquals("the bundled set should not even warn", emptyList<Any>(), result.warnings)
  }

  @Test
  fun `it is installed under the id the rest of the app falls back to`() {
    assertEquals(DiceSet.BUILTIN_ID, set.id)
    assertEquals("GPL-2.0-or-later", set.license)
  }

  @Test
  fun `it defines every standard die, which is why nothing ever falls further`() {
    DiceSet.StandardDieIds.forEach { id -> assertNotNull("the bundled set has no $id", set.die(id)) }
  }

  @Test
  fun `each standard die is the solid its name means`() {
    assertEquals(DieShape.Coin, set.die("d2")?.shape)
    assertEquals(DieShape.Tetrahedron, set.die("d4")?.shape)
    assertEquals(DieShape.Cube, set.die("d6")?.shape)
    assertEquals(DieShape.Octahedron, set.die("d8")?.shape)
    assertEquals(DieShape.PentagonalTrapezohedron, set.die("d10")?.shape)
    assertEquals(DieShape.Dodecahedron, set.die("d12")?.shape)
    assertEquals(DieShape.EnneagonalTrapezohedron, set.die("d18")?.shape)
    assertEquals(DieShape.Icosahedron, set.die("d20")?.shape)
  }

  @Test
  fun `every catalogue solid is used by at least one bundled die`() {
    assertEquals(
      "the bundled set does not exercise every shape",
      DieShape.entries.toSet(),
      set.dice.map { it.shape }.toSet(),
    )
  }

  @Test
  fun `a d2 is a coin, not a cube pretending to be one`() {
    assertEquals(listOf(1, 2), set.die("d2")?.values())
  }

  @Test
  fun `a d4 is read from the vertex at the top`() {
    assertEquals(FaceRead.VertexUp, set.die("d4")?.read)
  }

  @Test
  fun `every plain die runs one up to its face count`() {
    listOf("d2", "d4", "d6", "d8", "d10", "d12", "d18", "d20").forEach { id ->
      val die = requireNotNull(set.die(id))
      assertEquals(id, (1..die.shape.faceCount).toList(), die.values())
    }
  }

  @Test
  fun `a d10 is printed the way a real one is moulded`() {
    assertEquals(
      listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
      set.die("d10")?.faces?.map { it.label },
    )
  }

  @Test
  fun `a tens d10 scores and prints in tens`() {
    val tens = requireNotNull(set.die("d10-tens"))
    assertEquals((0..9).map { it * 10 }, tens.values())
    assertEquals("00", tens.faces.first().label)
    assertEquals("90", tens.faces.last().label)
  }

  @Test
  fun `a fudge die is two minuses, two blanks and two pluses`() {
    val fudge = requireNotNull(set.die("df"))
    assertEquals(listOf(-1, -1, 0, 0, 1, 1), fudge.values())
    assertEquals(listOf("−", "−", "0", "0", "+", "+"), fudge.faces.map { it.label })
  }

  @Test
  fun `every die carries the package's defaults unless it says otherwise`() {
    set.dice.forEach { die ->
      assertEquals(die.id, 0xFFE8DCC0.toInt(), die.material.colorArgb)
      assertEquals(die.id, 16.0, die.material.sizeMm, 0.0)
      assertEquals(die.id, 1.2, die.material.density, 0.0)
    }
  }

  @Test
  fun `no bundled die has a texture, so the no-artwork path is the one that ships`() {
    set.dice.forEach { die -> assertNull(die.id, die.texturePath) }
  }

  @Test
  fun `it ships the five tables docs tables names`() {
    assertEquals(listOf("felt-green", "felt-black", "oak", "dark-glass", "plain"), set.tables.map { it.id })
  }

  @Test
  fun `each table sounds like what it is made of`() {
    assertEquals(TableSound.Felt, set.table("felt-green")?.sound)
    assertEquals(TableSound.Wood, set.table("oak")?.sound)
    assertEquals(TableSound.Glass, set.table("dark-glass")?.sound)
    assertEquals(TableLight.Dim, set.table("plain")?.light)
  }

  @Test
  fun `every table's physics is inside the range the tray allows`() {
    set.tables.forEach { look ->
      assertEquals("${look.id} was clamped, so it was outside its range", look, look.clampedToLimits())
    }
  }

  @Test
  fun `the set is read once and kept`() {
    assertSame(BuiltinDiceSet.set, BuiltinDiceSet.set)
  }

  @Test
  fun `the shared fixture says the same thing as the package that ships`() {
    // StandardDice stands in for this set in every other module's tests. If the
    // two drift apart, those tests stop being about the dice the app has.
    val fixture = StandardDice.set()
    assertEquals(fixture.dice.map { it.id }, set.dice.map { it.id })
    fixture.dice.forEach { expected ->
      val shipped = requireNotNull(set.die(expected.id))
      assertEquals(expected.id, expected.values(), shipped.values())
      assertEquals(expected.id, expected.shape, shipped.shape)
      assertEquals(expected.id, expected.read, shipped.read)
    }
    assertEquals(fixture.tables.map { it.id }, set.tables.map { it.id })
  }

  @Test
  fun `a file the package does not have reads as nothing`() {
    assertNull(BuiltinDiceSet.files().read("textures/d20.png"))
    assertNull(BuiltinDiceSet.files().size("textures/d20.png"))
  }

  @Test
  fun `reading does not hand out the package's own bytes`() {
    val files = BuiltinDiceSet.files()
    requireNotNull(files.read(DiceSetValidator.DICE_SET_FILE))[0] = 0
    assertEquals('#'.code.toByte(), requireNotNull(files.read(DiceSetValidator.DICE_SET_FILE))[0])
  }
}
