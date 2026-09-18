package de.drehtuer.dinfinity.designer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.drehtuer.dinfinity.core.model.DieShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The pixels (`docs/face-designer.md`, "Export details").
 *
 * Robolectric in **native** graphics mode, which is a real Skia rather than a
 * shadow that records calls: what is asserted here is that a cell nobody drew
 * on comes out transparent and a cell somebody drew on does not, and a stubbed
 * canvas could say neither. It is the tier below a device rather than a
 * substitute for one — how a drawing *looks* is still a judgement somebody
 * makes with a phone in their hand.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapAtlasTest {
  private val painter = BitmapAtlas()

  @Test
  fun `what comes out is a PNG of the size the plan asked for`() {
    val plan = planFor(DieShape.Cube)

    val bitmap = paint(plan)

    assertEquals(plan.width, bitmap.width)
    assertEquals(plan.height, bitmap.height)
  }

  @Test
  fun `a cell nobody drew on is transparent, so the printed label shows through`() {
    // Cell 0 is the top-left of the d6's 3x2 grid; cell 1 is beside it and was
    // never drawn on (`docs/dice-sets.md`, "Textures": an atlas may leave
    // cells transparent, and the label is rendered in the empty ones).
    val bitmap = paint(planFor(DieShape.Cube))

    assertEquals(TRANSPARENT, bitmap.getPixel(Atlas.CELL_PIXELS + HALF_CELL, HALF_CELL))
  }

  @Test
  fun `a stroke lands on the face it was drawn on`() {
    val drawn = Draft(die = Drawings.die(DieShape.Cube)).onFace(0) { it.draw(across(width = 0.2f)) }

    val bitmap = paint(Atlas.plan(drawn) ?: error("nothing to paint"))

    assertEquals(Drawings.RED, bitmap.getPixel(HALF_CELL, HALF_CELL))
  }

  @Test
  fun `ink outside the face outline is clipped away`() {
    // A triangle fills the cell, so the cell's own corner is not on the face.
    // A turned paste puts marks out there on purpose (`FaceTransform`), and
    // what falls outside the outline belongs to no face at all.
    val drawn = Draft(die = Drawings.die(DieShape.Icosahedron)).onFace(0) { it.draw(wholeFace()) }

    val bitmap = paint(Atlas.plan(drawn) ?: error("nothing to paint"))

    assertEquals("the middle of the face", Drawings.RED, bitmap.getPixel(HALF_CELL, HALF_CELL))
    assertEquals("the cell's top-left corner", TRANSPARENT, bitmap.getPixel(2, 2))
  }

  @Test
  fun `the eraser clears back to transparent, because the paper here is nothing`() {
    // On the canvas the eraser paints the paper's own white; in the atlas the
    // paper is transparent, and a white stroke on a black die would be a mark
    // nobody drew.
    val drawn =
      Draft(die = Drawings.die(DieShape.Cube)).onFace(0) {
        it.draw(wholeFace()).draw(across(width = 0.3f, erases = true))
      }

    val bitmap = paint(Atlas.plan(drawn) ?: error("nothing to paint"))

    assertEquals(TRANSPARENT, bitmap.getPixel(HALF_CELL, HALF_CELL))
  }

  @Test
  fun `a disc is masked to its circle like any other outline`() {
    val drawn = Draft(die = Drawings.die(DieShape.Coin)).onFace(0) { it.draw(wholeFace()) }

    val bitmap = paint(Atlas.plan(drawn) ?: error("nothing to paint"))

    assertEquals(Drawings.RED, bitmap.getPixel(HALF_CELL, HALF_CELL))
    assertEquals(TRANSPARENT, bitmap.getPixel(1, 1))
  }

  @Test
  fun `a stamped zero is painted with its hole left open`() {
    // The rings of a glyph are wound against each other and are drawn as one
    // shape under the even-odd rule; a counter painted as a shape of its own
    // would be a blob where the hole is (`designer`'s `Stamp`).
    val zero =
      FaceStamp.at("0", Dot(0.5f, 0.5f), FaceOutline.Square, StampSize.Large, Drawings.RED)
        ?: error("the font has no zero")
    val drawn = Draft(die = Drawings.die(DieShape.Cube)).onFace(0) { it.draw(zero) }

    val bitmap = paint(Atlas.plan(drawn) ?: error("nothing to paint"))

    val onTheInk = ((zero.rings[0].minOf { it.x } + zero.rings[1].minOf { it.x }) / 2 * Atlas.CELL_PIXELS).toInt()
    assertEquals("the stroke of the zero", Drawings.RED, bitmap.getPixel(onTheInk, HALF_CELL))
    assertEquals("the hole in the zero", TRANSPARENT, bitmap.getPixel(HALF_CELL, HALF_CELL))
  }

  @Test
  fun `a pipped face is painted pip for pip, and the paper between them left alone`() {
    // Pips are rings like a glyph's, so the exporter paints them without being
    // told what a pip is (`docs/face-designer.md`, "Fill all with eyes"). A
    // `4` has its ink in the corners and nothing in the middle.
    val four = FaceEyes.of(4, Drawings.RED) ?: error("no pattern for a four")
    val drawn = Draft(die = Drawings.die(DieShape.Cube)).onFace(0) { it.draw(four) }

    val bitmap = paint(Atlas.plan(drawn) ?: error("nothing to paint"))

    val near = (FaceEyes.NEAR * Atlas.CELL_PIXELS).toInt()
    assertEquals("the pip in the corner", Drawings.RED, bitmap.getPixel(near, near))
    assertEquals("the paper between the pips", TRANSPARENT, bitmap.getPixel(HALF_CELL, HALF_CELL))
  }

  @Test
  fun `every catalogue shape paints`() {
    DieShape.entries.forEach { shape -> assertNotNull(shape.id, painter.png(planFor(shape))) }
  }

  @Test
  fun `a plan no bitmap could be made for is refused rather than thrown`() {
    val impossible =
      AtlasPlan(width = 0, height = 0, outline = FaceOutline.Square, corners = emptyList(), cells = emptyList())

    // A die with no atlas prints its labels; an exception here would cost
    // somebody the other nineteen dice in their package.
    assertNull(painter.png(impossible))
  }

  private fun planFor(shape: DieShape): AtlasPlan =
    Atlas.plan(Drawings.drawn(Drawings.die(shape), 0)) ?: error("${shape.id} has nothing to paint")

  /** A broad stroke straight across the middle of the face. */
  private fun across(
    width: Float,
    erases: Boolean = false,
  ) = Stroke(
    dots = listOf(Dot(0.1f, 0.5f), Dot(0.9f, 0.5f)),
    colorArgb = Drawings.RED,
    width = width,
    erases = erases,
  )

  /** The bucket on bare paper, which is the whole canvas square (`FaceFill`). */
  private fun wholeFace() = Fill(dots = FaceFill.FACE, colorArgb = Drawings.RED)

  private fun paint(plan: AtlasPlan): Bitmap {
    val bytes = painter.png(plan) ?: error("nothing was painted")
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("what came out was not a PNG")
  }

  private companion object {
    const val TRANSPARENT = 0
    const val HALF_CELL = Atlas.CELL_PIXELS / 2
  }
}
