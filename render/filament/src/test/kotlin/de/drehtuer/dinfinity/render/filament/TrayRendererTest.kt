package de.drehtuer.dinfinity.render.filament

import de.drehtuer.dinfinity.core.model.DieInstance
import de.drehtuer.dinfinity.core.model.TableLook
import de.drehtuer.dinfinity.core.model.TableView
import de.drehtuer.dinfinity.fixtures.StandardDice
import de.drehtuer.dinfinity.render.headless.BodyTransform
import de.drehtuer.dinfinity.render.headless.RenderFrame
import de.drehtuer.dinfinity.simulation.api.Quaternion
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
  fun `the dice waiting to be thrown are drawn standing on the table`() {
    // Tapping a saved roll puts its dice down rather than throwing them, so
    // what a player looks at before shaking is what they are about to throw
    // (`docs/TODO.md`, Step 4.1).
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)
    val added = stage.added.size

    renderer.waiting(spec())

    assertTrue("no dice were put on the table", stage.added.size > added)
    assertEquals("the waiting dice were not drawn anywhere", DICE, stage.placed.size)
  }

  @Test
  fun `waiting dice are laid out so that none of them overlaps`() {
    val stage = FakeStage()
    val renderer = TrayRenderer()
    renderer.stage(stage)
    renderer.table(geometry, look)

    renderer.waiting(spec())

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
    renderer.waiting(spec())

    renderer.waiting(spec().copy(dice = emptyList()))

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

    renderer.waiting(spec())

    assertTrue("dice were drawn onto a tray that does not exist yet", stage.placed.isEmpty())
  }

  @Test
  fun `a surface that arrives after the board is given the board`() {
    // The same rule a roll gets: the picture is rebuilt on the new stage from
    // what is already known, rather than being thrown away.
    val renderer = TrayRenderer()
    renderer.table(geometry, look)
    renderer.waiting(spec())

    val stage = FakeStage()
    renderer.stage(stage)

    assertEquals("the waiting dice did not follow the surface", DICE, stage.placed.size)
  }

  private fun spec(): ThrowSpec =
    ThrowSpec(
      dice =
        List(DICE) { index ->
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

    /** Where a 4x4 column-major transform keeps its x and y. */
    const val TRANSLATION_X = 12
    const val TRANSLATION_Y = 13
  }
}
