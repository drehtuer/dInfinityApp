package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.glyphs.SignedDistanceField
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.Face
import de.drehtuer.dinfinity.core.model.ShapeAtlas
import de.drehtuer.dinfinity.simulation.api.ShapeGeometry
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a die with no artwork has printed on it.
 *
 * The bug worth spending tests on is a die whose printed face and scored face
 * disagree, so nothing here compares one picture to another: the d4's numbers
 * are checked against `simulation/api`'s own account of which corner is which
 * catalogue face, the same account the reader reads
 * (`docs/architecture.md`, decision 45).
 */
class DieNumbersTest {
  private val d6 = Die.standard("d6", DieShape.Cube)
  private val d20 = Die.standard("d20", DieShape.Icosahedron)
  private val d4 = Die.standard("d4", DieShape.Tetrahedron)

  @Test
  fun `prints one number in the middle of every face of a face-read solid`() {
    val cells = DieNumbers.plan(d6)
    assertEquals(6, cells.size)
    cells.forEachIndexed { index, cell ->
      assertEquals(index, cell.index)
      val mark = cell.marks.single()
      assertEquals("${index + 1}", mark.text)
      assertEquals(0.5, mark.placement.centreX, 0.0001)
      assertEquals(0.5, mark.placement.centreY, 0.0001)
      assertEquals(0.0, mark.placement.turns, 0.0001)
    }
  }

  @Test
  fun `puts each face in the cell the atlas grid gives it`() {
    DieNumbers.plan(d20).forEach { cell ->
      val (column, row) = ShapeAtlas.cellOf(DieShape.Icosahedron, cell.index)
      assertEquals(column, cell.column)
      assertEquals(row, cell.row)
    }
  }

  @Test
  fun `prints a d4's values at its corners, one per corner of every face`() {
    val mesh = DieMesh.of(DieShape.Tetrahedron)
    val directions = ShapeGeometry.directionsOf(DieShape.Tetrahedron).map(Vector3::normalised)
    DieNumbers.plan(d4, mesh).forEach { cell ->
      assertEquals(3, cell.marks.size)
      // The cell of face i carries the three corners that are *not* i, which
      // is what makes a settled d4 readable from any of its visible faces.
      val surface = mesh.faces.first { it.index == cell.index }
      val expected =
        surface.positions
          .map { position -> directions.indices.minBy { (directions[it] - position.normalised()).length } }
          .map { "${it + 1}" }
          .toSet()
      assertEquals(expected, cell.marks.map { it.text }.toSet())
      assertFalse("face ${cell.index} prints its own value", "${cell.index + 1}" in expected)
    }
  }

  @Test
  fun `turns each of a d4's numbers to face its own corner`() {
    // A number that read upright on every corner would be upside down on two
    // of the three ways the die can come to rest.
    DieNumbers.plan(d4).forEach { cell ->
      cell.marks.forEach { mark ->
        val x = mark.placement.centreX - 0.5
        val y = mark.placement.centreY - 0.5
        // Up, after the turn, is (-sin, -cos) in cell coordinates.
        val angle = mark.placement.turns * 2 * Math.PI
        val upX = -kotlin.math.sin(angle)
        val upY = -kotlin.math.cos(angle)
        val length = kotlin.math.sqrt(x * x + y * y)
        assertTrue("a corner number sits at the middle of the cell", length > 0.1)
        assertEquals("the number does not face its corner", 1.0, (upX * x + upY * y) / length, 0.001)
      }
    }
  }

  @Test
  fun `keeps a d4's numbers inside the cell they belong to`() {
    DieNumbers.plan(d4).forEach { cell ->
      cell.marks.forEach { mark ->
        assertTrue(mark.placement.centreX in 0.0..1.0)
        assertTrue(mark.placement.centreY in 0.0..1.0)
      }
    }
  }

  @Test
  fun `underlines a six only when the die also carries a nine`() {
    // What a moulded die does: a d20's 6 and 9 are barred and a d6's 6 is not,
    // because a d6 has nothing its 6 could be mistaken for.
    assertTrue(DieNumbers.isAmbiguous("6", d20))
    assertTrue(DieNumbers.isAmbiguous("9", d20))
    assertFalse(DieNumbers.isAmbiguous("6", d6))
    assertFalse(DieNumbers.isAmbiguous("8", d20))
    assertFalse(DieNumbers.isAmbiguous("2", d20))
    assertFalse(DieNumbers.isAmbiguous("11", d20))
    assertFalse(DieNumbers.isAmbiguous("19", d20))
  }

  @Test
  fun `underlines what it says it underlines`() {
    val barred = DieNumbers.plan(d20).filter { cell -> cell.marks.any { it.placement.underlined } }
    assertEquals(setOf("6", "9"), barred.flatMap { cell -> cell.marks.map { it.text } }.toSet())
  }

