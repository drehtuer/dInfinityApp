package de.drehtuer.dinfinity.designer

import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.ShapeAtlas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a drawing lands in the image it is exported as
 * (`docs/face-designer.md`, "Export details").
 *
 * All of it is arithmetic, which is the point: the painter that follows has
 * nothing left to decide, so everything that can be *wrong* about an atlas is
 * asserted here rather than looked at on a phone.
 */
class AtlasTest {
  @Test
  fun `a d20 is five cells by four at 256 pixels each`() {
    val plan = Atlas.plan(Drawings.drawn(Drawings.die(DieShape.Icosahedron), 0))

    // `docs/face-designer.md`: "a d20 atlas is therefore 1280x1024 (5x4
    // cells)". If this number moves, that sentence moves with it.
    assertEquals(1280, plan?.width)
    assertEquals(1024, plan?.height)
  }

  @Test
  fun `every catalogue shape stays inside the texture limit`() {
    DieShape.entries.forEach { shape ->
      val plan = Atlas.plan(Drawings.drawn(Drawings.die(shape), 0))
      // 2048 is the limit a texture is held to (`docs/dice-sets.md`,
      // "Textures"). A cell size that put any shape past it would be a
      // designer whose own output the validator rejects.
      assertTrue("${shape.id} is ${plan?.width}x${plan?.height}", (plan?.width ?: 0) <= LIMIT)
      assertTrue("${shape.id} is ${plan?.width}x${plan?.height}", (plan?.height ?: 0) <= LIMIT)
    }
  }

  @Test
  fun `cells are square, so a face is drawn upright in one`() {
    DieShape.entries.forEach { shape ->
      val plan = Atlas.plan(Drawings.drawn(Drawings.die(shape), 0)) ?: error("${shape.id} has no plan")
      val grid = ShapeAtlas.gridFor(shape)
      assertEquals(shape.id, plan.width / grid.columns, plan.height / grid.rows)
    }
  }

  @Test
  fun `a face is in the cell the shape catalogue puts it in`() {
    // Face 7 of a d20 is column 2, row 1 of a 5x4 grid, and the app's answer
    // to that is `ShapeAtlas`'s rather than this object's — two answers would
    // be a die whose drawn faces are in the wrong places on somebody else's
    // phone.
    val plan = Atlas.plan(Drawings.drawn(Drawings.die(DieShape.Icosahedron), 7))
    val cell = plan?.cells?.single()

    assertEquals(7, cell?.index)
    assertEquals(2 * Atlas.CELL_PIXELS, cell?.left)
    assertEquals(1 * Atlas.CELL_PIXELS, cell?.top)
    assertEquals(Atlas.CELL_PIXELS, cell?.size)
    // The marks are carried across untouched; turning them into pixels is
    // `placed`'s job and the painter's moment.
    assertEquals(1, cell?.marks?.size)
  }

  @Test
  fun `a cell nobody drew on is not in the plan, so it stays transparent`() {
    val plan = Atlas.plan(Drawings.drawn(Drawings.die(DieShape.Cube), 0, 3))

    // The cells that are there are the cells with ink. Everything else is the
    // bitmap as it was created, which is nothing at all — and a transparent
    // cell is what lets the die's printed label show through
    // (`docs/dice-sets.md`, "Textures").
    assertEquals(listOf(0, 3), plan?.cells?.map(AtlasCell::index))
  }

  @Test
  fun `a face that was drawn on and then cleared is not a cell either`() {
    val cleared = Draft(die = Drawings.die(DieShape.Cube)).onFace(1) { it.draw(Drawings.line()).clear() }

    assertNull(Atlas.plan(cleared))
  }

  @Test
  fun `a die with nothing on it has no atlas at all`() {
    // Rather than a wholly transparent image, which would be a megabyte of
    // texture that says nothing.
    assertNull(Atlas.plan(Draft(die = Drawings.die(DieShape.Cube))))
  }

  @Test
  fun `a dot in the corner of the canvas is the corner of its cell`() {
    val cell = AtlasCell(index = 0, left = 512, top = 256, size = 256, marks = emptyList())

    assertEquals(Dot(512f, 256f), cell.at(Dot(0f, 0f)))
    assertEquals(Dot(768f, 512f), cell.at(Dot(1f, 1f)))
  }

  @Test
  fun `a nib is a fraction of the canvas, so it scales with the cell`() {
    val cell = AtlasCell(index = 0, left = 0, top = 0, size = 256, marks = emptyList())

    assertEquals(5.12f, cell.pixels(0.02f), 0.0001f)
  }

  @Test
  fun `placing a mark moves its dots and keeps everything else about it`() {
    val stroke = Stroke(dots = listOf(Dot(0f, 0f), Dot(1f, 1f)), colorArgb = Drawings.RED, width = 0.05f, erases = true)
    val cell = AtlasCell(index = 0, left = 100, top = 200, size = 10, marks = listOf(stroke))

    val placed = cell.placed().single() as Stroke

    assertEquals(listOf(Dot(100f, 200f), Dot(110f, 210f)), placed.dots)
    assertEquals(Drawings.RED, placed.colorArgb)
    assertTrue(placed.erases)
    assertEquals(0.05f, placed.width, 0f)
  }

  @Test
  fun `the plan carries the cell outline, so the painter has nothing to work out`() {
    val plan = Atlas.plan(Drawings.drawn(Drawings.die(DieShape.Dodecahedron), 0))

    assertEquals(FaceOutline.Pentagon, plan?.outline)
    assertEquals(FaceShapes.corners(FaceOutline.Pentagon), plan?.corners)
  }

  @Test
  fun `a coin has no corners, because a disc is not a polygon`() {
    val plan = Atlas.plan(Drawings.drawn(Drawings.die(DieShape.Coin), 0))

    assertEquals(FaceOutline.Circle, plan?.outline)
    assertTrue(plan?.corners.orEmpty().isEmpty())
  }

  @Test
  fun `the resolution is the caller's to name, so a test can work in small numbers`() {
    val plan = Atlas.plan(Drawings.drawn(Drawings.die(DieShape.Cube), 5), cellPixels = 4)

    assertEquals(12, plan?.width)
    assertEquals(8, plan?.height)
    assertEquals(8, plan?.cells?.single()?.left)
    assertEquals(4, plan?.cells?.single()?.top)
  }

  private companion object {
    const val LIMIT = 2048
  }
}
