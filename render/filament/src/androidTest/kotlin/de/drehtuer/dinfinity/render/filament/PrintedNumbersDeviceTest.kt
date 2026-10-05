package de.drehtuer.dinfinity.render.filament

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.drehtuer.dinfinity.core.model.Die
import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.FaceRead
import de.drehtuer.dinfinity.core.model.ShapeAtlas
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SolidFace
import de.drehtuer.dinfinity.simulation.api.SolidFaces
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The printed numbers, read back off a frame the GPU drew
 * (`docs/physics-and-rendering.md`, "Rendering (normal mode)").
 *
 * Every glyph on every die was drawn reflected for the whole of `v0.1.0`, and
 * the fix was confirmed by a photograph. A photograph cannot pin it down: a
 * reflection in `u` and a reflection in `v` differ by a half-turn, and a die
 * lands at an arbitrary orientation, so a mirrored `7` lying on its back
 * looks like a `7` the other mirror turned over.
 *
 * So this does not land the die anywhere. It turns one face to look straight
 * at the camera with its **texture-up as the camera's up**
 * ([Quaternion.carrying]), so there is exactly one right picture and it is
 * not turned, and then asks of each axis separately whether the ink leans the
 * way [DieNumbers.fieldOf] says it leans. A `u` reflection fails the
 * left-right question and only that one; a `v` reflection — the `v0.1.0` bug —
 * fails top-bottom and only that one. The negative controls draw both on
 * purpose and show that each is caught by its own question.
 *
 * No golden image and no projection of pixels: the face is square to the
 * camera, so the frame is the face at one scale and the only number worked out
 * here is how far back the camera stands for the frame to be a known square of
 * that face. Which half is heavier is the field's to say, which is what the
 * JVM already tests (`DieNumbersTest`).
 */
@RunWith(AndroidJUnit4::class)
class PrintedNumbersDeviceTest {
  private val geometry = TableGeometry.referenceDevice()

  /**
   * A floor the colour of the dice, so that wherever the frame runs past the
   * face — a d4's cell is wider than its triangle — it is not ink.
   */
  private val look =
    TableLook(
      id = "plain",
      name = "Plain",
      floorColorArgb = StandardDice.d6.material.colorArgb,
      wallColorArgb = StandardDice.d6.material.colorArgb,
    )

  /**
   * One engine for the whole class. Each check is a stage of its own, and a
   * stage that made its own engine would compile the material again — which
   * is most of a second a frame, a few hundred frames a run.
   */
  private lateinit var filament: FilamentEngine

  @Before
  fun ready() {
    FilamentStage.ready()
    filament = FilamentEngine()
  }

  @After
  fun done() {
    filament.close()
  }

  @Test
  fun aD6PrintsItsNumbersTheRightWayRound() {
    assertReadsRightWayRound(StandardDice.d6)
  }

  @Test
  fun aD20PrintsItsNumbersTheRightWayRound() {
    assertReadsRightWayRound(StandardDice.d20)
  }

  @Test
  fun aD4PrintsItsCornerNumbersTheRightWayRound() {
    // Three numbers to a face, each turned to its own corner, so a face as a
    // whole is lopsided in a way a reflection changes.
    assertReadsRightWayRound(StandardDice.d4)
  }

  @Test
  fun aNumberReflectedInUIsCaughtByTheLeftRightQuestionAlone() {
    listOf(StandardDice.d6, StandardDice.d20).forEach { die ->
      val reflected = reflectedInU(DieNumbers.fieldOf(die)!!, die)
      checksOf(die).forEach { check ->
        val drawn = leanOf(drawnDirectly(die, check.face, reflected))
        if (check.axis == Axis.Across) {
          assertTrue("${check.name(die)}: a u-reflected field read the right way round", !check.agrees(drawn))
        } else {
          assertTrue("${check.name(die)}: a u-reflection changed which way up it is", check.agrees(drawn))
        }
      }
    }
  }

  @Test
  fun aNumberReflectedInVIsCaughtByTheTopBottomQuestionAlone() {
    // This is the bug `v0.1.0` shipped: `flipUV` turning `v` a second time.
    listOf(StandardDice.d6, StandardDice.d20).forEach { die ->
      val reflected = reflectedInV(DieNumbers.fieldOf(die)!!, die)
      checksOf(die).forEach { check ->
        val drawn = leanOf(drawnDirectly(die, check.face, reflected))
        if (check.axis == Axis.Down) {
          assertTrue("${check.name(die)}: a v-reflected field read the right way round", !check.agrees(drawn))
        } else {
          assertTrue("${check.name(die)}: a v-reflection changed which way it leans", check.agrees(drawn))
        }
      }
    }
  }

