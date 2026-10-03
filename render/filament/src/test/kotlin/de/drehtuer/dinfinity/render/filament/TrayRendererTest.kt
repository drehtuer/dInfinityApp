package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.simulation.api.BoardRequest
import de.drehtuer.dinfinity.simulation.api.BoardTrack
import de.drehtuer.dinfinity.simulation.api.ClearSpace
import de.drehtuer.dinfinity.simulation.api.Quaternion
import de.drehtuer.dinfinity.simulation.api.SettleRule
import de.drehtuer.dinfinity.simulation.api.TableCapacity
import de.drehtuer.dinfinity.simulation.api.TableGeometry
import de.drehtuer.dinfinity.simulation.api.ThrowSpec
import de.drehtuer.dinfinity.simulation.api.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The renderer that outlives its surface.
 *
 * The rule it exists for is short: **a roll is never restarted to get a
 * picture back.** Turning the phone, backgrounding the app and resizing the
 * view all take the stage away and give a different one back, and none of
 * them is allowed to touch the roll — so what has to be rebuilt is the
 * picture, from what the simulation has already said.
 */
class TrayRendererTest {
  private val geometry = TableGeometry.referenceDevice()
  private val look = TableLook(id = "plain", name = "Plain")
  private val boards = FakeBoards()

  @Test
  fun `with nowhere to draw it draws nothing and does not complain`() {
    // The app in the background. The roll goes on; the frames go nowhere.
    val renderer = TrayRenderer()

    renderer.begin(spec(), geometry, look)
    renderer.show(frame(0.0))
    renderer.settled(frame(0.0))
    renderer.end()

    assertFalse(renderer.drawable)
  }

  @Test
  fun `a stage that arrives mid-roll is given the scene it missed`() {
    // The surface came back while the dice were still in the air. Nothing is
    // asked of the simulation: the tray, the dice and where they are have all
    // been said once already, and this is them being said again.
    val renderer = TrayRenderer()
    renderer.begin(spec(), geometry, look)
    renderer.show(frame(TUMBLING_HEIGHT))

    val stage = FakeStage()
    renderer.stage(stage)

    assertTrue(renderer.drawable)
    assertTrue("the new stage was left dark", stage.lit)
    assertEquals("the scene was not rebuilt on the new stage", TRAY_PARTS + DICE, stage.added.size)
    assertEquals("the dice were not put back where the roll had them", DICE, stage.placed.size)
  }

  @Test
  fun `a roll that had already settled comes back settled`() {
    // The camera moves onto the dice when they stop. A surface that arrives
    // afterwards has to show the result, not a tray shot of a finished roll.
    val onTheDice = FakeStage()
    val whileRolling = FakeStage()

    val rolling = TrayRenderer()
    rolling.stage(whileRolling)
    rolling.begin(spec(), geometry, look)
    rolling.settled(frame(0.0))

    val restored = TrayRenderer()
    restored.begin(spec(), geometry, look)
    restored.settled(frame(0.0))
    restored.stage(onTheDice)

    assertEquals(whileRolling.shots, onTheDice.shots)
  }

  @Test
  fun `losing the stage does not lose the roll`() {
    val renderer = TrayRenderer()
    val first = FakeStage()
    renderer.stage(first)
    renderer.begin(spec(), geometry, look)
    renderer.show(frame(TUMBLING_HEIGHT))

    renderer.stage(null)
    renderer.show(frame(LANDED_HEIGHT))
    assertFalse(renderer.drawable)

    val second = FakeStage()
    renderer.stage(second)

    assertEquals(TRAY_PARTS + DICE, second.added.size)
    assertEquals("the same dice were not put back", first.placed.keys, second.placed.keys)
  }