  @Test
  fun `the rule is about the labels, not about how many sides the die has`() {
    // A cube can be labelled like a d20's worst pair, and then it needs bars
    // for the same reason a d20 does.
    val odd = die(listOf("1", "2", "3", "4", "6", "9"), DieShape.Cube)
    assertTrue(DieNumbers.isAmbiguous("6", odd))
    assertTrue(DieNumbers.isAmbiguous("9", odd))
    // `69` turned about is `69`, so it can never be taken for anything else.
    val symmetric = die(listOf("69", "2", "3", "4", "5", "1"), DieShape.Cube)
    assertFalse(DieNumbers.isAmbiguous("69", symmetric))
  }

  @Test
  fun `prints the value when the font cannot draw the label`() {
    val skulls = die(listOf("☠", "2", "3", "4", "5", "6"), DieShape.Cube)
    assertEquals("1", DieNumbers.textOf(skulls.faces[0]))
    assertEquals("2", DieNumbers.textOf(skulls.faces[1]))
  }

  @Test
  fun `prints nothing on a face an author left blank`() {
    val fudge = die(listOf("", "", "0", "0", "+", "+"), DieShape.Cube)
    assertEquals("", DieNumbers.textOf(fudge.faces[0]))
    assertTrue(
      DieNumbers
        .plan(fudge)
        .first()
        .marks
        .isEmpty(),
    )
  }

  @Test
  fun `brings a long label down so that it fits its cell`() {
    val long = die(listOf("1", "2", "3", "4", "5", "8888"), DieShape.Cube)
    val plan = DieNumbers.plan(long)
    val one =
      plan[0]
        .marks
        .single()
        .placement.height
    val four =
      plan[5]
        .marks
        .single()
        .placement.height
    assertTrue("a four-character label was not brought down: $four against $one", four < one)
    assertTrue(four > 0)
  }

  @Test
  fun `the field is the atlas grid at the size it was asked for`() {
    val field = requireNotNull(DieNumbers.fieldOf(d20, cellPixels = 32))
    val grid = ShapeAtlas.gridFor(DieShape.Icosahedron)
    assertEquals(grid.columns * 32, field.width)
    assertEquals(grid.rows * 32, field.height)
    assertEquals(field.width * field.height, field.pixels.size)
  }

  @Test
  fun `every face's cell has ink in it`() {
    val field = requireNotNull(DieNumbers.fieldOf(d20, cellPixels = 32))
    DieNumbers.plan(d20).forEach { cell ->
      assertTrue("nothing is printed on face ${cell.index}", inked(field, cell, 32))
    }
  }

  @Test
  fun `a spare cell of the grid is left empty`() {
    // A d10 is a 4x3 grid with ten faces in it, so two cells carry nothing.
    val d10 = Die.standard("d10", DieShape.PentagonalTrapezohedron)
    val field = requireNotNull(DieNumbers.fieldOf(d10, cellPixels = 32))
    val grid = ShapeAtlas.gridFor(DieShape.PentagonalTrapezohedron)
    assertEquals(2, grid.spareCells(DieShape.PentagonalTrapezohedron.faceCount))
    val spare = PrintedCell(index = -1, column = 3, row = 2, marks = emptyList())
    assertFalse(inked(field, spare, 32))
  }

  @Test
  fun `a die with nothing printed on it has no field at all`() {
    // Not a blank image: "this die prints nothing" is the difference between
    // drawing a plain resin d6 and drawing one with an invisible number on it.
    assertNull(DieNumbers.fieldOf(die(List(6) { "" }, DieShape.Cube)))
  }

  @Test
  fun `refuses a field whose bytes do not fill it`() {
    val broken = runCatching { NumberField(width = 4, height = 4, pixels = ByteArray(3)) }
    assertTrue(broken.exceptionOrNull() is IllegalArgumentException)
  }

  private fun inked(
    field: NumberField,
    cell: PrintedCell,
    size: Int,
  ): Boolean =
    (0 until size).any { row ->
      (0 until size).any { column ->
        val x = cell.column * size + column
        val y = cell.row * size + row
        (field.pixels[y * field.width + x].toInt() and 0xFF) >= SignedDistanceField.EDGE
      }
    }

  private fun die(
    labels: List<String>,
    shape: DieShape,
  ): Die =
    Die(
      id = "test",
      shape = shape,
      faces = labels.mapIndexed { index, label -> Face(index = index, value = index + 1, label = label) },
    )

  @Test
  fun `builds a die's numbers once and hands the same field back after that`() {
    val printed = PrintedDice(cellPixels = 16)

    val first = printed.of(d20)
    val again = printed.of(d20)

    assertSame("the field was rebuilt for the same die", first, again)
    assertEquals(1, printed.built)
  }

  @Test
  fun `builds a different field for a different die`() {
    val printed = PrintedDice(cellPixels = 16)

    printed.of(d20)
    printed.of(d6)

    assertEquals(2, printed.built)
  }

  @Test
  fun `never builds anything for a die whose author supplied artwork`() {
    val printed = PrintedDice(cellPixels = 16)

    assertNull(printed.of(d6.copy(texturePath = "textures/d6.png")))
    assertEquals("an atlas was written over", 0, printed.built)
  }
}