  @Test
  fun theControlsOwnPathReadsAnUnreflectedFieldTheRightWayRound() {
    // The controls draw the die themselves, so that they can hand it a field
    // the app never would. That path has to pass with the real field, or a
    // control that fails proves nothing about the reflection.
    listOf(StandardDice.d6, StandardDice.d20).forEach { die ->
      val field = DieNumbers.fieldOf(die)!!
      checksOf(die).forEach { check ->
        val drawn = leanOf(drawnDirectly(die, check.face, field))
        assertTrue("${check.name(die)}: ${check.describe(drawn)}", check.agrees(drawn))
      }
    }
  }

  @Test
  fun aFrameReadBackIsTheRightWayUpAndTheRightWayRound() {
    // Everything above leans on this: the frame's top is the camera's up and
    // its right the camera's right. Asked of geometry alone — a die set off
    // towards the camera's up and right, on a dark floor — so that no texture
    // coordinate is involved in the answer.
    val dark = look.copy(floorColorArgb = DARK, wallColorArgb = DARK)
    val up = Vector3(1.0, 0.0, 0.0)
    val right = Vector3(0.0, -1.0, 0.0)
    val target = Vector3(0.0, 0.0, HEIGHT_MM)
    val offset = StandardDice.d6.material.boundingRadiusMm * 2
    val frame =
      FilamentStage(SIZE, SIZE, postProcessing = false, shared = filament).use { stage ->
        val renderer = FilamentDiceRenderer(stage)
        renderer.begin(
          ThrowSpec(
            dice = listOf(DieInstance(0, 0, "builtin", "builtin", StandardDice.d6)),
            geometry = geometry,
            table = dark,
            seed = 1L,
          ),
          geometry,
          dark,
        )
        renderer.show(
          RenderFrame.still(listOf(BodyTransform(0, target + (up + right) * offset, Quaternion.Identity))),
        )
        stage.aim(CameraShot(target + Vector3.Up * offset * OVERVIEW, target, up, FIELD_OF_VIEW_DEGREES * OVERVIEW))
        checkNotNull(stage.capture())
      }
    val lean = leanOf(frame, darkIsInk = false)
    assertTrue("the die is not right of the middle: %+.3f".format(lean.across), lean.across > 0)
    assertTrue("the die is not above the middle: %+.3f".format(lean.down), lean.down < 0)
  }

  /** Every lopsided face of [die], drawn by the renderer that ships, leans the way its field does. */
  private fun assertReadsRightWayRound(die: Die) {
    val checks = checksOf(die)
    // A die with nothing lopsided on an axis would pass whatever was drawn.
    assertTrue("${die.id} has nothing lopsided enough to tell a u-reflection", checks.any { it.axis == Axis.Across })
    assertTrue("${die.id} has nothing lopsided enough to tell a v-reflection", checks.any { it.axis == Axis.Down })
    val failures =
      checks.mapNotNull { check ->
        val drawn = leanOf(drawnByTheRenderer(die, check.face))
        if (check.agrees(drawn)) null else "${check.name(die)}: ${check.describe(drawn)}"
      }
    assertEquals("printed the wrong way round:\n${failures.joinToString("\n")}", emptyList<String>(), failures)
  }

  /** One axis of one face whose ink leans far enough to be asked about. */
  private data class Check(
    val face: Int,
    val axis: Axis,
    val expected: Double,
  ) {
    /** The same sign, and far enough from even that it is not the noise of the edge. */
    fun agrees(drawn: Lean): Boolean {
      val seen = drawn.along(axis)
      return sign(seen) == sign(expected) && abs(seen) >= abs(expected) * AGREEMENT
    }

    fun name(die: Die): String = "${die.id} face $face ('${die.faces[face].label}') ${axis.name.lowercase()}"

    fun describe(drawn: Lean): String =
      "the field leans %+.3f, the frame %+.3f (ink %.1f %% of it)".format(
        expected,
        drawn.along(axis),
        drawn.share * PERCENT,
      )
  }

  private enum class Axis { Across, Down }

  /**
   * Which way the ink leans: [across] is right minus left, [down] bottom minus
   * top, each over all the ink, so a numeral of any size is between -1 and 1.
   */
  private data class Lean(
    val across: Double,
    val down: Double,
    val share: Double,
  ) {
    fun along(axis: Axis): Double = if (axis == Axis.Across) across else down
  }

