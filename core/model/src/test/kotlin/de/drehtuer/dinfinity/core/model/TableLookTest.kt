package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TableLookTest {
  @Test
  fun `a look with nothing set is a plain untextured tray`() {
    val look = TableLook(id = "plain", name = "Plain")
    assertNull(look.floorTexturePath)
    assertNull(look.wallTexturePath)
    assertEquals(TableLook.Tiling(1, 1), look.floorTiling)
    assertEquals(TableSound.Felt, look.sound)
    assertEquals(TableLight.Neutral, look.light)
  }

  @Test
  fun `a tiling repeats a small tile across both sides`() {
    val felt = TableLook(id = "felt", name = "Felt", floorTiling = TableLook.Tiling(3, 6))
    assertEquals(3, felt.floorTiling.acrossShortSide)
    assertEquals(6, felt.floorTiling.acrossLongSide)
  }

  @Test
  fun `a table may be a bit slippery but never frictionless`() {
    assertEquals(0.2, TableLook(id = "ice", name = "Ice", friction = 0.0).clampedToLimits().friction)
  }

  @Test
  fun `a trampoline of a table is clamped to the bounciest tray that settles`() {
    val bouncy = TableLook(id = "b", name = "B", restitution = 1.0).clampedToLimits()
    assertEquals(0.6, bouncy.restitution)
  }

  @Test
  fun `roughness and metallic are plain unit ranges`() {
    val odd = TableLook(id = "o", name = "O", roughness = 9.0, metallic = -2.0).clampedToLimits()
    assertEquals(1.0, odd.roughness)
    assertEquals(0.0, odd.metallic)
  }

  @Test
  fun `a non-finite physics value falls back rather than reaching the solver`() {
    val poisoned =
      TableLook(id = "p", name = "P", friction = Double.NaN, restitution = Double.NaN).clampedToLimits()
    assertEquals(0.6, poisoned.friction)
    assertEquals(0.2, poisoned.restitution)
  }

  @Test
  fun `a look already inside its limits is left alone`() {
    val fine = TableLook(id = "f", name = "F", friction = 0.5, restitution = 0.3)
    assertEquals(fine, fine.clampedToLimits())
  }

  @Test
  fun `every built-in sound preset resolves from its file spelling`() {
    assertEquals(
      listOf("felt", "wood", "glass", "stone", "plastic"),
      TableSound.entries.map(TableSound::id),
    )
    TableSound.entries.forEach { assertEquals(it, TableSound.ofId(it.id)) }
  }

  @Test
  fun `every built-in lighting preset resolves from its file spelling`() {
    assertEquals(listOf("neutral", "warm", "cool", "dim"), TableLight.entries.map(TableLight::id))
    TableLight.entries.forEach { assertEquals(it, TableLight.ofId(it.id)) }
  }

  @Test
  fun `a package cannot invent a sound or a lighting preset`() {
    assertNull(TableSound.ofId("thunder"))
    assertNull(TableSound.ofId(null))
    assertNull(TableLight.ofId("strobe"))
    assertNull(TableLight.ofId(null))
  }
}
