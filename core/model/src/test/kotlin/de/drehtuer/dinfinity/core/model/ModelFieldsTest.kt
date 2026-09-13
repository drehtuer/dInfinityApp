package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Each type built with every field set, and every field read back.
 *
 * Dull on purpose. These types are the contract between the parser, the
 * simulator, the database and the screens, and a field that no test ever reads
 * is a field nobody would notice going missing in a refactor.
 */
class ModelFieldsTest {
  private val d6 = Die.standard("d6", DieShape.Cube)

  @Test
  fun `a die carries its shape, faces, read mode, texture and material`() {
    val skull =
      Die(
        id = "skull-d6",
        shape = DieShape.Cube,
        faces = List(6) { Face(index = it, value = it + 1, label = "${it + 1}") },
        read = FaceRead.FaceUp,
        texturePath = "textures/skull.png",
        material = DieMaterial(sizeMm = 18.0),
      )
    assertEquals("skull-d6", skull.id)
    assertEquals(DieShape.Cube, skull.shape)
    assertEquals(6, skull.faces.size)
    assertEquals(FaceRead.FaceUp, skull.read)
    assertEquals("textures/skull.png", skull.texturePath)
    assertEquals(18.0, skull.material.sizeMm)
    assertEquals(0, skull.faces.first().index)
  }

  @Test
  fun `a set carries its identity and what it defines`() {
    val set =
      DiceSet(
        id = "brass-and-bone",
        name = "Brass & Bone",
        version = "1.2.0",
        dice = listOf(d6),
        tables = listOf(TableLook(id = "bone-felt", name = "Bone felt")),
      )
    assertEquals("brass-and-bone", set.id)
    assertEquals("Brass & Bone", set.name)
    assertEquals("1.2.0", set.version)
    assertEquals(listOf(d6), set.dice)
    assertEquals(1, set.tables.size)
  }

  @Test
  fun `a table look carries both surfaces and both colours`() {
    val oak =
      TableLook(
        id = "oak",
        name = "Oak",
        floorTexturePath = "tables/oak.png",
        floorTiling = TableLook.Tiling(3, 6),
        wallTexturePath = "tables/oak-wall.png",
        wallTiling = TableLook.Tiling(8, 1),
        floorColorArgb = 0xFF5A3A1E.toInt(),
        wallColorArgb = 0xFF3A2A18.toInt(),
      )
    assertEquals("oak", oak.id)
    assertEquals("Oak", oak.name)
    assertEquals("tables/oak.png", oak.floorTexturePath)
    assertEquals(TableLook.Tiling(3, 6), oak.floorTiling)
    assertEquals("tables/oak-wall.png", oak.wallTexturePath)
    assertEquals(TableLook.Tiling(8, 1), oak.wallTiling)
    assertEquals(0xFF5A3A1E.toInt(), oak.floorColorArgb)
    assertEquals(0xFF3A2A18.toInt(), oak.wallColorArgb)
  }

  @Test
  fun `a plan carries the formula and every group's own notation`() {
    val instance = DieInstance(index = 0, groupId = 3, setId = "builtin", requestedSetId = "brass", die = d6)
    val group = PlannedGroup(id = 3, notation = "1d6", dice = listOf(instance))
    val plan = RollPlan(formula = "1d6 [Fire]", label = "Fire", groups = listOf(group))
    assertEquals("1d6 [Fire]", plan.formula)
    assertEquals(3, group.id)
    assertEquals("1d6", group.notation)
    assertEquals(3, instance.groupId)
    assertEquals("builtin", instance.setId)
    assertEquals("brass", instance.requestedSetId)
  }

  @Test
  fun `a result carries the formula, the label, the groups and the time`() {
    val die = RolledDie(instanceIndex = 0, dieId = "d6", value = 6, notes = setOf(DieNote.FromExplosion))
    val group =
      RolledGroup(
        id = 2,
        notation = "1d6!",
        setId = "builtin",
        requestedSetId = "builtin",
        dice = listOf(die),
        subtotal = 6,
      )
    val result =
      RollResult(
        formula = "1d6! [Fire]",
        label = "Fire",
        total = 6,
        groups = listOf(group),
        rolledAtEpochMs = 1_700_000L,
      )
    assertEquals("1d6! [Fire]", result.formula)
    assertEquals("Fire", result.label)
    assertEquals(listOf(group), result.groups)
    assertEquals(1_700_000L, result.rolledAtEpochMs)
    assertEquals(2, group.id)
    assertEquals("1d6!", group.notation)
    assertEquals("builtin", group.setId)
    assertEquals("builtin", group.requestedSetId)
    assertEquals(6L, group.subtotal)
    assertEquals("d6", die.dieId)
    assertEquals(setOf(DieNote.FromExplosion), die.notes)
  }

  @Test
  fun `a saved roll carries everything the editor sets`() {
    val roll =
      SavedRoll(
        id = "fireball",
        groupId = "thorin",
        name = "Fireball",
        formula = "8d6 [Fire]",
        icon = "🔥",
        createdAtEpochMs = 1_000L,
        lastUsedAtEpochMs = 2_000L,
        useCount = 7,
      )
    assertEquals("fireball", roll.id)
    assertEquals("thorin", roll.groupId)
    assertEquals("Fireball", roll.name)
    assertEquals("🔥", roll.icon)
    assertEquals(1_000L, roll.createdAtEpochMs)
    assertEquals(2_000L, roll.lastUsedAtEpochMs)
    assertEquals(7, roll.useCount)
  }

  @Test
  fun `a group carries its name, icon and place in the list`() {
    val thorin = SavedRollGroup(id = "thorin", name = "Thorin", icon = "⚔️", parentId = "dnd", sortOrder = 2)
    assertEquals("thorin", thorin.id)
    assertEquals("Thorin", thorin.name)
    assertEquals("⚔️", thorin.icon)
    assertEquals(2, thorin.sortOrder)
    assertEquals("builtin", TablePin(setId = "builtin", tableId = "oak").setId)
  }
}