  /** The faces of [die] that lean far enough on some axis to be asked about it. */
  private fun checksOf(die: Die): List<Check> {
    val field = DieNumbers.fieldOf(die)!!
    return SolidFaces.of(die.shape).flatMap { solid ->
      val lean = leanOf(field, die, solid)
      listOf(Axis.Across, Axis.Down)
        .filter { abs(lean.along(it)) >= LOPSIDED }
        .map { Check(solid.index, it, lean.along(it)) }
    }
  }

  /**
   * How far from the middle of its cell the frame reaches, in the cell's own
   * coordinates (which run `0..1`).
   *
   * All of it for a d4, whose numbers sit in its corners and whose other
   * three faces all lean away from a camera looking square at one. For a
   * solid read by its top face, the square inside the face's inscribed
   * circle: its neighbours lean *towards* the camera and carry numbers of
   * their own, and that square is all face. The numbers are sized to fit it.
   */
  private fun reachOf(
    die: Die,
    solid: SolidFace,
  ): Double =
    if (die.shape.naturalRead == FaceRead.VertexUp) {
      HALF
    } else {
      val inscribed =
        solid.corners.indices.minOf { at ->
          val from = solid.corners[at]
          val edge = (solid.corners[(at + 1) % solid.corners.size] - from).normalised()
          val offset = solid.centre - from
          (offset - edge * (offset dot edge)).length
        }
      inscribed / sqrt(2.0) * INSIDE / (2 * solid.radius)
    }

  /** Which way the ink leans in the square of [solid]'s cell the frame will show. */
  private fun leanOf(
    field: NumberField,
    die: Die,
    solid: SolidFace,
  ): Lean {
    val grid = ShapeAtlas.gridFor(die.shape)
    val cell = field.width / grid.columns
    val (column, row) = ShapeAtlas.cellOf(die.shape, solid.index)
    val middle = cell / 2
    // The same number of columns either side of the middle, or the crop
    // itself would lean.
    val span = (reachOf(die, solid) * cell).toInt()
    val from = middle - span
    val to = middle + span
    val counts = Counts()
    for (y in from until to) {
      for (x in from until to) {
        val ink = field.pixels[(row * cell + y) * field.width + column * cell + x].toInt() and BYTE
        counts.add(ink >= EDGE, right = x >= middle, below = y >= middle)
      }
    }
    return counts.lean()
  }

  /**
   * Which way the ink leans in a frame: a pixel is ink when it is darker than
   * halfway between the darkest and the brightest in it. The dice print dark
   * on light, and the floor is the dice's own colour.
   */
  private fun leanOf(
    frame: Snapshot,
    darkIsInk: Boolean = true,
  ): Lean {
    val brightness =
      IntArray(frame.width * frame.height) { pixel ->
        val at = pixel * Snapshot.CHANNELS
        (0 until RGB).sumOf { frame.pixels[at + it].toInt() and BYTE }
      }
    val threshold = (brightness.min() + brightness.max()) / 2
    val counts = Counts()
    for (y in 0 until frame.height) {
      for (x in 0 until frame.width) {
        val dark = brightness[y * frame.width + x] < threshold
        counts.add(dark == darkIsInk, right = x >= frame.width / 2, below = y >= frame.height / 2)
      }
    }
    return counts.lean()
  }

  private class Counts {
    private var left = 0
    private var right = 0
    private var top = 0
    private var bottom = 0
    private var all = 0

    fun add(
      ink: Boolean,
      right: Boolean,
      below: Boolean,
    ) {
      all++
      if (!ink) return
      if (right) this.right++ else left++
      if (below) bottom++ else top++
    }

    fun lean(): Lean {
      val inked = (left + right).coerceAtLeast(1).toDouble()
      return Lean(
        across = (right - left) / inked,
        down = (bottom - top) / inked,
        share = (left + right) / all.toDouble(),
      )
    }
  }

  /**
   * Where the die goes and where the camera stands, so that [face] fills the
   * frame square on, the right way up.
   *
   * The camera looks straight down with `+x` as its up, the shot the app
   * takes on Table view "straight down"; the die is turned so that face's
   * outward normal points back up at it and its texture-up is `+x`.
   */
  private fun viewOf(
    die: Die,
    face: Int,
  ): Pair<BodyTransform, CameraShot> {
    val solid = SolidFaces.of(die.shape).first { it.index == face }
    val radius = die.material.boundingRadiusMm
    val up = Vector3(1.0, 0.0, 0.0)
    val turn = Quaternion.carrying(fromOut = solid.normal, fromUp = solid.up, toOut = Vector3.Up, toUp = up)
    val position = Vector3(0.0, 0.0, HEIGHT_MM)
    val target = position + turn.rotate(solid.centre * radius)
    // Half the frame, on the face, in millimetres: the cell runs over twice
    // the face's own radius.
    val reachMm = reachOf(die, solid) * 2 * solid.radius * radius
    val distance = reachMm / tan(Math.toRadians(FIELD_OF_VIEW_DEGREES / 2))
    check(distance > FilamentStage.NEAR_MM * 2) { "the camera would stand inside its own near plane" }
    return BodyTransform(index = 0, position = position, orientation = turn) to
      CameraShot(
        position = target + Vector3.Up * distance,
        target = target,
        up = up,
        verticalFieldOfViewDegrees = FIELD_OF_VIEW_DEGREES,
      )
  }

