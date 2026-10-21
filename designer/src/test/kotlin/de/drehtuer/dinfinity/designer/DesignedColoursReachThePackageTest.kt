package de.drehtuer.dinfinity.designer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import de.drehtuer.dinfinity.core.model.DiceSet
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.dicesets.format.DiceSetValidator
import de.drehtuer.dinfinity.dicesets.format.PackageFiles
import de.drehtuer.dinfinity.dicesets.format.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * The colours somebody chose in the designer are the colours in the package
 * the tray reads (`docs/face-designer.md`, "Material, colour and edges" and
 * "Export details").
 *
 * The pieces are tested apart elsewhere — the presenter puts the pen's ink on
 * the marks it lays down, `BitmapAtlasTest` paints one red stroke red — and
 * yellow pips on a black die still came to the tray black, because nothing
 * followed one colour from the drawing through the painter, the package and the
 * validator to the pixels and the die a renderer is handed. This does, with
 * the real painter in native graphics mode and the real validator; what the
 * renderer then makes of those pixels is `render/filament`'s
 * `AtlasTextureTest`, on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignedColoursReachThePackageTest {
  @Test
  fun `eyes in a chosen ink on a black die come out in that ink on a black die`() {
    val draft = FaceEyes.fill(Draft(die = Drawings.die(DieShape.Cube), finish = black()), YELLOW)

    val (set, files) = packaged(draft)
    val die = set.dice.single()

    assertEquals(BLACK, die.material.colorArgb)
    // Face 0 scores 1, and its one pip is in the middle of the face.
    val middle = cellOf(draft, 0).at(Dot(HALF, HALF))
    assertEquals(YELLOW, atlasOf(die, files).getPixel(middle.x.toInt(), middle.y.toInt()))
  }

  @Test
  fun `every pip of every face is the ink, and nothing else on the face is`() {
    val draft = FaceEyes.fill(Draft(die = Drawings.die(DieShape.Cube), finish = black()), YELLOW)

    val (set, files) = packaged(draft)

    assertEveryCellIsInked(draft, atlasOf(set.dice.single(), files), YELLOW)
  }

  @Test
  fun `numbers in a chosen ink keep that ink on every face of a d20`() {
    val draft = FaceStamp.fill(Draft(die = Drawings.die(DieShape.Icosahedron), finish = black()), RED)

    val (set, files) = packaged(draft)

    assertEveryCellIsInked(draft, atlasOf(set.dice.single(), files), RED)
  }

  @Test
  fun `the body colour and the ink stay apart however close they are`() {
    // A dark ink on a dark body is the author's call, not something to fix up:
    // nothing on the way may swap one for the other or average them.
    val navy = 0xFF101040.toInt()
    val draft =
      FaceStamp.fill(
        Draft(die = Drawings.die(DieShape.Cube), finish = DieFinish.STANDARD.coloured(navy)),
        DEEP_BLUE,
      )

    val (set, files) = packaged(draft)
    val die = set.dice.single()

    assertEquals(navy, die.material.colorArgb)
    assertEveryCellIsInked(draft, atlasOf(die, files), DEEP_BLUE)
  }

  /**
   * Every fully opaque pixel of every drawn cell is [ink]. The soft rim a
   * numeral or a pip is anti-aliased with is partly clear and is left out,
   * because its colour is the ink at less than full strength rather than
   * another colour.
   */
  private fun assertEveryCellIsInked(
    draft: Draft,
    atlas: Bitmap,
    ink: Int,
  ) {
    draft.die.faces.indices.forEach { index ->
      val cell = cellOf(draft, index)
      val solid =
        (cell.top until cell.top + cell.size)
          .flatMap { y -> (cell.left until cell.left + cell.size).map { x -> atlas.getPixel(x, y) } }
          .filter { it ushr ALPHA_SHIFT == OPAQUE }
      assertTrue("face $index has no ink on it", solid.isNotEmpty())
      assertEquals("face $index", setOf(ink), solid.toSet())
    }
  }

  private fun cellOf(
    draft: Draft,
    index: Int,
  ): AtlasCell = Atlas.plan(draft)?.cells?.single { it.index == index } ?: error("face $index was not drawn")

  private fun packaged(draft: Draft): Pair<DiceSet, Map<String, ByteArray>> {
    val files = MinePackage.of(listOf(draft), SetLicense.Mit.id, author = null, painter = BitmapAtlas())
    val result = DiceSetValidator.validate(PackageFiles.of(files))
    assertTrue("$result", result is ValidationResult.Valid)
    return (result as ValidationResult.Valid).set to files
  }

  /** The die's atlas as the tray decodes it: straight alpha, not premultiplied. */
  private fun atlasOf(
    die: Die,
    files: Map<String, ByteArray>,
  ): Bitmap {
    val path = die.texturePath ?: error("${die.id} has no atlas")
    val bytes = files[path] ?: error("the package has no $path")
    val options = BitmapFactory.Options().apply { inPremultiplied = false }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("the atlas did not decode")
  }

  private fun black(): DieFinish = DieFinish.STANDARD.coloured(BLACK)

  private companion object {
    const val BLACK = 0xFF000000.toInt()
    const val YELLOW = 0xFFF2C200.toInt()
    const val RED = 0xFFC01010.toInt()
    const val DEEP_BLUE = 0xFF2030A0.toInt()
    const val HALF = 0.5f
    const val ALPHA_SHIFT = 24
    const val OPAQUE = 0xFF
  }
}