  @Test
  fun `a new stage catches up with where the dice are now, not where they were when it went`() {
    val renderer = TrayRenderer()
    renderer.stage(FakeStage())
    renderer.begin(spec(), geometry, look)
    renderer.show(frame(TUMBLING_HEIGHT))
    renderer.stage(null)

    // Four more steps happened with nobody watching.
    repeat(4) { renderer.show(frame(LANDED_HEIGHT)) }

    val caught = FakeStage()
    renderer.stage(caught)

    val onTheFloor = FakeStage()
    val reference = TrayRenderer()
    reference.stage(onTheFloor)
    reference.begin(spec(), geometry, look)
    reference.show(frame(LANDED_HEIGHT))

    assertEquals(
      "the dice came back where they were when the surface went, not where they are",
      onTheFloor.placed.mapValues { it.value.toList() },
      caught.placed.mapValues { it.value.toList() },
    )
  }

  @Test
  fun `a stage arriving before there is anything to draw is left empty`() {
    // The surface is ready long before the first roll. There is no tray yet
    // because nobody has said which table it is.
    val stage = FakeStage()
    TrayRenderer().stage(stage)

    assertEquals(0, stage.added.size)
    assertFalse(stage.lit)
  }

  @Test
  fun `a roll that ended leaves its table behind, and no dice`() {
    // Taking the dice away is not taking the table away. A stage arriving
    // after the roll is over gets the tray the dice were thrown onto and
    // nothing standing on it (`docs/TODO.md`, Step 4.1).
    val renderer = TrayRenderer()
    renderer.begin(spec(), geometry, look)
    renderer.show(frame(0.0))
    renderer.end()

    val stage = FakeStage()
    renderer.stage(stage)

    assertEquals("a roll that is over was put back on screen", TRAY_PARTS, stage.added.size)
  }

  @Test
  fun `a renderer that never had a table draws nothing when one arrives`() {
    // Nothing has been begun and no table has been named, so there is no
    // scene to rebuild — and inventing one would be drawing a table the app
    // never asked for.
    val renderer = TrayRenderer()
    renderer.end()

    val stage = FakeStage()
    renderer.stage(stage)

    assertEquals(0, stage.added.size)
    assertFalse(stage.lit)
  }

  @Test
  fun `a table with nothing on it is drawn, and rebuilt on a stage that arrives later`() {
    val renderer = TrayRenderer()
    renderer.table(geometry, look)

    val stage = FakeStage()
    renderer.stage(stage)

    assertEquals("the empty table was not built", TRAY_PARTS, stage.added.size)
    assertTrue("an empty table was left unlit", stage.lit)
    assertEquals("the camera never framed the empty table", 1, stage.shots.size)
  }

  @Test
  fun `a table named while there is somewhere to draw is built at once`() {
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)

    renderer.table(geometry, look)

