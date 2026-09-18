package de.drehtuer.dinfinity

import de.drehtuer.dinfinity.core.model.AtlasImage
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieShape
import de.drehtuer.dinfinity.core.model.ShapeAtlas
import de.drehtuer.dinfinity.designer.BitmapAtlas
import de.drehtuer.dinfinity.designer.Draft
import de.drehtuer.dinfinity.designer.FaceFill
import de.drehtuer.dinfinity.designer.Fill
import de.drehtuer.dinfinity.designer.MinePackage
import de.drehtuer.dinfinity.dicesets.builtin.BuiltinDiceSet
import de.drehtuer.dinfinity.dicesets.install.InstalledArtwork
import de.drehtuer.dinfinity.dicesets.install.InstalledPackage
import de.drehtuer.dinfinity.dicesets.install.InstalledSets
import de.drehtuer.dinfinity.render.filament.AtlasKey
import de.drehtuer.dinfinity.simulation.api.SolidFace
import de.drehtuer.dinfinity.simulation.api.SolidFaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/**
 * A face somebody drew reaches the die that is thrown — all of it, where the
 * die shows it (`docs/face-designer.md`, "Export details"; `docs/dice-sets.md`,
 * "Textures").
 *
 * **This is the check that was missing, and it is why a device session found
 * the fault instead.** The chain from a drawing to a pixel on the tray runs
 * through six modules — `Atlas` plans it, `BitmapAtlas` paints it,
 * `MinePackage` writes the file, `InstalledSets` validates the folder,
 * `InstalledArtwork` reads it back, [DieArtwork] answers the renderer's
 * [AtlasKey] — and every link of it had tests of its own. What nothing
 * asserted was the thing a player sees: that the pixels come out where the
 * *mesh samples them*.
 *
 * They did not. A cell is sampled in the face's own frame, with the face's up
 * taken as `+z` flattened onto it (`docs/dice-sets.md`, "Up is `+z`"), and
 * the canvas masks every cell into one canonical outline — a triangle on its
 * point, a square on an edge, a kite with its short tip up. Those are not the
 * same polygon: a d20's is turned by up to 60°, a d10's by up to 154°, and
 * every one of them is drawn at 0.96 of the size the die shows. So a bucket
 * fill of a whole face came out covering part of the face, and the printed
 * label showed through the rest — which is exactly "shows the face numbers
 * and does not show the face colour".
 *
 * The test is therefore written as the promise rather than as the bug: fill a
 * face, and every point the die shows of that face carries the fill.
 *
 * It needs Robolectric's native graphics, because the one thing in the export
 * that cannot be arithmetic is putting the pixels down ([BitmapAtlas]) and the
 * one thing in the read-back that cannot is taking them up again
 * (`AtlasDecoder`). Everything between them is plain Kotlin, and this is what
 * holds the two ends to each other.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawnFaceReachesTheDieTest {
  private lateinit var root: File

  @Before
  fun setUp() {
    root = Files.createTempDirectory("dicesets").toFile()
  }

  @Test
  fun `the cell somebody drew is not empty in the atlas the renderer is handed`() {
    val die = builtin("d20")
    val atlas = atlasOf(filled(die, face = 7))

    assertEquals(emptyList<Int>(), listOf(7).filter { it in atlas.emptyCells(die.faces.size) })
  }

  @Test
  fun `and the cells nobody drew are clear, so those faces print their labels`() {
    // The documented half of the behaviour, which is not the bug: a clear
    // cell is a face that shows its number, deliberately (`docs/dice-sets.md`,
    // "Labels, and the artwork over them").
    val die = builtin("d20")
    val atlas = atlasOf(filled(die, face = 7))

    assertEquals(die.faces.indices.toList() - 7, atlas.emptyCells(die.faces.size))
  }

  @Test
  fun `a whole-face fill covers the whole of the face the die shows`() {
    // The one that failed, and the one worth keeping. Every shape at once
    // rather than one assertion each, so that a run says
    // which shapes are wrong and by how much rather than stopping at the
    // first — the difference between "the exporter is off" and "the exporter
    // is off by a different turn on every solid", which is what it was.
    val missed =
      DieShape.entries.mapNotNull { shape ->
        val die = builtinOf(shape) ?: return@mapNotNull null
        val atlas = atlasOf(filledThroughout(die))
        // Every face, not the first: the turn is a property of where the
        // face sits on the solid, so a d12 whose face 0 happens to be
        // upright says nothing at all about its other eleven.
        val bare = die.faces.indices.sumOf { face -> bareSamples(atlas, shape, face).size }
        val of = die.faces.indices.sumOf { face -> samplesIn(SolidFaces.of(shape)[face]).size }
        if (bare == 0) null else "${shape.id} $bare/$of"
      }

    assertTrue(
      "points the die shows and a fill of every face does not cover: $missed",
      missed.isEmpty(),
    )
  }

  @Test
  fun `and it is the colour that was drawn rather than the die's own`() {
    val die = builtin("d20")
    val atlas = atlasOf(filled(die, face = 0))
    val middle = pixelOf(atlas, die.shape, face = 0, at = 0.5 to 0.5)

    assertEquals(0xCC, red(atlas, middle))
    assertEquals(0x22, green(atlas, middle))
    assertEquals(0x22, blue(atlas, middle))
    assertEquals(0xFF, atlas.alphaAt(middle.first, middle.second))
  }

  /** The bundled die called [id], which is what a drawing is started from. */
  private fun builtin(id: String): Die = BuiltinDiceSet.set.die(id) ?: error("the bundled set has no '$id'")

  /** The first bundled die of [shape], or null when the set has none. */
  private fun builtinOf(shape: DieShape): Die? = BuiltinDiceSet.set.dice.firstOrNull { it.shape == shape }

  /** [die] with the bucket run over one face, which is the ordinary use of it. */
  private fun filled(
    die: Die,
    face: Int,
  ): Draft = Draft(die = die).onFace(face) { it.draw(Fill(dots = FaceFill.FACE, colorArgb = PAINT)) }

  /** And with it run over every face, which is a die somebody has coloured in. */
  private fun filledThroughout(die: Die): Draft =
    die.faces.indices.fold(Draft(die = die)) { draft, face ->
      draft.onFace(face) { it.draw(Fill(dots = FaceFill.FACE, colorArgb = PAINT)) }
    }

  /**
   * The atlas [draft] ends up as, by the road the app actually takes.
   *
   * Painted on a device, written into a package, validated as a stranger's
   * package is, installed in a folder, found again by set id, and read back
   * through the key the renderer would hand over. Nothing is short-circuited,
   * because every one of those steps is a step that could drop the picture.
   */
  private fun atlasOf(draft: Draft): AtlasImage {
    val files =
      MinePackage.of(
        drawings = listOf(draft),
        license = "CC0-1.0",
        author = null,
        painter = BitmapAtlas(),
      )
    files.forEach { (path, bytes) ->
      val file = File(File(root, MinePackage.ID), path)
      file.parentFile?.mkdirs()
      file.writeBytes(bytes)
    }
    val installed = InstalledSets(root)
    val ready = installed.find(MinePackage.ID) as? InstalledPackage.Ready ?: error("the package did not install")
    val die = ready.set.die(draft.die.id) ?: error("'${draft.die.id}' is not in the package")
    val path = die.texturePath ?: error("'${die.id}' came out of the exporter with no texture")
    val artwork = DieArtwork(InstalledArtwork(installed)::read)
    return artwork(AtlasKey.of(MinePackage.ID, path)) ?: error("the renderer would be handed nothing for '$path'")
  }

  /** The points of [face]'s cell the die shows that the atlas leaves clear. */
  private fun bareSamples(
    atlas: AtlasImage,
    shape: DieShape,
    face: Int,
  ): List<Pair<Double, Double>> =
    samplesIn(SolidFaces.of(shape)[face]).filter { at ->
      val (x, y) = pixelOf(atlas, shape, face, at)
      atlas.alphaAt(x, y) <= AtlasImage.CLEAR_ALPHA
    }

  /**
   * Points well inside the polygon the mesh samples for [face], in cell
   * coordinates.
   *
   * Corners and edge midpoints, pulled in a twentieth of the way to the
   * middle so that nothing here is a question about the last pixel of a hard
   * clip. Every one of them is a place the die really does show.
   */
  private fun samplesIn(face: SolidFace): List<Pair<Double, Double>> {
    val corners = face.corners.map(face::cellOf)
    val middles =
      corners.indices.map { at ->
        val next = corners[(at + 1) % corners.size]
        (corners[at].first + next.first) / 2 to (corners[at].second + next.second) / 2
      }
    return (corners + middles).map { (u, v) ->
      MIDDLE + (u - MIDDLE) * INSIDE to MIDDLE + (v - MIDDLE) * INSIDE
    }
  }

  /** Where [at] in [face]'s cell lands in the whole image. */
  private fun pixelOf(
    atlas: AtlasImage,
    shape: DieShape,
    face: Int,
    at: Pair<Double, Double>,
  ): Pair<Int, Int> {
    val grid = ShapeAtlas.gridFor(shape)
    val (column, row) = ShapeAtlas.cellOf(shape, face)
    val cellWidth = atlas.width.toDouble() / grid.columns
    val cellHeight = atlas.height.toDouble() / grid.rows
    return ((column + at.first) * cellWidth).toInt().coerceIn(0, atlas.width - 1) to
      ((row + at.second) * cellHeight).toInt().coerceIn(0, atlas.height - 1)
  }

  private fun red(
    atlas: AtlasImage,
    at: Pair<Int, Int>,
  ): Int = channel(atlas, at, 0)

  private fun green(
    atlas: AtlasImage,
    at: Pair<Int, Int>,
  ): Int = channel(atlas, at, 1)

  private fun blue(
    atlas: AtlasImage,
    at: Pair<Int, Int>,
  ): Int = channel(atlas, at, 2)

  private fun channel(
    atlas: AtlasImage,
    at: Pair<Int, Int>,
    offset: Int,
  ): Int {
    assertNotNull(atlas)
    val index = (at.second * atlas.width + at.first) * AtlasImage.CHANNELS + offset
    return atlas.pixels[index].toInt() and BYTE
  }

  private companion object {
    /** A colour no die is, so a pixel carrying it came from the drawing. */
    const val PAINT = 0xFFCC2222.toInt()

    /** The middle of a cell, which every sample is measured from. */
    const val MIDDLE = 0.5

    /** How far out the samples go: all but a twentieth of the way to the edge. */
    const val INSIDE = 0.95

    const val BYTE = 0xFF
  }
}