  /** [face] of [die], drawn the way a roll draws it: the renderer that ships, and the field it builds. */
  private fun drawnByTheRenderer(
    die: Die,
    face: Int,
  ): Snapshot =
    FilamentStage(SIZE, SIZE, postProcessing = false, shared = filament).use { stage ->
      val (body, shot) = viewOf(die, face)
      val renderer = FilamentDiceRenderer(stage)
      renderer.begin(
        ThrowSpec(
          dice = listOf(DieInstance(index = 0, groupId = 0, setId = "builtin", requestedSetId = "builtin", die = die)),
          geometry = geometry,
          table = look,
          seed = 1L,
        ),
        geometry,
        look,
      )
      renderer.show(RenderFrame.still(listOf(body)))
      stage.aim(shot)
      checkNotNull(stage.capture()) { "no frame came back off the GPU" }
    }

  /**
   * The same, with [numbers] printed on it instead of what the renderer would
   * have built — which is how a control puts a reflected field on a die.
   */
  private fun drawnDirectly(
    die: Die,
    face: Int,
    numbers: NumberField,
  ): Snapshot =
    FilamentStage(SIZE, SIZE, postProcessing = false, shared = filament).use { stage ->
      val (body, shot) = viewOf(die, face)
      stage.light()
      stage.add(
        GpuMesh.of(TrayMesh.of(geometry, look).partsOf(TrayPart.Floor)),
        DiceMaterial.floorOf(look),
        casts = false,
      )
      val entity =
        stage.add(
          GpuMesh.of(DieMesh.of(die, scale = 1.0).surfaces, scale = die.material.boundingRadiusMm),
          DiceMaterial.dieOf(die.material, texturePath = null, numbers = numbers),
        )
      stage.place(entity, Transform.of(body.position, body.orientation))
      stage.aim(shot)
      checkNotNull(stage.capture()) { "no frame came back off the GPU" }
    }

  /** [field] with every cell turned left for right. */
  private fun reflectedInU(
    field: NumberField,
    die: Die,
  ): NumberField {
    val cell = field.width / ShapeAtlas.gridFor(die.shape).columns
    val pixels =
      ByteArray(field.pixels.size) { at ->
        val x = at % field.width
        val y = at / field.width
        val start = x / cell * cell
        field.pixels[y * field.width + start + (cell - 1 - (x - start))]
      }
    return NumberField(field.width, field.height, pixels)
  }

  /** [field] with every cell turned top for bottom: what `flipUV` did to it. */
  private fun reflectedInV(
    field: NumberField,
    die: Die,
  ): NumberField {
    val cell = field.height / ShapeAtlas.gridFor(die.shape).rows
    val pixels =
      ByteArray(field.pixels.size) { at ->
        val x = at % field.width
        val y = at / field.width
        val start = y / cell * cell
        field.pixels[(start + (cell - 1 - (y - start))) * field.width + x]
      }
    return NumberField(field.width, field.height, pixels)
  }

  private companion object {
    /** A square frame, so that one reach serves both axes. */
    const val SIZE = 256

    /** Narrow, so the camera stands well clear of its near plane over a d20's small face. */
    const val FIELD_OF_VIEW_DEGREES = 10.0

    /** High above the floor, so the die's own shadow lands far outside the frame. */
    const val HEIGHT_MM = 100.0

    /** A face leans far enough to be asked about once its ink is this lopsided. */
    const val LOPSIDED = 0.15

    /** And the frame agrees once it leans the same way by at least this share of that. */
    const val AGREEMENT = 0.33

    /** A little inside the inscribed square, so that no edge of the face is in frame. */
    const val INSIDE = 0.95

    /** The middle of a cell, and the reach of a whole one. */
    const val HALF = 0.5

    /** Where a distance field's edge is: a half, as a byte. */
    const val EDGE = 128

    /** How much wider the right-way-up shot takes in than a face's. */
    const val OVERVIEW = 4.0

    /** A floor nothing on a die is as dark as. */
    const val DARK = 0xFF000000.toInt()

    const val BYTE = 0xFF
    const val RGB = 3
    const val PERCENT = 100.0
  }
}