    assertEquals(TRAY_PARTS, stage.added.size)
    assertTrue(stage.lit)
  }

  @Test
  fun `a throw replaces the empty table it was thrown onto`() {
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)

    renderer.begin(spec(), geometry, look)

    assertEquals("the dice landed on top of the empty table", TRAY_PARTS + DICE, stage.added.size)
  }

  @Test
  fun `where the player was looking survives the surface going and coming back`() {
    // Turning the phone while zoomed in. The roll does not restart, and neither
    // does the camera go back to the whole tray.
    val renderer = TrayRenderer()
    renderer.begin(spec(), geometry, look)
    renderer.show(frame(0.0))
    val closer = TrayView(zoom = 2.0, panAlongMm = 20.0).within(geometry)
    renderer.look(closer)

    val stage = FakeStage()
    renderer.stage(stage)

    val whole = TrayCamera.framingTheTray(geometry, stage.width.toDouble() / stage.height)
    assertEquals(
      "the new surface was framed somewhere the player had left",
      TrayCamera.framingTheTray(geometry, stage.width.toDouble() / stage.height, closer),
      stage.shots.last(),
    )
    assertTrue("the camera went back to the whole tray", stage.shots.last() != whole)
  }

  @Test
  fun `a new throw is watched from the whole table, wherever the player had been looking`() {
    // The dice can land anywhere in the tray, so a camera left closed in on one
    // corner would hide most of what was just rolled.
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)
    renderer.look(TrayView(zoom = TrayView.CLOSEST, panAlongMm = 50.0).within(geometry))

    renderer.begin(spec(), geometry, look)

    assertEquals(
      TrayCamera.framingTheTray(geometry, stage.width.toDouble() / stage.height),
      stage.shots.last(),
    )
  }

  @Test
  fun `looking around with nowhere to draw is remembered rather than lost`() {
    val renderer = TrayRenderer()
    renderer.table(geometry, look)
    val closer = TrayView(zoom = 2.0).within(geometry)
    renderer.look(closer)

    val stage = FakeStage()
    renderer.stage(stage)

    assertEquals(
      TrayCamera.framingTheTray(geometry, stage.width.toDouble() / stage.height, closer),
      stage.shots.single(),
    )
  }

  @Test
  fun `the table view the screen opened with is the one it draws`() {
    // **Table view**, read when the roll screen opens and not watched
    // (`docs/architecture.md`, decision 16). The renderer is where that answer
    // is kept, so the shot is the one the player asked for rather than the one
    // this module drew before the lean was a setting.
    val stage = FakeStage()
    val renderer = TrayRenderer(TableView.StraightDown)
    renderer.stage(stage)

    renderer.table(geometry, look)

    val aspect = stage.width.toDouble() / stage.height
    assertEquals(
      TrayCamera.framingTheTray(geometry, aspect, tiltDegrees = TrayCamera.NO_TILT_DEGREES),
      stage.shots.last(),
    )
    assertTrue("straight down drew the leaning shot", stage.shots.last() != TrayCamera.framingTheTray(geometry, aspect))
  }

  @Test
  fun `turning the phone does not straighten the table or lean it`() {
    // A new stage means a new drawing renderer, and it has to be built with
    // the same answer — otherwise the phone comes back from a turn looking at
    // the table from somewhere else, which is the setting taking effect
    // mid-roll by the back door.
    val renderer = TrayRenderer(TableView.StraightDown)
    renderer.begin(spec(), geometry, look)
    renderer.show(frame(0.0))

    val turned = FakeStage()
    renderer.stage(turned)

    val aspect = turned.width.toDouble() / turned.height
    assertEquals(
      TrayCamera.framingTheTray(geometry, aspect, tiltDegrees = TrayCamera.NO_TILT_DEGREES),
      turned.shots.last(),
    )
  }

  @Test
  fun `redraw says whether there was anywhere to draw`() {
    val renderer = TrayRenderer()
    renderer.table(geometry, look)
    assertFalse("a renderer with no stage claimed to have drawn", renderer.redraw())

    val stage = FakeStage()
    renderer.stage(stage)

    assertTrue(renderer.redraw())
    assertEquals(1, stage.frames)
  }

  @Test
  fun `a second roll replaces the first on the stage it is already drawing on`() {
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)

    renderer.begin(spec(), geometry, look)
    renderer.show(frame(0.0))
    renderer.begin(spec(), geometry, look)

    assertEquals("the second roll landed on top of the first", TRAY_PARTS + DICE, stage.added.size)
  }

  @Test
  fun `the dice waiting to be thrown are drawn on the table once their drop is back`() {
    // Tapping a saved roll puts its dice down rather than throwing them, so
    // what a player looks at before shaking is what they are about to throw
    // (`docs/TODO.md`, Step 4.1).
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)
    val added = stage.added.size

    val request = requireNotNull(renderer.waiting(spec()))

    assertEquals("dice were drawn before their drop had been worked out", added, stage.added.size)
    assertTrue(renderer.settled(request.number, boards.settle(request)))
    assertTrue("no dice were put on the table", stage.added.size > added)
    assertEquals("the waiting dice were not drawn anywhere", DICE, stage.placed.size)
  }

  @Test
  fun `waiting dice are let go so that none of them overlaps`() {
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)

    renderer.drop(spec())

    val where = stage.placed.values.map { it[TRANSLATION_X] to it[TRANSLATION_Y] }
    assertEquals("two waiting dice were drawn in the same place", where.size, where.distinct().size)
  }

  @Test
  fun `a board with no dice on it is the empty table again`() {
    // Clearing the board is saying "no dice", not "no table".
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)
    renderer.drop(spec())

    val request = renderer.waiting(spec().copy(dice = emptyList()))

    assertEquals("an empty board was sent to be dropped", null, request)
    assertTrue("the table went with the dice", renderer.redraw())
    assertEquals("dice were left on a cleared board", 0, stage.placed.size)
  }

  @Test
  fun `dice put on the table before there is a table to put them on are ignored`() {
    // The screen says which table it is before it says what is on it, and a
    // board with no scene under it has nowhere to stand.
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)

    assertEquals(null, renderer.waiting(spec()))
    assertFalse("a drop was taken for a tray that does not exist yet", renderer.settled(1, BoardTrack.EMPTY))
    assertTrue("dice were drawn onto a tray that does not exist yet", stage.placed.isEmpty())
  }

  @Test
  fun `a surface that arrives after the board is given the board`() {
    // The same rule a roll gets: the picture is rebuilt on the new stage from
    // what is already known, rather than being thrown away.
    val renderer = TrayRenderer()
    renderer.table(geometry, look)
    renderer.drop(spec())

    val stage = FakeStage()
    renderer.stage(stage)

    assertEquals("the waiting dice did not follow the surface", DICE, stage.placed.size)
  }

  @Test
  fun `a die put on the board arrives from above and is still on its way`() {
    // The feature: a die the player added falls in rather than appearing.
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)

    renderer.drop(spec())

    assertTrue("the dice were stood down rather than dropped", renderer.falling)
    assertTrue(
      "a waiting die was drawn already on the table",
      stage.placed.values.any { it[TRANSLATION_Z] > OFF_THE_TABLE_MM },
    )
  }

  @Test
  fun `and it is on the table once the drop is over`() {
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)
    renderer.drop(spec())

    renderer.fall(A_WHOLE_FALL)

    assertFalse("the die never landed", renderer.falling)
    assertTrue(
      "a die that had landed was still in the air",
      stage.placed.values.all { it[TRANSLATION_Z] < OFF_THE_TABLE_MM },
    )
  }

  @Test
  fun `a moment between two recorded steps is drawn between them`() {
    // Played back like a roll: a panel at some other rate than the physics
    // still sees the die move smoothly.
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)
    val request = renderer.drop(spec(dice = 1))
    val from =
      request.bodies
        .single()
        .placement.position.z
    val floor = ClearSpace.radiusOf(StandardDice.d6, 1.0)
    val perStep = (from - floor) / FakeBoards.STEPS

    renderer.fall(SettleRule.TIMESTEP_SECONDS * STEP_AND_A_HALF)

    val drawn = stage.placed.getValue(FIRST_DIE)[TRANSLATION_Z].toDouble()
    assertEquals(from - STEP_AND_A_HALF * perStep, drawn, DRAWN_MM)
  }

  @Test
  fun `a die already standing does not move when another is added`() {
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)
    renderer.drop(spec(dice = 1))
    renderer.fall(A_WHOLE_FALL)
    val standing =
      stage.placed.values
        .single()
        .copyOf()

    val request = renderer.drop(spec(dice = 2))

    val carried = request.bodies.first().placement
    assertEquals("a standing die was handed over moving", Vector3.Zero, carried.linearVelocity)
    assertEquals(Vector3.Zero, carried.angularVelocity)
    renderer.fall(A_WHOLE_FALL)
    val again = stage.placed.getValue(FIRST_DIE)
    assertEquals(standing[TRANSLATION_X], again[TRANSLATION_X], 0.0f)
    assertEquals(standing[TRANSLATION_Y], again[TRANSLATION_Y], 0.0f)
    assertEquals("the die that was down was picked up again", standing[TRANSLATION_Z], again[TRANSLATION_Z], 0.0f)
  }

  @Test
  fun `a die still in the air when the next one is added carries on with its momentum`() {
    val renderer = TrayRenderer()
    renderer.stage(FakeStage())
    renderer.table(geometry, look)
    renderer.drop(spec(dice = 1))
    renderer.fall(PART_OF_A_FALL)

    val carried = requireNotNull(renderer.waiting(spec(dice = 2))).bodies.first().placement

    assertTrue("the die in the air was stopped dead", carried.linearVelocity.z < 0.0)
  }

  @Test
  fun `a die taken off the board leaves the others where they were`() {
    // Removing a die is a board like any other — the rest may have been
    // leaning on it — and none of them is new.
    val renderer = TrayRenderer()
    renderer.stage(FakeStage())
    renderer.table(geometry, look)
    renderer.drop(spec(dice = 3))
    renderer.fall(A_WHOLE_FALL)

    val request = requireNotNull(renderer.waiting(spec(dice = 2)))

    assertEquals(2, request.bodies.size)
    assertTrue(request.bodies.all { it.placement.linearVelocity == Vector3.Zero })
  }

  @Test
  fun `only the latest board is drawn, and an earlier drop arriving late is dropped`() {
    // Eight quick taps ask for eight boards; whichever comes back after the
    // player has moved on is for a board that no longer exists.
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)
    val first = requireNotNull(renderer.waiting(spec(dice = 1)))
    val second = requireNotNull(renderer.waiting(spec(dice = 2)))

    assertTrue("two boards shared a number", second.number > first.number)
    assertFalse("a stale drop was drawn", renderer.settled(first.number, boards.settle(first)))
    assertEquals(0, stage.placed.size)
    assertTrue(renderer.settled(second.number, boards.settle(second)))
    assertEquals(2, stage.placed.size)
    assertFalse("the same drop was drawn twice", renderer.settled(second.number, boards.settle(second)))
  }

  @Test
  fun `a board asked for against one that never arrived drops every die afresh`() {
    // The request is built from what is on screen, and the dice of the board
    // that never arrived were never on screen.
    val renderer = TrayRenderer()
    renderer.stage(FakeStage())
    renderer.table(geometry, look)
    renderer.waiting(spec(dice = 1))

    val second = requireNotNull(renderer.waiting(spec(dice = 2)))

    assertTrue(second.bodies.all { it.placement.position.z > OFF_THE_TABLE_MM })
  }

  @Test
  fun `a drop that arrives late starts as far in as the board on screen has moved`() {
    // The dice carried over were snapshotted when it was asked for; starting
    // it from its beginning would set them back by however long it took.
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)
    renderer.drop(spec(dice = 1))
    val request = requireNotNull(renderer.waiting(spec(dice = 2)))
    renderer.fall(SettleRule.TIMESTEP_SECONDS * CATCH_UP_STEPS)

    renderer.settled(request.number, boards.settle(request))

    val released =
      request.bodies
        .last()
        .placement.position.z
    val drawn = stage.placed.getValue(FIRST_DIE + 1)[TRANSLATION_Z].toDouble()
    assertTrue("the late drop started from the beginning", drawn < released - DRAWN_MM)
  }

  @Test
  fun `every die is dropped and drawn, even more than there is clear floor for`() {
    // A die the player added must appear: the shake will count it.
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)

    val request = renderer.drop(spec(dice = TableCapacity.MAX_DICE))

    assertEquals(TableCapacity.MAX_DICE, request.bodies.size)
    assertEquals(TableCapacity.MAX_DICE, stage.placed.size)
  }

  @Test
  fun `a board with nothing falling on it is drawn once and then left alone`() {
    // A settled board is a still picture like any other. Asking it for frames
    // it does not need would be a tray that never stops drawing.
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)
    renderer.drop(spec())
    renderer.fall(A_WHOLE_FALL)
    val drawn = stage.frames

    renderer.fall(A_WHOLE_FALL)

    assertFalse(renderer.falling)
    assertEquals("the board went on drawing after it had settled", drawn, stage.frames)
  }

  @Test
  fun `a surface that arrives mid-fall is given the dice where they are`() {
    val renderer = TrayRenderer()
    renderer.table(geometry, look)
    renderer.drop(spec())
    renderer.fall(PART_OF_A_FALL)

    val stage = FakeStage()
    renderer.stage(stage)

    assertEquals("the falling dice did not follow the surface", DICE, stage.placed.size)
    assertTrue(
      "the dice were put back on the table rather than where they were",
      stage.placed.values.any { it[TRANSLATION_Z] > OFF_THE_TABLE_MM },
    )
  }

  @Test
  fun `throwing the dice ends the fall they were arriving on`() {
    // The dice that were waiting have been thrown, and a half-finished fall
    // belongs to a board that no longer exists.
    val renderer = TrayRenderer()
    renderer.stage(FakeStage())
    renderer.table(geometry, look)
    renderer.drop(spec())

    renderer.begin(spec(), geometry, look)

    assertFalse(renderer.falling)
  }

  @Test
  fun `a drop still being worked out when the dice are thrown never paints over the throw`() {
    val renderer = TrayRenderer()
    renderer.stage(FakeStage())
    renderer.table(geometry, look)
    val request = requireNotNull(renderer.waiting(spec()))

    renderer.begin(spec(), geometry, look)

    assertFalse(renderer.settled(request.number, boards.settle(request)))
  }

  @Test
  fun `nor over a new table or a cleared tray`() {
    val renderer = TrayRenderer()
    renderer.stage(FakeStage())
    renderer.table(geometry, look)
    val beforeTable = requireNotNull(renderer.waiting(spec()))
    renderer.table(geometry, look)
    val beforeEnd = requireNotNull(renderer.waiting(spec()))
    renderer.end()

    assertFalse(renderer.settled(beforeTable.number, boards.settle(beforeTable)))
    assertFalse(renderer.settled(beforeEnd.number, boards.settle(beforeEnd)))
  }

  @Test
  fun `clearing the board ends the fall too`() {
    val renderer = TrayRenderer()
    renderer.stage(FakeStage())
    renderer.table(geometry, look)
    renderer.drop(spec())

    renderer.end()

    assertFalse(renderer.falling)
  }

  @Test
  fun `a fall asked to advance with no board does nothing`() {
    val renderer = TrayRenderer()
    renderer.stage(FakeStage())
    renderer.table(geometry, look)

    renderer.fall(A_WHOLE_FALL)

    assertFalse(renderer.falling)
  }

  /** Asks for a board and hands its drop straight back, as the board thread would. */
  private fun TrayRenderer.drop(spec: ThrowSpec): BoardRequest {
    val request = requireNotNull(waiting(spec)) { "no board was asked for" }
    assertTrue("the drop that was asked for was not drawn", settled(request.number, boards.settle(request)))
    return request
  }

  private fun spec(dice: Int = DICE): ThrowSpec =
    ThrowSpec(
      dice =
        List(dice) { index ->
          DieInstance(
            index = index,
            groupId = 0,
            setId = "builtin",
            requestedSetId = "builtin",
            die = StandardDice.d6,
          )
        },
      geometry = geometry,
      table = look,
      seed = 7L,
    )

  private fun frame(heightMm: Double): RenderFrame =
    RenderFrame.still(
      List(DICE) { index ->
        BodyTransform(
          index = index,
          position = Vector3(index * SPACING_MM, 0.0, heightMm),
          orientation = Quaternion.Identity,
        )
      },
    )

  private companion object {
    const val DICE = 3
    const val SPACING_MM = 20.0
    const val TUMBLING_HEIGHT = 60.0
    const val LANDED_HEIGHT = 8.0

    /** The floor, the walls and the rim — three meshes, one material each. */
    const val TRAY_PARTS = 3

    /** Where a 4x4 column-major transform keeps its x, y and z. */
    const val TRANSLATION_X = 12
    const val TRANSLATION_Y = 13
    const val TRANSLATION_Z = 14

    /** The first die's entity, which `FakeStage` numbers from one. */
    const val FIRST_DIE = TRAY_PARTS + 1

    /** Above a d6's own resting height, so only a die in the air is over it. */
    const val OFF_THE_TABLE_MM = 20.0f

    /** How many steps a drop that comes back late has to catch up on. */
    const val CATCH_UP_STEPS = 10

    /** A moment half way between the first and second recorded steps. */
    const val STEP_AND_A_HALF = 1.5

    /** How closely a drawn position, a float, matches the arithmetic. */
    const val DRAWN_MM = 1e-3

    /** Longer than any drop takes, so the board is certainly down. */
    const val A_WHOLE_FALL = 2.0

    /** And short enough that it certainly is not. */
    const val PART_OF_A_FALL = 0.01
  }
}
