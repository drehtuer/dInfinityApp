package de.drehtuer.dinfinity.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ShapeAtlasTest {
  @Test
  fun `every catalogue shape has a grid with room for all its faces`() {
    DieShape.entries.forEach { shape ->
      val grid = ShapeAtlas.gridFor(shape)
      assertTrue(grid.columns * grid.rows >= shape.faceCount, "${shape.id} does not fit its grid")
    }
  }

  @Test
  fun `a d20 is five across and four down`() {
    assertEquals(ShapeAtlas.Grid(columns = 5, rows = 4), ShapeAtlas.gridFor(DieShape.Icosahedron))
  }

  @Test
  fun `a d6 is three across and two down`() {
    assertEquals(ShapeAtlas.Grid(columns = 3, rows = 2), ShapeAtlas.gridFor(DieShape.Cube))
  }

  @Test
  fun `a coin is two across and one down`() {
    assertEquals(ShapeAtlas.Grid(columns = 2, rows = 1), ShapeAtlas.gridFor(DieShape.Coin))
  }

  @Test
  fun `a grid is never taller than it is wide`() {
    DieShape.entries.forEach { shape ->
      val grid = ShapeAtlas.gridFor(shape)
      assertTrue(grid.columns >= grid.rows, "${shape.id} is ${grid.columns}×${grid.rows}")
    }
  }

  @Test
  fun `faces run left to right and then down`() {
    assertEquals(0 to 0, ShapeAtlas.cellOf(DieShape.Icosahedron, 0))
    assertEquals(4 to 0, ShapeAtlas.cellOf(DieShape.Icosahedron, 4))
    assertEquals(0 to 1, ShapeAtlas.cellOf(DieShape.Icosahedron, 5))
    assertEquals(4 to 3, ShapeAtlas.cellOf(DieShape.Icosahedron, 19))
  }

  @Test
  fun `no two faces share a cell`() {
    DieShape.entries.forEach { shape ->
      val cells = (0 until shape.faceCount).map { ShapeAtlas.cellOf(shape, it) }
      assertEquals(shape.faceCount, cells.distinct().size, "${shape.id} reuses a cell")
    }
  }

  @Test
  fun `a d20 leaves no cell spare, and a d18 leaves two`() {
    assertEquals(0, ShapeAtlas.gridFor(DieShape.Icosahedron).spareCells(DieShape.Icosahedron.faceCount))
    assertEquals(
      2,
      ShapeAtlas.gridFor(DieShape.EnneagonalTrapezohedron).spareCells(DieShape.EnneagonalTrapezohedron.faceCount),
    )
  }

  @Test
  fun `there is no face beyond the shape's own`() {
    assertFailsWith<IllegalArgumentException> { ShapeAtlas.cellOf(DieShape.Cube, 6) }
    assertFailsWith<IllegalArgumentException> { ShapeAtlas.cellOf(DieShape.Cube, -1) }
  }

  @Test
  fun `a die with no faces has no atlas`() {
    assertFailsWith<IllegalArgumentException> { ShapeAtlas.gridFor(0) }
  }
}
