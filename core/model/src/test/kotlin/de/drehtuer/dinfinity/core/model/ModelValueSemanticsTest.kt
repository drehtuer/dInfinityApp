package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Every type in this module is a value: two of them built from the same fields
 * are the same thing, `copy` is how one is changed, and destructuring order is
 * part of the public shape.
 *
 * It is worth a test of its own because the whole module is data that crosses
 * process boundaries — Room rows, DataStore, Compose state — and all of that
 * leans on equality. Turning one of these into an ordinary class, or reordering
 * a constructor, would break callers silently rather than loudly.
 */
class ModelValueSemanticsTest {
  private val d6 = Die.standard("d6", DieShape.Cube)

  @Test
  fun `a face is its index, value and label, in that order`() {
    val (index, value, label) = Face(index = 2, value = 5, label = "5")
    assertEquals(2, index)
    assertEquals(5, value)
    assertEquals("5", label)
    assertEquals(Face(2, 5, "5"), Face(2, 5, "5"))
    assertEquals(Face(2, 5, "5").hashCode(), Face(2, 5, "5").hashCode())
    assertNotEquals(Face(2, 5, "5"), Face(2, 5, "5").copy(value = 6))
  }

  @Test
  fun `a material is equal by value and changed by copy`() {
    val bone = DieMaterial()
    assertEquals(bone, DieMaterial())
    assertEquals(bone.hashCode(), DieMaterial().hashCode())
    assertNotEquals(bone, bone.copy(sizeMm = 20.0))
    assertEquals(20.0, bone.copy(sizeMm = 20.0).sizeMm)
    assertEquals("DieMaterial", bone.toString().substringBefore("("))
  }

  @Test
  fun `a die is equal by value and changed by copy`() {
    assertEquals(d6, Die.standard("d6", DieShape.Cube))
    assertEquals(d6.hashCode(), Die.standard("d6", DieShape.Cube).hashCode())
    assertNotEquals(d6, d6.copy(id = "d6-alt"))
    assertEquals("textures/d6.png", d6.copy(texturePath = "textures/d6.png").texturePath)
    assertEquals("Die", d6.toString().substringBefore("("))
  }

  @Test
  fun `a set is equal by value and changed by copy`() {
    val set = DiceSet(id = "brass", name = "Brass", version = "1.0.0", dice = listOf(d6))
    assertEquals(set, set.copy())
    assertEquals(set.hashCode(), set.copy().hashCode())
    assertNotEquals(set, set.copy(version = "1.0.1"))
    assertEquals("DiceSet", set.toString().substringBefore("("))
  }

  @Test
  fun `a table look is equal by value and changed by copy`() {
    val felt = TableLook(id = "felt", name = "Felt")
    assertEquals(felt, TableLook(id = "felt", name = "Felt"))
    assertEquals(felt.hashCode(), TableLook(id = "felt", name = "Felt").hashCode())
    assertNotEquals(felt, felt.copy(sound = TableSound.Wood))
    assertEquals("TableLook", felt.toString().substringBefore("("))
    val (short, long) = TableLook.Tiling(3, 6)
    assertEquals(3, short)
    assertEquals(6, long)
    assertEquals(TableLook.Tiling(3, 6).hashCode(), TableLook.Tiling(3, 6).hashCode())
    assertNotEquals(TableLook.Tiling(3, 6), TableLook.Tiling(6, 3))
    assertEquals("Tiling", TableLook.Tiling().toString().substringBefore("("))
  }

  @Test
  fun `a plan and its parts are equal by value and changed by copy`() {
    val instance = DieInstance(index = 0, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = d6)
    val group = PlannedGroup(id = 0, notation = "1d6", dice = listOf(instance))
    val plan = RollPlan(formula = "1d6", groups = listOf(group))
    assertEquals(plan, plan.copy())
    assertEquals(plan.hashCode(), plan.copy().hashCode())
    assertNotEquals(plan, plan.copy(label = "Fire"))
    assertNotEquals(group, group.copy(explodes = true))
    assertNotEquals(instance, instance.copy(role = DieRole.PercentileTens))
    assertEquals("RollPlan", plan.toString().substringBefore("("))
    assertEquals("PlannedGroup", group.toString().substringBefore("("))
    assertEquals("DieInstance", instance.toString().substringBefore("("))
  }

  @Test
  fun `a result and its parts are equal by value and changed by copy`() {
    val die = RolledDie(instanceIndex = 0, dieId = "d6", value = 4)
    val group = RolledGroup(id = 0, notation = "1d6", setId = "builtin", requestedSetId = "builtin", dice = listOf(die))
    val result = RollResult(formula = "1d6", total = 4, groups = listOf(group))
    assertEquals(result, result.copy())
    assertEquals(result.hashCode(), result.copy().hashCode())
    assertNotEquals(result, result.copy(total = 5))
    assertNotEquals(group, group.copy(subtotal = 4))
    assertNotEquals(die, die.copy(value = 5))
    assertEquals("RollResult", result.toString().substringBefore("("))
    assertEquals("RolledGroup", group.toString().substringBefore("("))
    assertEquals("RolledDie", die.toString().substringBefore("("))
  }

  @Test
  fun `a saved roll and its group are equal by value and changed by copy`() {
    val roll = SavedRoll(id = "fireball", groupId = "thorin", name = "Fireball", formula = "8d6")
    val group = SavedRollGroup(id = "thorin", name = "Thorin")
    val pin = TablePin(setId = "builtin", tableId = "oak")
    assertEquals(roll, roll.copy())
    assertEquals(roll.hashCode(), roll.copy().hashCode())
    assertNotEquals(roll, roll.copy(sortOrder = 1))
    assertNotEquals(group, group.copy(sortOrder = 1))
    assertEquals(pin, TablePin("builtin", "oak"))
    assertEquals(pin.hashCode(), TablePin("builtin", "oak").hashCode())
    assertNotEquals(pin, pin.copy(tableId = "felt"))
    assertEquals("SavedRoll", roll.toString().substringBefore("("))
    assertEquals("SavedRollGroup", group.toString().substringBefore("("))
    assertEquals("TablePin", pin.toString().substringBefore("("))
  }
}
